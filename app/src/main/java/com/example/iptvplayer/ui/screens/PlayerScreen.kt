@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.example.iptvplayer.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.iptvplayer.ui.viewmodel.ChannelViewModel
import com.example.iptvplayer.ui.viewmodel.ChannelViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoUrl: String,
    channelName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // --- View Model ---
    val viewModel: ChannelViewModel = viewModel(factory = ChannelViewModelFactory(context))
    val filteredChannels by viewModel.filteredChannels.collectAsState()

    // --- UI State ---
    var showChannelSheet by remember { mutableStateOf(false) }
    var isInPipMode by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // --- Gesture State ---
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var gestureIcon by remember { mutableStateOf<ImageVector?>(null) }
    var showGestureIndicator by remember { mutableStateOf(false) }
    var dragVolumeAccumulator by remember { mutableFloatStateOf(0f) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // --- Player State ---
    var currentUrl by remember { mutableStateOf(videoUrl) }
    var currentTitle by remember { mutableStateOf(channelName) }

    // Playback Speed State
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    // Hold Player instance
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // --- Initialize Player (Lazy Loading for Smooth Transition) ---
    DisposableEffect(context) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(client)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        val trackSelector = DefaultTrackSelector(context)

        val player = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(okHttpDataSourceFactory)
                    .setLoadErrorHandlingPolicy(object : DefaultLoadErrorHandlingPolicy() {
                        override fun getMinimumLoadableRetryCount(dataType: Int) = Int.MAX_VALUE
                        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo) = 1000L
                    })
            )
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
            }

        exoPlayer = player
        onDispose {
            player.release()
            exoPlayer = null
        }
    }

    // --- Loading Screen if Player isn't ready ---
    if (exoPlayer == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    // Force non-null for easier usage below
    val player = exoPlayer!!

    // --- Player Logic ---
    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }

    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }

    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                playbackState = state
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlayerError(error: PlaybackException) {
                player.prepare()
                player.play()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(currentUrl) {
        val mediaItem = MediaItem.fromUri(currentUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        val foundChannel = filteredChannels.find { it.url == currentUrl }
        if (foundChannel != null) viewModel.markAsWatched(foundChannel)
    }

    // --- Gestures & Lifecycle (Same as before) ---
    // (omitted detailed lifecycle/PiP code for brevity, it works as is)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && activity?.isInPictureInPictureMode == false) player.pause()
            else if (event == Lifecycle.Event.ON_STOP && activity?.isInPictureInPictureMode == false && !activity.isChangingConfigurations) player.release()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(isInPipMode) {
        if (!isInPipMode) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            hideSystemBars(activity)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            volumeLevel = currentVol.toFloat() / maxVolume.toFloat()
            brightnessLevel = activity?.window?.attributes?.screenBrightness ?: 0.5f
            if (brightnessLevel < 0) brightnessLevel = 0.5f
        }
        onDispose {
            if (!isInPipMode) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                showSystemBars(activity)
            }
        }
    }

    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying) {
            delay(4000)
            areControlsVisible = false
        }
    }

    // --- Helper Functions ---
    fun cycleSpeed() {
        playbackSpeed = when (playbackSpeed) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            2.0f -> 0.5f
            else -> 1.0f
        }
        player.setPlaybackSpeed(playbackSpeed)
    }

    fun cycleAudioTracks() {
        val tracks = player.currentTracks
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        if (audioGroups.size > 1) {
            val currentGroupIndex = audioGroups.indexOfFirst { it.isSelected }
            val nextGroupIndex = (currentGroupIndex + 1) % audioGroups.size
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon().clearOverrides()
                .setOverrideForType(TrackSelectionOverride(audioGroups[nextGroupIndex].mediaTrackGroup, 0))
                .build()
        }
    }

    // --- UI COMPOSITION ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        if (!isInPipMode) showGestureIndicator = true
                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        dragVolumeAccumulator = currentVol.toFloat()
                    },
                    onDragEnd = { showGestureIndicator = false; gestureIcon = null }
                ) { change, dragAmount ->
                    if (isLocked || isInPipMode) return@detectVerticalDragGestures
                    change.consume()
                    val sensitivity = 1.5f / size.height.toFloat()
                    if (change.position.x < size.width / 2) {
                        brightnessLevel = (brightnessLevel - (dragAmount * sensitivity)).coerceIn(0f, 1f)
                        activity?.window?.attributes = activity.window?.attributes?.apply { screenBrightness = brightnessLevel }
                        gestureIcon = Icons.Rounded.Brightness6
                    } else {
                        val volChange = -(dragAmount * sensitivity * maxVolume)
                        dragVolumeAccumulator = (dragVolumeAccumulator + volChange).coerceIn(0f, maxVolume.toFloat())
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, dragVolumeAccumulator.toInt(), 0)
                        volumeLevel = dragVolumeAccumulator / maxVolume.toFloat()
                        gestureIcon = Icons.AutoMirrored.Rounded.VolumeUp
                    }
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (!isInPipMode) areControlsVisible = if (!isLocked) !areControlsVisible else true
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
                }
            },
            update = { view -> view.resizeMode = resizeMode },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering
        if (isBuffering) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        // Gesture Overlay
        AnimatedVisibility(
            visible = showGestureIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            // Material 3 Expressive Card
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(120.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(gestureIcon ?: Icons.Rounded.Tv, null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${((if(gestureIcon == Icons.Rounded.Brightness6) brightnessLevel else volumeLevel) * 100).toInt()}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- MAIN CONTROLS OVERLAY ---
        AnimatedVisibility(
            visible = areControlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.8f), Color.Transparent, Color.Black.copy(0.8f))))
            ) {
                if (isLocked) {
                    FilledTonalButton(
                        onClick = { isLocked = false },
                        modifier = Modifier.align(Alignment.Center),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Icon(Icons.Rounded.Lock, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock Screen")
                    }
                } else {
                    // 1. Top Bar (Clean & Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp).align(Alignment.TopCenter),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                // 1. Force Portrait IMMEDIATELY
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                showSystemBars(activity)
                                onBack()
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(0.3f), contentColor = Color.White)
                        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) }

                        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(currentTitle, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                                Spacer(Modifier.width(6.dp))
                                Text("LIVE", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.8f), fontWeight = FontWeight.Bold)
                            }
                        }

                        // Aspect Ratio Chip
                        AssistChip(
                            onClick = { resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
                            label = { Text(if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) "FIT" else "ZOOM") },
                            colors = AssistChipDefaults.assistChipColors(labelColor = Color.White, containerColor = Color.Black.copy(0.3f)),
                            border = null
                        )
                        Spacer(Modifier.width(8.dp))

                        val isPipAllowed = playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING
                        IconButton(
                            onClick = {
                                if (isPipAllowed) {
                                    activity?.enterPictureInPictureMode(
                                        PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                                    )
                                } else {
                                    android.widget.Toast.makeText(context, "Wait for video to load", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = if (isPipAllowed) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(Icons.Rounded.PictureInPicture, null)
                        }
                    }

                    // 2. Center Controls (Chunky & Expressive)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind (Pill Shape)
                        FilledTonalIconButton(
                            onClick = { player.seekTo(player.currentPosition - 10000) },
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(24.dp), // Expressive Shape
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(0.2f), contentColor = Color.White)
                        ) {
                            Icon(Icons.Rounded.Replay10, null, modifier = Modifier.size(32.dp))
                        }

                        // Play/Pause (Giant Squircle)
                        val playIconScale by animateFloatAsState(if (isPlaying) 1f else 1.1f, label = "scale")
                        FilledIconButton(
                            onClick = { if (isPlaying) { player.pause(); isPlaying=false } else { player.play(); isPlaying=true } },
                            modifier = Modifier.size(96.dp).scale(playIconScale),
                            shape = RoundedCornerShape(32.dp), // Expressive Squircle
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                null,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        // Forward (Pill Shape)
                        FilledTonalIconButton(
                            onClick = { player.seekTo(player.currentPosition + 10000) },
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(0.2f), contentColor = Color.White)
                        ) {
                            Icon(Icons.Rounded.Forward10, null, modifier = Modifier.size(32.dp))
                        }
                    }

                    // 3. Bottom Bar (Floating Island)
                    Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(0.6f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomControlItem(Icons.Rounded.LockOpen, "Lock", onClick = { isLocked = true })
                            BottomControlItem(Icons.Rounded.Speed, "${playbackSpeed}x", onClick = { cycleSpeed() })
                            BottomControlItem(Icons.Rounded.Audiotrack, "Audio", onClick = { cycleAudioTracks() })
                            BottomControlItem(Icons.Rounded.Timer, "Sleep", onClick = { /* TODO */ })
                            VerticalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = Color.White.copy(0.2f))

                            // Channels
                            FilledTonalButton(
                                onClick = { showChannelSheet = true; areControlsVisible = false },
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White, contentColor = Color.Black)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.List, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Channels")
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Channel List Sheet (Same functionality, slightly cleaner UI) ---
    if (showChannelSheet && !isInPipMode) {
        ModalBottomSheet(
            onDismissRequest = { showChannelSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrimColor = Color.Black.copy(0.5f)
        ) {
            Column(modifier = Modifier.fillMaxHeight(0.6f)) {
                Text(
                    "Switch Channel",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(24.dp),
                    fontWeight = FontWeight.Bold
                )
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(filteredChannels) { channel ->
                        ListItem(
                            headlineContent = { Text(channel.name, fontWeight = FontWeight.SemiBold) },
                            leadingContent = {
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(channel.name.take(1), fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                currentUrl = channel.url
                                currentTitle = channel.name
                                scope.launch { sheetState.hide() }.invokeOnCompletion { showChannelSheet = false }
                            }
                        )
                    }
                }
            }
        }
    }
}

// Helpers
private fun hideSystemBars(activity: Activity?) {
    activity?.window?.let { window ->
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private fun showSystemBars(activity: Activity?) {
    activity?.window?.let { window ->
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
fun BottomControlItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp), // Touch target padding
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}