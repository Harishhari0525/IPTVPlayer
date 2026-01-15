package com.example.iptvplayer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PortableWifiOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.example.iptvplayer.data.model.Channel
import com.example.iptvplayer.ui.viewmodel.ChannelViewModel
import com.example.iptvplayer.ui.viewmodel.ChannelViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onChannelClick: (Channel) -> Unit
) {
    val context = LocalContext.current
    val viewModel: ChannelViewModel = viewModel(factory = ChannelViewModelFactory(context))

    val filteredChannels by viewModel.filteredChannels.collectAsState()
    val availableGroups by viewModel.groups.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()

    val favoriteChannels by viewModel.favorites.collectAsState()
    val recentChannels by viewModel.recents.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var selectedGroupIndex by remember { mutableStateOf("All") }

    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importPlaylist(context, it)
            showImportDialog = false
        }
    }

    if (showImportDialog) {
        ImportDialog(
            onDismiss = { showImportDialog = false },
            onUrlSubmit = { url ->
                viewModel.importPlaylistFromUrl(url)
                showImportDialog = false
            },
            onFileSelect = { fileLauncher.launch(arrayOf("*/*")) }
        )
    }

    Scaffold(
        topBar = {
            Column {

                if (scanProgress != null) {
                    AlertDialog(
                        onDismissRequest = {}, // Prevent dismissing while scanning
                        title = { Text("Cleaning Playlist") },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text(scanProgress ?: "Please wait...")
                                Text("Do not close the app.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        },
                        confirmButton = {}
                    )
                }

                if (isSearching) {
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    viewModel.search(it)
                                },
                                placeholder = { Text("Search channels...") },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        windowInsets = WindowInsets.statusBars,
                        navigationIcon = {
                            IconButton(onClick = { isSearching = false; searchQuery = ""; viewModel.search("") }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close Search")
                            }
                        }
                    )
                } else {
                    CenterAlignedTopAppBar(
                        title = { Text("IPTV Player") },
                        actions = {
                            IconButton(onClick = { isSearching = true }) {
                                Icon(Icons.Default.Search, "Search")
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, "Options")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Clear History") },
                                        onClick = { viewModel.clearHistory(); showMenu = false },
                                        leadingIcon = { Icon(Icons.Default.History, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Clear Favorites") },
                                        onClick = { viewModel.clearFavorites(); showMenu = false },
                                        leadingIcon = { Icon(Icons.Default.FavoriteBorder, null) }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Delete All Channels") },
                                        onClick = { viewModel.deleteAll(); showMenu = false },
                                        leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = Color.Red) }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Remove Dead Channels") },
                                        onClick = {
                                            viewModel.removeDeadChannels()
                                            showMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.PortableWifiOff, null, tint = Color.Red) }
                                    )
                                }
                            }
                        },
                        windowInsets = WindowInsets.statusBars
                    )
                }

                PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("All") }, icon = { Icon(Icons.Default.Tv, null) })
                    Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("Favorites") }, icon = { Icon(Icons.Default.Favorite, null) })
                    Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }, text = { Text("Recent") }, icon = { Icon(Icons.Default.History, null) })
                }
                if (selectedTabIndex == 0 && !isSearching) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = availableGroups.indexOf(selectedGroupIndex).coerceAtLeast(0),
                        edgePadding = 16.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        divider = {} // No divider for cleaner look
                    ) {
                        availableGroups.forEach { group ->
                            Tab(
                                selected = selectedGroupIndex == group,
                                onClick = {
                                    selectedGroupIndex = group
                                    viewModel.selectGroup(group)
                                },
                                text = { Text(group) }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showImportDialog = true },
                icon = { Icon(Icons.Default.Add, "Import") },
                text = { Text("Playlist") }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding),contentAlignment = Alignment.Center) {
            if (isLoading && scanProgress == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                val baseList = when (selectedTabIndex) {
                    0 -> filteredChannels
                    1 -> favoriteChannels
                    else -> recentChannels
                }

                // Filter logic
                val channelsToShow = if (searchQuery.isBlank()) baseList else baseList.filter {
                    it.name.contains(searchQuery, ignoreCase = true)
                }

                if (channelsToShow.isEmpty()) {
                    EmptyStateMessage(selectedTabIndex)
                } else {
                    ChannelList(
                        channels = channelsToShow,
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onChannelClick = { channel ->
                            viewModel.markAsWatched(channel)
                            onChannelClick(channel)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ImportDialog(
    onDismiss: () -> Unit,
    onUrlSubmit: (String) -> Unit,
    onFileSelect: () -> Unit
) {
    var urlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Enter M3U URL or select a file from device.")

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Playlist URL") },
                    placeholder = { Text("http://example.com/playlist.m3u") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { Icon(Icons.Default.Link, null) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("- OR -", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onFileSelect,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select File from Storage")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (urlText.isNotBlank()) onUrlSubmit(urlText) },
                enabled = urlText.isNotBlank()
            ) {
                Text("Import URL")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ChannelList(channels: List<Channel>, onToggleFavorite: (Channel) -> Unit, onChannelClick: (Channel) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp), // Added padding for aesthetics
        verticalArrangement = Arrangement.spacedBy(12.dp), // Space between items
        modifier = Modifier.fillMaxSize()
    ) {
        items(items = channels, key = { it.id }) { channel ->
            ChannelItem(channel, onToggleFavorite, onChannelClick)
        }
        // Spacer for FAB
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun ChannelItem(channel: Channel, onToggleFavorite: (Channel) -> Unit, onClick: (Channel) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp) // Taller row
            .clickable { onClick(channel) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // 1. Large "Poster" Image on Left
            Card(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .padding(4.dp), // Small padding inside
                shape = RoundedCornerShape(8.dp)
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(channel.logoUrl)
                        .decoderFactory(SvgDecoder.Factory())
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    },
                    error = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Tv, null, tint = Color.Gray)
                        }
                    }
                )
            }

            // 2. Text Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
                if (channel.group.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = channel.group,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 3. Favorite Button
            IconButton(onClick = { onToggleFavorite(channel) }) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Toggle Favorite",
                    tint = if (channel.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyStateMessage(tabIndex: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (tabIndex == 0) Icons.Default.Tv else Icons.Default.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (tabIndex == 0) "No channels found.\nAdd a playlist to start."
                else "Your Watchlist is empty.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}