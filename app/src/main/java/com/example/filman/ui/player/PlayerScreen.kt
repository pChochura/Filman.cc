package com.example.filman.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import com.example.filman.data.local.SessionManager
import com.example.filman.data.scraper.FilmanScraper
import com.example.filman.data.scraper.resolveFilmanEmbedLink
import com.example.filman.data.model.EmbedLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Brush

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    mediaUrl: String,
    scraper: FilmanScraper,
    sessionManager: SessionManager,
    progressManager: com.example.filman.data.local.ProgressManager
) {
    val context = LocalContext.current
    var currentMediaUrl by remember { mutableStateOf(mediaUrl) }
    
    // Server state
    var currentRouteToken by remember { mutableStateOf("") }
    var currentMediaTitle by remember { mutableStateOf("") }
    var currentMediaPoster by remember { mutableStateOf("") }
    var servers by remember { mutableStateOf<List<EmbedLink>>(emptyList()) }
    var selectedServer by remember { mutableStateOf<EmbedLink?>(null) }
    var attemptedServers by remember { mutableStateOf<Set<EmbedLink>>(emptySet()) }
    var serverLoadError by remember { mutableStateOf<String?>(null) }
    var isFetchingServers by remember { mutableStateOf(true) }
    
    // Video extraction state
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var videoHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isExtracting by remember { mutableStateOf(false) }
    
    // UI state
    var isOverlayVisible by remember { mutableStateOf(true) }
    var isSettingsVisible by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val playPauseFocusRequester = remember { FocusRequester() }
    
    var currentPos by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var nextEpisodeDismissed by remember { mutableStateOf(false) }
    var seriesDataLoaded by remember { mutableStateOf(false) }
    
    var directPrevUrl by remember { mutableStateOf<String?>(null) }
    var directNextUrl by remember { mutableStateOf<String?>(null) }

    // Reset dismiss state when media changes
    LaunchedEffect(currentMediaUrl) {
        nextEpisodeDismissed = false
    }

    // Format time function
    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
    
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    val activityContext = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        val window = (activityContext as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-hide overlay
    LaunchedEffect(isOverlayVisible, isSettingsVisible, isPlaying, lastInteractionTime) {
        if (isOverlayVisible && !isSettingsVisible && isPlaying) {
            delay(5000)
            isOverlayVisible = false
        }
    }

    // Load servers when mediaUrl changes
    LaunchedEffect(currentMediaUrl) {
        scope.launch {
            isFetchingServers = true
            serverLoadError = null
            videoUrl = null
            errorMessage = null
            
            try {
                val details = scraper.getMediaDetails(currentMediaUrl)
                if (details is com.example.filman.data.model.MediaDetails.MovieOrEpisode) {
                    currentRouteToken = details.routeToken
                    currentMediaTitle = details.title
                    currentMediaPoster = details.posterUrl
                    directPrevUrl = details.prevEpisodeUrl
                    directNextUrl = details.nextEpisodeUrl
                    
                    if (details.seriesUrl != null && PlayerStateHolder.seasons.isEmpty()) {
                        scope.launch {
                            try {
                                val series = scraper.getMediaDetails(details.seriesUrl)
                                if (series is com.example.filman.data.model.MediaDetails.Series) {
                                    PlayerStateHolder.seriesTitle = series.title
                                    PlayerStateHolder.seasons = series.seasons
                                    for (i in series.seasons.indices) {
                                        val epIndex = series.seasons[i].episodes.indexOfFirst { it.url == currentMediaUrl || currentMediaUrl.contains(it.url) }
                                        if (epIndex != -1) {
                                            PlayerStateHolder.currentSeasonIndex = i
                                            PlayerStateHolder.currentEpisodeIndex = epIndex
                                            break
                                        }
                                    }
                                    seriesDataLoaded = !seriesDataLoaded // trigger recomposition
                                }
                            } catch (e: Exception) {}
                        }
                    }
                    
                    if (details.embeds.isNotEmpty()) {
                        servers = details.embeds
                        
                        // Priority: doodstream > voe > others
                        val prioritized = servers.sortedBy { link ->
                            when (link.serverName.lowercase()) {
                                "doodstream" -> 0
                                "voe" -> 1
                                else -> 2
                            }
                        }
                        
                        // Try to load the first prioritized server
                        attemptedServers = emptySet()
                        selectedServer = prioritized.first()
                    } else {
                        serverLoadError = "No servers found for this media."
                    }
                }
            } catch (e: Exception) {
                serverLoadError = "Failed to load servers: ${e.message}"
            }
            isFetchingServers = false
        }
    }

    // Extract video when selectedServer changes
    LaunchedEffect(selectedServer) {
        val server = selectedServer ?: return@LaunchedEffect
        scope.launch {
            attemptedServers = attemptedServers + server
            try {
                isExtracting = true
                videoUrl = null
                errorMessage = null
                
                val embedUrl = resolveFilmanEmbedLink(
                    cookie = sessionManager.getCookie() ?: "",
                    userAgent = sessionManager.getUserAgent(),
                    linkId = server.url,
                    routeToken = currentRouteToken
                )

                if (embedUrl != null) {
                    val extractor = com.example.filman.data.scraper.getExtractorForUrl(embedUrl)
                    if (extractor != null) {
                        val extracted = extractor.extractVideo(embedUrl)
                        if (extracted != null) {
                            videoHeaders = extracted.headers
                            videoUrl = extracted.url
                        } else {
                            val nextServer = servers.firstOrNull { it !in attemptedServers }
                            if (nextServer != null) {
                                selectedServer = nextServer
                            } else {
                                errorMessage = "Failed to extract video from all servers. Try selecting another in Settings."
                            }
                        }
                    } else {
                        val nextServer = servers.firstOrNull { it !in attemptedServers }
                        if (nextServer != null) {
                            selectedServer = nextServer
                        } else {
                            errorMessage = "Unsupported server natively: $embedUrl"
                        }
                    }
                } else {
                    val nextServer = servers.firstOrNull { it !in attemptedServers }
                    if (nextServer != null) {
                        selectedServer = nextServer
                    } else {
                        errorMessage = "Failed to decrypt embed link."
                    }
                }
            } catch (e: Exception) {
                val nextServer = servers.firstOrNull { it !in attemptedServers }
                if (nextServer != null) {
                    selectedServer = nextServer
                } else {
                    errorMessage = "Error: ${e.message}"
                }
            }
            isExtracting = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    lastInteractionTime = System.currentTimeMillis()
                    val key = event.key
                    when (key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (!isOverlayVisible) {
                                isOverlayVisible = true
                                scope.launch {
                                    delay(100)
                                    try { playPauseFocusRequester.requestFocus() } catch (e: Exception) {}
                                }
                                true
                            } else false
                        }
                        Key.DirectionLeft -> {
                            if (!isOverlayVisible) {
                                exoPlayer?.let { player ->
                                    player.seekTo((player.currentPosition - 15000).coerceAtLeast(0))
                                }
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (!isOverlayVisible) {
                                exoPlayer?.let { player ->
                                    player.seekTo((player.currentPosition + 15000).coerceAtMost(player.duration))
                                }
                                true
                            } else false
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (!isOverlayVisible) {
                                isOverlayVisible = true
                                scope.launch {
                                    delay(100)
                                    try { playPauseFocusRequester.requestFocus() } catch (e: Exception) {}
                                }
                                true
                            } else false
                        }
                        Key.Back, Key.Escape -> {
                            if (isSettingsVisible) {
                                isSettingsVisible = false
                                true
                            } else if (isOverlayVisible) {
                                isOverlayVisible = false
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .focusRequester(focusRequester)
    ) {
        // Player Layer
        if (videoUrl != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // Disable default ExoPlayer controller because we are building our own
                        useController = false
                        
                        val headers = mutableMapOf<String, String>()
                        headers["Referer"] = "https://voe.sx/"
                        headers.putAll(videoHeaders)

                        val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .setAllowCrossProtocolRedirects(true)
                            .setDefaultRequestProperties(headers)

                        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)

                        val newPlayer = ExoPlayer.Builder(ctx)
                            .setMediaSourceFactory(mediaSourceFactory)
                            .build()
                            .apply {
                                setMediaItem(MediaItem.fromUri(videoUrl!!))
                                prepare()
                                // Seek to saved progress if available
                                val savedProgress = progressManager.getProgressForUrl(currentMediaUrl)
                                if (savedProgress != null && savedProgress.progressMs > 0 && savedProgress.progressPercentage < 0.95f) {
                                    seekTo(savedProgress.progressMs)
                                }
                                playWhenReady = true
                            }
                            
                        val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(newPlayer) {
                            override fun hasNextMediaItem(): Boolean = PlayerStateHolder.hasNextEpisode()
                            override fun hasPreviousMediaItem(): Boolean = PlayerStateHolder.hasPrevEpisode()

                            override fun seekToNextMediaItem() {
                                if (PlayerStateHolder.hasNextEpisode()) {
                                    scope.launch {
                                        PlayerStateHolder.moveToNextEpisode()
                                        val nextUrl = PlayerStateHolder.getCurrentEpisodeUrl()
                                        if (nextUrl != null) {
                                            currentMediaUrl = nextUrl
                                        }
                                    }
                                }
                            }

                            override fun seekToPreviousMediaItem() {
                                if (PlayerStateHolder.hasPrevEpisode()) {
                                    scope.launch {
                                        PlayerStateHolder.moveToPrevEpisode()
                                        val prevUrl = PlayerStateHolder.getCurrentEpisodeUrl()
                                        if (prevUrl != null) {
                                            currentMediaUrl = prevUrl
                                        }
                                    }
                                }
                            }
                        }
                        
                        forwardingPlayer.addListener(object : androidx.media3.common.Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                                    forwardingPlayer.seekToNextMediaItem()
                                }
                            }
                            override fun onIsPlayingChanged(playing: Boolean) {
                                isPlaying = playing
                            }
                        })
                        
                        player = forwardingPlayer
                        exoPlayer = newPlayer
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view ->
                    exoPlayer?.let { player ->
                        if (currentMediaTitle.isNotBlank()) {
                            val seriesName = PlayerStateHolder.seriesTitle ?: currentMediaTitle.substringBefore(" - Sezon").substringBefore(" - Odcinek")
                            progressManager.saveProgress(
                                com.example.filman.data.model.ProgressItem(
                                    url = currentMediaUrl,
                                    title = currentMediaTitle,
                                    posterUrl = currentMediaPoster,
                                    progressMs = player.currentPosition,
                                    durationMs = player.duration,
                                    seriesTitle = seriesName
                                )
                            )
                        }
                    }
                    view.player?.release()
                    exoPlayer = null
                }
            )

            // Periodic save coroutine and position update
            LaunchedEffect(exoPlayer, isPlaying) {
                while (exoPlayer != null) {
                    currentPos = exoPlayer?.currentPosition ?: 0L
                    duration = exoPlayer?.duration ?: 0L
                    
                    if (isPlaying && currentMediaTitle.isNotBlank() && duration > 0) {
                        val seriesName = PlayerStateHolder.seriesTitle ?: currentMediaTitle.substringBefore(" - Sezon").substringBefore(" - Odcinek")
                        progressManager.saveProgress(
                            com.example.filman.data.model.ProgressItem(
                                url = currentMediaUrl,
                                title = currentMediaTitle,
                                posterUrl = currentMediaPoster,
                                progressMs = currentPos,
                                durationMs = duration,
                                seriesTitle = seriesName
                            )
                        )
                    }
                    delay(1000)
                }
            }
        }
        
        // Status Layer
        if (isFetchingServers) {
            Text("Loading Servers...", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else if (serverLoadError != null) {
            Text(serverLoadError!!, color = Color.Red, modifier = Modifier.align(Alignment.Center))
        } else if (isExtracting) {
            Text("Extracting Video from ${selectedServer?.serverName}...", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else if (errorMessage != null) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(errorMessage!!, color = Color.Red, modifier = Modifier.padding(16.dp))
                Button(onClick = { isSettingsVisible = true }) {
                    Text("Select Another Server")
                }
            }
        }

        // Custom Controller Overlay
        if (isOverlayVisible && !isSettingsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            ) {
                // Top Start (Title)
                Column(
                    modifier = Modifier.align(Alignment.TopStart).padding(32.dp)
                ) {
                    val seriesTitle = PlayerStateHolder.seriesTitle
                    val seasonName = PlayerStateHolder.getCurrentSeasonName()
                    if (seriesTitle != null && seasonName != null) {
                        Text("$seriesTitle - $seasonName", style = MaterialTheme.typography.titleMedium, color = Color.LightGray)
                    }
                    Text(currentMediaTitle, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }

                // Middle (Playback Controls)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // dummy read of seriesDataLoaded to ensure recomposition when it changes
                    val dummy = seriesDataLoaded 
                    if (PlayerStateHolder.hasPrevEpisode() || directPrevUrl != null) {
                        Surface(
                            onClick = {
                                scope.launch {
                                    if (directPrevUrl != null) {
                                        currentMediaUrl = directPrevUrl!!
                                    } else {
                                        PlayerStateHolder.moveToPrevEpisode()
                                        val prevUrl = PlayerStateHolder.getCurrentEpisodeUrl()
                                        if (prevUrl != null) currentMediaUrl = prevUrl
                                    }
                                }
                            },
                            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                            modifier = Modifier.size(64.dp),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("⏮", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                    
                    Surface(
                        onClick = {
                            if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                        },
                        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                        modifier = Modifier.size(80.dp).focusRequester(playPauseFocusRequester),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color.DarkGray.copy(alpha = 0.5f))
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (isPlaying) "⏸" else "▶", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                        }
                    }

                    if (PlayerStateHolder.hasNextEpisode() || directNextUrl != null) {
                        Surface(
                            onClick = {
                                scope.launch {
                                    if (directNextUrl != null) {
                                        currentMediaUrl = directNextUrl!!
                                    } else {
                                        PlayerStateHolder.moveToNextEpisode()
                                        val nextUrl = PlayerStateHolder.getCurrentEpisodeUrl()
                                        if (nextUrl != null) currentMediaUrl = nextUrl
                                    }
                                }
                            },
                            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                            modifier = Modifier.size(64.dp),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("⏭", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                }
                
                // Bottom Bar (Progress, Settings)
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 32.dp)
                ) {
                    var isProgressBarFocused by remember { mutableStateOf(false) }
                    var isSeeking by remember { mutableStateOf(false) }
                    var seekPos by remember { mutableLongStateOf(0L) }
                    
                    val displayPos = if (isSeeking) seekPos else currentPos
                    val progressRatio = if (duration > 0) (displayPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .focusable()
                            .onFocusChanged { focusState ->
                                isProgressBarFocused = focusState.isFocused
                                if (!focusState.isFocused && isSeeking) {
                                    isSeeking = false
                                }
                            }
                            .onKeyEvent { event ->
                                lastInteractionTime = System.currentTimeMillis()
                                if (event.type == KeyEventType.KeyDown) {
                                    val repeatCount = event.nativeKeyEvent.repeatCount
                                    val step = if (repeatCount > 20) 60000L else if (repeatCount > 5) 30000L else 15000L
                                    
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            if (!isSeeking) {
                                                isSeeking = true
                                                seekPos = currentPos
                                            }
                                            seekPos = (seekPos - step).coerceAtLeast(0)
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            if (!isSeeking) {
                                                isSeeking = true
                                                seekPos = currentPos
                                            }
                                            seekPos = (seekPos + step).coerceAtMost(duration)
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            if (isSeeking) {
                                                exoPlayer?.seekTo(seekPos)
                                                isSeeking = false
                                                true
                                            } else false
                                        }
                                        else -> false
                                    }
                                } else false
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val width = maxWidth
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(if (isProgressBarFocused) Color.Gray else Color.DarkGray))
                        Box(modifier = Modifier.width(width * progressRatio).height(4.dp).background(Color.Red))
                        if (isProgressBarFocused) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(Color.Red, CircleShape)
                                    .offset(x = (width * progressRatio) - 8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row {
                            Text(formatTime(displayPos), color = if (isSeeking) Color.Red else Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(" · ", color = Color.LightGray)
                            Text(formatTime(duration), color = Color.LightGray, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        
                        Surface(
                            onClick = { isSettingsVisible = true }, 
                            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color.DarkGray.copy(alpha = 0.5f))
                        ) {
                            Text("⚙ Settings", modifier = Modifier.padding(12.dp), color = Color.White)
                        }
                    }
                }
            }
        }
        
        // Next Episode Popup
        val dummy2 = seriesDataLoaded
        if (!nextEpisodeDismissed && duration > 0 && currentPos >= duration - 20_000 && (PlayerStateHolder.hasNextEpisode() || directNextUrl != null)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 100.dp, end = 32.dp)
                    .background(Color.DarkGray.copy(alpha = 0.9f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text("Next Episode", color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Starting soon...", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Button(onClick = {
                            scope.launch {
                                if (directNextUrl != null) {
                                    currentMediaUrl = directNextUrl!!
                                } else {
                                    PlayerStateHolder.moveToNextEpisode()
                                    val nextUrl = PlayerStateHolder.getCurrentEpisodeUrl()
                                    if (nextUrl != null) currentMediaUrl = nextUrl
                                }
                            }
                        }) {
                            Text("Play Next")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { nextEpisodeDismissed = true }, colors = ButtonDefaults.colors(containerColor = Color.Gray)) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }

        // Settings Side Panel
        if (isSettingsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(350.dp)
                    .background(Color.Black.copy(alpha = 0.9f))
                    .align(Alignment.CenterEnd)
                    .padding(32.dp)
            ) {
                Column {
                    Text("Select Server", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(servers) { server ->
                            Surface(
                                onClick = {
                                    selectedServer = server
                                    isSettingsVisible = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (selectedServer == server) Color.DarkGray else Color.Transparent
                                )
                            ) {
                                Text(server.serverName, color = Color.White, modifier = Modifier.padding(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
