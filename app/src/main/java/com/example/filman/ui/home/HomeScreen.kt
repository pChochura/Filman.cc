package com.example.filman.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import coil.compose.AsyncImage
import com.example.filman.data.model.Movie
import com.example.filman.data.scraper.AuthException
import com.example.filman.data.scraper.FilmanScraper
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    scraper: FilmanScraper,
    favoritesManager: com.example.filman.data.local.FavoritesManager,
    progressManager: com.example.filman.data.local.ProgressManager,
    onMovieClick: (String) -> Unit,
    onAuthInvalid: () -> Unit,
) {
    var homeMovies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var progressItems by remember {
        mutableStateOf<List<com.example.filman.data.model.ProgressItem>>(emptyList())
    }
    var searchResults by remember { mutableStateOf<List<Movie>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    var selectedTabIndex by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val tabs = listOf("Home", "Movies", "Series", "Kids")

    var moviesPage by remember { androidx.compose.runtime.mutableIntStateOf(1) }
    var moviesList by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var isMoviesLoading by remember { mutableStateOf(false) }

    var seriesPage by remember { androidx.compose.runtime.mutableIntStateOf(1) }
    var seriesList by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var isSeriesLoading by remember { mutableStateOf(false) }

    var kidsPage by remember { androidx.compose.runtime.mutableIntStateOf(1) }
    var kidsList by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var isKidsLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun loadNextPage(tabIndex: Int) {
        scope.launch {
            try {
                when (tabIndex) {
                    1 -> {
                        if (isMoviesLoading) return@launch
                        isMoviesLoading = true
                        val newMovies = scraper.getCategoryMovies("/filmy/", moviesPage)
                        moviesList = moviesList + newMovies
                        moviesPage++
                        isMoviesLoading = false
                    }
                    2 -> {
                        if (isSeriesLoading) return@launch
                        isSeriesLoading = true
                        val newSeries = scraper.getCategoryMovies("/seriale/", seriesPage)
                        seriesList = seriesList + newSeries
                        seriesPage++
                        isSeriesLoading = false
                    }
                    3 -> {
                        if (isKidsLoading) return@launch
                        isKidsLoading = true
                        val newKids = scraper.getCategoryMovies("/dla-dzieci-pl/", kidsPage)
                        kidsList = kidsList + newKids
                        kidsPage++
                        isKidsLoading = false
                    }
                }
            } catch(e: Exception) {
                if (e is AuthException) onAuthInvalid()
            }
        }
    }

    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 1 && moviesList.isEmpty()) loadNextPage(1)
        if (selectedTabIndex == 2 && seriesList.isEmpty()) loadNextPage(2)
        if (selectedTabIndex == 3 && kidsList.isEmpty()) loadNextPage(3)
    }

    LaunchedEffect(Unit) {
        favorites = favoritesManager.getFavorites()
        progressItems = progressManager.getProgressItems().filter { it.progressPercentage < 0.95f }
        scope.launch {
            try {
                homeMovies = scraper.getHomeMovies()
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text("Loading...", modifier = Modifier.fillMaxSize(), textAlign = TextAlign.Center)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp),
    ) {
        item {
            // Search Bar at Top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 32.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                val keyboardController =
                    androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                Text(
                    "Search:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 16.dp),
                )
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(color = Color.White),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            scope.launch {
                                try {
                                    if (searchQuery.isNotBlank()) {
                                        searchResults = scraper.searchMovies(searchQuery)
                                    } else {
                                        searchResults = null
                                    }
                                } catch (e: AuthException) {
                                    onAuthInvalid()
                                }
                            }
                        },
                    ),
                    modifier = Modifier
                        .width(300.dp)
                        .background(Color.DarkGray)
                        .padding(8.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        keyboardController?.hide()
                        scope.launch {
                            try {
                                if (searchQuery.isNotBlank()) {
                                    searchResults = scraper.searchMovies(searchQuery)
                                } else {
                                    searchResults = null
                                }
                            } catch (e: AuthException) {
                                onAuthInvalid()
                            }
                        }
                    },
                ) {
                    Text("Go")
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(onClick = { onAuthInvalid() }) {
                    Text("Logout")
                }
            }
        }
        
        item {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.padding(horizontal = 32.dp).padding(bottom = 24.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onFocus = { selectedTabIndex = index },
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if (searchResults != null) {
            item {
                Text(
                    "Search Results",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        horizontal = 32.dp,
                    ),
                ) {
                    items(searchResults!!) { movie ->
                        MovieCard(movie = movie, onClick = { onMovieClick(movie.url) })
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        } else if (selectedTabIndex == 0) {
            if (progressItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Continue Watching",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .padding(bottom = 16.dp),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 32.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp),
                    ) {
                        items(progressItems) { item ->
                            ProgressCard(item = item, onClick = { onMovieClick(item.url) })
                        }
                    }
                }
            }

            if (favorites.isNotEmpty()) {
                item {
                    Text(
                        text = "Your Favorites",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .padding(bottom = 16.dp),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 32.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp),
                    ) {
                        items(favorites) { movie ->
                            MovieCard(movie = movie, onClick = { onMovieClick(movie.url) })
                        }
                    }
                }
            }

            item {
                Text(
                    "Recommended",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                ) {
                    items(homeMovies) { movie ->
                        MovieCard(movie = movie, onClick = { onMovieClick(movie.url) })
                    }
                }
            }
        } else {
            val list = when (selectedTabIndex) {
                1 -> moviesList
                2 -> seriesList
                else -> kidsList
            }
            val isLoadingMore = when (selectedTabIndex) {
                1 -> isMoviesLoading
                2 -> isSeriesLoading
                else -> isKidsLoading
            }
            
            val chunkedList = list.chunked(6)
            items(chunkedList.size) { rowIndex ->
                val rowMovies = chunkedList[rowIndex]
                
                if (rowIndex == chunkedList.size - 1 && !isLoadingMore) {
                    LaunchedEffect(rowIndex) {
                        loadNextPage(selectedTabIndex)
                    }
                }

                Row(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    for (movie in rowMovies) {
                        MovieCard(movie = movie, onClick = { onMovieClick(movie.url) })
                    }
                }
            }
            
            if (isLoadingMore) {
                item {
                    Text("Loading more...", modifier = Modifier.padding(32.dp))
                }
            }
        }
    }
}

@Composable
fun MovieCard(movie: Movie, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(150.dp)
            .height(220.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (movie.posterUrl.isNotEmpty()) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray),
                )
            }

            // Title overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(8.dp),
            ) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
fun ProgressCard(item: com.example.filman.data.model.ProgressItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(150.dp)
            .height(220.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.posterUrl.isNotEmpty()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray),
                )
            }

            // Progress Bar and Title overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f)),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    modifier = Modifier.padding(8.dp),
                )

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.DarkGray),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(item.progressPercentage)
                            .background(Color.Red),
                    )
                }
            }
        }
    }
}
