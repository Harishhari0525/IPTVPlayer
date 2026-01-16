@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.iptvplayer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
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

    // --- State ---
    val filteredChannels by viewModel.filteredChannels.collectAsState()
    val availableGroups by viewModel.groups.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val favoriteChannels by viewModel.favorites.collectAsState()
    val recentChannels by viewModel.recents.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedGroupIndex by remember { mutableStateOf("All") }

    var showImportDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
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
        containerColor = Color.Black,
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                // 1. Top Bar (Title or Search)
                if (isSearching) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = {
                            searchQuery = it
                            viewModel.search(it)
                        },
                        onClose = {
                            isSearching = false
                            searchQuery = ""
                            viewModel.search("")
                        }
                    )
                } else {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                "IPTV Player",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        actions = {
                            IconButton(onClick = { isSearching = true }) {
                                Icon(Icons.Rounded.Search, "Search")
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Rounded.MoreVert, "Options")
                                }
                                ExpressiveMenu(
                                    expanded = showMenu,
                                    onDismiss = { showMenu = false },
                                    viewModel = viewModel,
                                    context = context
                                )
                            }
                        },
                        windowInsets = TopAppBarDefaults.windowInsets,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                // 2. Main Sections Tabs (All / Favorites / Recent)
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(selectedTabIndex),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    divider = {}
                ) {
                    val tabs = listOf("All Channels", "Favorites", "Recent")
                    val icons = listOf(Icons.Rounded.Tv, Icons.Rounded.Favorite, Icons.Rounded.History)

                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                            icon = { Icon(icons[index], null) },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showImportDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(12.dp) // Expressive Shape
            ) {
                Icon(Icons.Rounded.Add, "Import", modifier = Modifier.size(28.dp))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // 3. Category Filter Chips (Only on "All" tab)
            AnimatedVisibility(
                visible = selectedTabIndex == 0 && !isSearching,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    items(availableGroups) { group ->
                        FilterChip(
                            selected = selectedGroupIndex == group,
                            onClick = {
                                selectedGroupIndex = group
                                viewModel.selectGroup(group)
                            },
                            label = { Text(group) },
                            leadingIcon = if (selectedGroupIndex == group) {
                                { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            border = null,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // 4. Content List
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isLoading && scanProgress == null) {
                    CircularProgressIndicator()
                } else {
                    val baseList = when (selectedTabIndex) {
                        0 -> filteredChannels
                        1 -> favoriteChannels
                        else -> recentChannels
                    }
                    val channelsToShow = if (searchQuery.isBlank()) baseList else baseList.filter {
                        it.name.contains(searchQuery, ignoreCase = true)
                    }

                    if (channelsToShow.isEmpty()) {
                        EmptyStateMessage(selectedTabIndex)
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(items = channelsToShow, key = { it.id }) { channel ->
                                ExpressiveChannelItem(
                                    channel = channel,
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onClick = {
                                        viewModel.markAsWatched(channel)
                                        onChannelClick(channel)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Expressive Components ---

@Composable
fun ExpressiveChannelItem(
    channel: Channel,
    onToggleFavorite: (Channel) -> Unit,
    onClick: (Channel) -> Unit
) {
    Card(
        onClick = { onClick(channel) },
        shape = RoundedCornerShape(24.dp), // Expressive Corners
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().height(88.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Logo Container
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black // Dark bg for logos
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(channel.logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    error = rememberVectorPainter(Icons.Rounded.Tv),
                    placeholder = rememberVectorPainter(Icons.Rounded.Tv)
                )
            }

            Spacer(Modifier.width(16.dp))

            // 2. Text Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (channel.group.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text(channel.group, style = MaterialTheme.typography.labelSmall) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null,
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            // 3. Favorite Action
            IconButton(
                onClick = { onToggleFavorite(channel) },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (channel.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite"
                )
            }
        }
    }
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search channels...") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null) }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        shape = RoundedCornerShape(50), // Pill Shape
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun ExpressiveMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: ChannelViewModel,
    context: android.content.Context
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        DropdownMenuItem(
            text = { Text("Clear History") },
            onClick = { viewModel.clearHistory(); onDismiss() },
            leadingIcon = { Icon(Icons.Rounded.History, null) }
        )
        DropdownMenuItem(
            text = { Text("Clear Favorites") },
            onClick = { viewModel.clearFavorites(); onDismiss() },
            leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, null) }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(
            text = { Text("Delete All Channels") },
            onClick = { viewModel.deleteAll(); onDismiss() },
            leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
        )
        DropdownMenuItem(
            text = { Text("Remove Dead Channels") },
            onClick = {
                viewModel.removeDeadChannels(context)
                android.widget.Toast.makeText(context, "Scanning in background...", android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Rounded.PortableWifiOff, null, tint = MaterialTheme.colorScheme.error) },
            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
        )
    }
}

@Composable
fun EmptyStateMessage(tabIndex: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (tabIndex == 0) Icons.Rounded.TvOff else Icons.Rounded.Inbox,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (tabIndex == 0) "No channels found.\nTap + to add a playlist."
            else "Nothing to see here yet.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// (ImportDialog function remains the same as before, no changes needed there)
@Composable
fun ImportDialog(
    onDismiss: () -> Unit,
    onUrlSubmit: (String) -> Unit,
    onFileSelect: () -> Unit
) {
    var urlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("M3U URL") },
                    placeholder = { Text("http://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                TextButton(
                    onClick = onFileSelect,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Icon(Icons.Rounded.FolderOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select File from Storage")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (urlText.isNotBlank()) onUrlSubmit(urlText) },
                enabled = urlText.isNotBlank()
            ) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}