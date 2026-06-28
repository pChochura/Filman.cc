package com.example.filman.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import com.example.filman.data.model.EmbedLink
import com.example.filman.data.model.MediaDetails
import com.example.filman.data.model.Season
import com.example.filman.data.model.Episode
import com.example.filman.data.scraper.AuthException
import com.example.filman.data.scraper.FilmanScraper
import kotlinx.coroutines.launch

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.rememberLazyListState

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieDetailsScreen(
    movieUrl: String,
    scraper: FilmanScraper,
    favoritesManager: com.example.filman.data.local.FavoritesManager,
    progressManager: com.example.filman.data.local.ProgressManager,
    onPlayMovie: (String) -> Unit,
    onAuthInvalid: () -> Unit
) {
    var mediaDetails by remember { mutableStateOf<MediaDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // UI states for Series
    var seriesDetails by remember { mutableStateOf<MediaDetails.Series?>(null) }
    var selectedSeason by remember { mutableStateOf<Season?>(null) }
    var nextEpisode by remember { mutableStateOf<Episode?>(null) }
    var initialFocusSet by remember { mutableStateOf(false) }
    val episodesLazyRowState = rememberLazyListState()
    val nextEpisodeFocusRequester = remember { FocusRequester() }

    val scope = rememberCoroutineScope()
    var isFavorite by remember(mediaDetails?.title) { mutableStateOf(favoritesManager.isFavorite(movieUrl)) }

    LaunchedEffect(movieUrl) {
        scope.launch {
            try {
                android.util.Log.d("FilmanDebug", "Fetching details for: $movieUrl")
                isLoading = true
                val details = scraper.getMediaDetails(movieUrl)
                if (details is MediaDetails.Series) {
                    seriesDetails = details
                    var nextS: Season? = details.seasons.firstOrNull()
                    var nextE: Episode? = nextS?.episodes?.firstOrNull()
                    var foundIncomplete = false
                    
                    for (season in details.seasons) {
                        for (episode in season.episodes) {
                            val prog = progressManager.getProgressForUrl(episode.url)
                            if (prog != null) {
                                if (prog.progressPercentage < 0.95f) {
                                    nextS = season
                                    nextE = episode
                                    foundIncomplete = true
                                    break
                                } else {
                                    val epIdx = season.episodes.indexOf(episode)
                                    if (epIdx + 1 < season.episodes.size) {
                                        nextS = season
                                        nextE = season.episodes[epIdx + 1]
                                    } else {
                                        val sIdx = details.seasons.indexOf(season)
                                        if (sIdx + 1 < details.seasons.size) {
                                            nextS = details.seasons[sIdx + 1]
                                            nextE = nextS.episodes.firstOrNull()
                                        }
                                    }
                                }
                            }
                        }
                        if (foundIncomplete) break
                    }
                    
                    selectedSeason = nextS
                    nextEpisode = nextE
                }
                mediaDetails = details
                isLoading = false
            } catch (e: AuthException) {
                onAuthInvalid()
            } catch (e: Exception) {
                e.printStackTrace()
                isLoading = false
            }
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading...", modifier = Modifier.fillMaxSize(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        return
    }

    val details = mediaDetails ?: return

    Box(modifier = Modifier.fillMaxSize()) {
        // Background Image with Gradient Overlay
        AsyncImage(
            model = details.posterUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black,
                            Color.Black
                        ),
                        startY = 0f,
                        endY = 1000f
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Top Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 70.dp, top = 50.dp, end = 70.dp, bottom = 40.dp)
                ) {
                    // Left Poster
                    AsyncImage(
                        model = details.posterUrl,
                        contentDescription = details.title,
                        modifier = Modifier
                            .width(250.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(40.dp))

                    // Right Info
                    Column {
                        Text(
                            text = details.title,
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(text = "HD", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Text(
                            text = details.description,
                            modifier = Modifier.padding(top = 24.dp),
                            color = Color.White,
                            maxLines = 10,
                            style = MaterialTheme.typography.bodyLarge
                        )

                        // Action Buttons
                        Row(modifier = Modifier.padding(top = 32.dp)) {
                            if (details is MediaDetails.MovieOrEpisode && seriesDetails == null) {
                                // If it's a standalone movie, pass the movie URL to the player
                                Button(onClick = { 
                                    onPlayMovie(movieUrl)
                                }) {
                                    Text("Watch Now")
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            
                            Button(onClick = {
                                if (isFavorite) {
                                    favoritesManager.removeFavorite(movieUrl)
                                    isFavorite = false
                                } else {
                                    val movieToSave = com.example.filman.data.model.Movie(
                                        url = movieUrl,
                                        title = details.title,
                                        posterUrl = details.posterUrl
                                    )
                                    favoritesManager.addFavorite(movieToSave)
                                    isFavorite = true
                                }
                            }) {
                                Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites")
                            }
                        }
                    }
                }
            }

            // Seasons Row (if Series)
            if (seriesDetails != null) {
                item {
                    Text(
                        text = "Seasons",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 70.dp, bottom = 16.dp),
                        color = Color.White
                    )
                    LazyRow(
                        contentPadding = PaddingValues(start = 70.dp, end = 70.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(seriesDetails!!.seasons) { season ->
                            Surface(
                                onClick = { selectedSeason = season },
                                modifier = Modifier
                                    .width(140.dp)
                                    .aspectRatio(2f / 3f)
                                    .onFocusChanged { state ->
                                        if (state.isFocused) {
                                            selectedSeason = season
                                        }
                                    },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = details.posterUrl, // fallback to series poster
                                        contentDescription = season.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.5f))
                                    )
                                    Text(
                                        text = season.name,
                                        modifier = Modifier.align(Alignment.Center),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Episodes Row
                if (selectedSeason != null) {
                    item {
                        Text(
                            text = "Episodes",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(start = 70.dp, bottom = 16.dp),
                            color = Color.White
                        )
                        LaunchedEffect(selectedSeason) {
                            if (!initialFocusSet && nextEpisode != null) {
                                val index = selectedSeason!!.episodes.indexOf(nextEpisode)
                                if (index >= 0) {
                                    episodesLazyRowState.scrollToItem(index)
                                    try {
                                        nextEpisodeFocusRequester.requestFocus()
                                    } catch (e: Exception) { }
                                    initialFocusSet = true
                                }
                            }
                        }
                        
                        LazyRow(
                            state = episodesLazyRowState,
                            contentPadding = PaddingValues(start = 70.dp, end = 70.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(selectedSeason!!.episodes) { episode ->
                                val progItem = progressManager.getProgressForUrl(episode.url)
                                val prog = progItem?.progressPercentage ?: 0f
                                val isWatched = prog >= 0.95f
                                
                                val focusModifier = if (episode == nextEpisode && !initialFocusSet) {
                                    Modifier.focusRequester(nextEpisodeFocusRequester)
                                } else {
                                    Modifier
                                }

                                Surface(
                                    onClick = {
                                        com.example.filman.ui.player.PlayerStateHolder.seriesTitle = seriesDetails?.title
                                        com.example.filman.ui.player.PlayerStateHolder.seasons = seriesDetails?.seasons ?: emptyList()
                                        com.example.filman.ui.player.PlayerStateHolder.currentSeasonIndex = seriesDetails?.seasons?.indexOf(selectedSeason) ?: -1
                                        com.example.filman.ui.player.PlayerStateHolder.currentEpisodeIndex = selectedSeason!!.episodes.indexOf(episode)
                                        onPlayMovie(episode.url)
                                    },
                                    modifier = focusModifier
                                        .width(240.dp)
                                        .aspectRatio(2f / 1f),
                                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = details.posterUrl, // fallback to series poster
                                            contentDescription = episode.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            alpha = if (isWatched) 0.5f else 1f
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = if (isWatched) 0.7f else 0.4f))
                                        )
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.Bottom
                                        ) {
                                            if (isWatched) {
                                                Text("Watched", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                            }
                                            Text(
                                                text = episode.title,
                                                color = if (isWatched) Color.LightGray else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            if (prog > 0f && !isWatched) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .background(Color.DarkGray, RoundedCornerShape(2.dp))
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(prog)
                                                            .fillMaxHeight()
                                                            .background(Color.Red, RoundedCornerShape(2.dp))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
