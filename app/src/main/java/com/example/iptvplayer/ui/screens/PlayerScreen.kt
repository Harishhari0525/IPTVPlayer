@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.example.iptvplayer.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.util.Rational
import android.view.WindowManager
import androidx.media3.common.C
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.iptvplayer.ui.viewmodel.ChannelViewModel
import com.example.iptvplayer.ui.viewmodel.ChannelViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoUrl: String,
    channelName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // --- View Model & Data ---
    val viewModel: ChannelViewModel = viewModel(factory = ChannelViewModelFactory(context))
    val filteredChannels by viewModel.filteredChannels.collectAsState()

    // --- UI State ---
    var showChannelSheet by remember { mutableStateOf(false) }
    var isInPipMode by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // --- Gesture State (Volume/Brightness) ---
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var gestureIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var showGestureIndicator by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // --- Player Setup ---
    var currentUrl by remember { mutableStateOf(videoUrl) }
    var currentTitle by remember { mutableStateOf(channelName) }

    val exoPlayer = remember(context) {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(10, TimeUnit.SECONDS) // Tweak timeouts
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(client)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.7559.60 Safari/537.36")
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.google.com/"))

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(okHttpDataSourceFactory)
            )
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
            }
    }

    // --- State Observables ---
    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }

    // --- Listeners ---
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity?.removeOnPictureInPictureModeChangedListener(listener) }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                // UPDATE: Better buffering detection
                isBuffering = state == Player.STATE_BUFFERING
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Load Stream
    LaunchedEffect(currentUrl) {
        val mediaItem = MediaItem.fromUri(currentUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        val foundChannel = filteredChannels.find { it.url == currentUrl }
        if (foundChannel != null) viewModel.markAsWatched(foundChannel)
    }

    // Lifecycle & Orientation
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (activity?.isInPictureInPictureMode == false) exoPlayer.pause()
            } else if (event == Lifecycle.Event.ON_STOP) {
                if (activity?.isInPictureInPictureMode == false && !activity.isChangingConfigurations) {
                    exoPlayer.release()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); exoPlayer.release() }
    }

    DisposableEffect(isInPipMode) {
        if (!isInPipMode) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            hideSystemBars(activity)

            // Sync initial brightness/volume
            brightnessLevel = activity?.window?.attributes?.screenBrightness ?: 0.5f
            if (brightnessLevel < 0) brightnessLevel = 0.5f
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            volumeLevel = currentVol.toFloat() / maxVolume.toFloat()
        }
        onDispose {
            if (!isInPipMode) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                showSystemBars(activity)
            }
        }
    }

    // Auto-hide controls
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying) {
            delay(4000)
            areControlsVisible = false
        }
    }

    // --- Helper to Cycle Audio Tracks ---
    // --- Helper to Cycle Audio Tracks ---
    fun cycleAudioTracks() {
        val tracks = exoPlayer.currentTracks
        // 1. Get all available audio track groups
        val audioGroups = tracks.groups.filter {
            it.type == C.TRACK_TYPE_AUDIO && it.isSupported
        }

        if (audioGroups.size > 1) {
            // 2. Find the index of the currently playing audio track
            val currentGroupIndex = audioGroups.indexOfFirst { it.isSelected }

            // 3. Calculate the next index (loop back to 0 if at the end)
            val nextGroupIndex = (currentGroupIndex + 1) % audioGroups.size
            val nextGroup = audioGroups[nextGroupIndex]

            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearOverrides() // Clear old overrides first
                .setOverrideForType(
                    TrackSelectionOverride(nextGroup.mediaTrackGroup, 0)
                )
                .build()
        }
    }

    // --- UI Layout ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { if (!isInPipMode) showGestureIndicator = true },
                    onDragEnd = { showGestureIndicator = false; gestureIcon = null }
                ) { change, dragAmount ->
                    if (isLocked || isInPipMode) return@detectVerticalDragGestures
                    change.consume()

                    val isLeftWait = change.position.x < (size.width / 2)

                    // FIXED SENSITIVITY: Much slower and smoother
                    // 1.0f / height * 0.8f makes it take almost a full screen swipe to go 0->100
                    val sensitivity = (1.0f / size.height.toFloat()) * 0.8f

                    if (isLeftWait) {
                        // Brightness
                        val delta = dragAmount * sensitivity
                        brightnessLevel = (brightnessLevel - delta).coerceIn(0f, 1f)
                        val lp = activity?.window?.attributes
                        lp?.screenBrightness = brightnessLevel
                        activity?.window?.attributes = lp
                        gestureIcon = Icons.Default.Brightness6
                    } else {
                        // Volume
                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        // Use a smaller multiplier for volume to avoid jumping steps
                        val volDelta = (dragAmount * sensitivity * maxVolume).toInt()

                        // Only update if change is significant to avoid jitter
                        if (kotlin.math.abs(volDelta) >= 0) {
                            val newVol = (currentVol - volDelta).coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            volumeLevel = newVol.toFloat() / maxVolume.toFloat()
                        }
                        gestureIcon = Icons.AutoMirrored.Filled.VolumeUp
                    }
                }
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (!isInPipMode) {
                    areControlsVisible = if (!isLocked) !areControlsVisible else true
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
                }
            },
            update = { view -> view.resizeMode = resizeMode },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Indicator
        if (isBuffering) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        // Gesture Indicator
        AnimatedVisibility(
            visible = showGestureIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f))) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = gestureIcon ?: Icons.Default.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    val percent = if (gestureIcon == Icons.Default.Brightness6) brightnessLevel else volumeLevel
                    Text(text = "${(percent * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- Controls Overlay ---
        AnimatedVisibility(
            visible = areControlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent, Color.Black.copy(0.7f))))
            ) {
                if (isLocked) {
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier.align(Alignment.CenterStart).padding(32.dp).size(56.dp).background(Color.White.copy(0.2f), MaterialTheme.shapes.extraLarge)
                    ) {
                        Icon(Icons.Default.Lock, null, tint = Color.White)
                    }
                    Text("Screen Locked", color = Color.White, modifier = Modifier.align(Alignment.Center))
                } else {
                    // Top Bar
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(currentTitle, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Live Stream", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.weight(1f))

                        // Audio Track Button
                        IconButton(onClick = { cycleAudioTracks() }) {
                            Icon(Icons.Default.Audiotrack, "Audio", tint = Color.White)
                        }

                        // PiP Button
                        IconButton(onClick = {
                            val params = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                            activity?.enterPictureInPictureMode(params)
                        }) { Icon(Icons.Default.PictureInPicture, "PiP", tint = Color.White) }

                        IconButton(onClick = { resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT }) {
                            Icon(if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) Icons.Default.AspectRatio else Icons.Default.FitScreen, null, tint = Color.White)
                        }
                    }

                    // Center Controls (Play/Pause/Seek)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s
                        IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition - 10000) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Replay10, "Rewind", tint = Color.White, modifier = Modifier.fillMaxSize())
                        }

                        // Play/Pause
                        IconButton(
                            onClick = { if (isPlaying) { exoPlayer.pause(); isPlaying=false } else { exoPlayer.play(); isPlaying=true } },
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.fillMaxSize())
                        }

                        // Forward 10s
                        IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition + 10000) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Forward10, "Forward", tint = Color.White, modifier = Modifier.fillMaxSize())
                        }
                    }

                    // Bottom Bar
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.BottomCenter), horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = { isLocked = true }) { Icon(Icons.Default.LockOpen, null, tint = Color.White) }
                        IconButton(onClick = { showChannelSheet = true; areControlsVisible = false }) {
                            Icon(Icons.AutoMirrored.Filled.List, "Channel List", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Channel List Sheet
    if (showChannelSheet && !isInPipMode) {
        ModalBottomSheet(
            onDismissRequest = { showChannelSheet = false },
            sheetState = sheetState,
            containerColor = Color.Black.copy(alpha = 0.9f)
        ) {
            Column(modifier = Modifier.fillMaxHeight(0.5f)) {
                Text("Switch Channel", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary)
                LazyColumn {
                    items(filteredChannels) { channel ->
                        ListItem(
                            headlineContent = { Text(channel.name, color = Color.White) },
                            leadingContent = { Icon(Icons.Default.Tv, null, tint = Color.Gray) },
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

// Helpers... (hideSystemBars / showSystemBars remain the same)
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