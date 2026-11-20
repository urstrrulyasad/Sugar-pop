package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.example.data.VaultAlbumEntity
import com.example.data.VaultMediaEntity
import com.example.ui.components.MediaInfoBottomSheet
import com.example.util.formatDuration
import com.example.util.formatResolution
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

enum class VideoResizeMode(val label: String, val contentScale: ContentScale) {
    FIT("Fit", ContentScale.Fit),
    FILL("Zoom", ContentScale.Crop),
    STRETCH("Stretch", ContentScale.FillBounds)
}

private enum class DragGestureAxis {
    NONE, BRIGHTNESS, VOLUME, SEEK
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    initialMediaId: String,
    videos: List<VaultMediaEntity>,
    albums: List<VaultAlbumEntity>,
    defaultSpeed: Float,
    onSavePosition: (mediaId: String, positionMs: Long) -> Unit,
    onCaptureFrameSnapshot: (VaultMediaEntity, Long) -> Unit,
    onRenameMedia: (String, String) -> Unit,
    onBack: () -> Unit
) {
    if (videos.isEmpty()) {
        onBack()
        return
    }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val coroutineScope = rememberCoroutineScope()

    val startIndex = remember(initialMediaId, videos) {
        videos.indexOfFirst { it.id == initialMediaId }.coerceAtLeast(0)
    }

    // Core ExoPlayer instance bound to vault playlist
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItems = videos.map { video ->
                MediaItem.Builder()
                    .setMediaId(video.id)
                    .setUri(Uri.fromFile(File(video.vaultFilePath)))
                    .build()
            }
            val startVideo = videos[startIndex]
            val resumePos = if (startVideo.lastPositionMs > 1000L &&
                startVideo.durationMs > 5000L &&
                startVideo.lastPositionMs < startVideo.durationMs - 3000L
            ) {
                startVideo.lastPositionMs
            } else {
                0L
            }
            setMediaItems(mediaItems, startIndex, resumePos)
            setPlaybackSpeed(defaultSpeed)
            prepare()
            playWhenReady = true
        }
    }

    var currentVideoIndex by remember { mutableIntStateOf(startIndex) }
    val currentVideo = videos.getOrNull(currentVideoIndex) ?: videos.first()

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(currentVideo.durationMs.coerceAtLeast(1L)) }
    var playbackSpeed by remember { mutableFloatStateOf(defaultSpeed) }
    var isTurboHold by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var repeatOne by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var isLandscape by remember { mutableStateOf(false) }
    var showSpeedBar by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }

    // A-B Loop markers (ms)
    var loopStartMs by remember { mutableStateOf<Long?>(null) }
    var loopEndMs by remember { mutableStateOf<Long?>(null) }

    // Gesture HUD overlays
    var skipIndicatorText by remember { mutableStateOf<String?>(null) }
    var brightnessLevel by remember { mutableStateOf<Float?>(null) }
    var volumeFraction by remember { mutableStateOf<Float?>(null) }
    var scrubPreviewMs by remember { mutableStateOf<Long?>(null) }
    var containerSize by remember { mutableStateOf(IntSize(1080, 1920)) }

    // Keep screen on while playing & restore orientation/brightness/bars on exit
    DisposableEffect(activity) {
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            val finalPos = exoPlayer.currentPosition
            val activeId = videos.getOrNull(exoPlayer.currentMediaItemIndex)?.id
            if (activeId != null) {
                onSavePosition(activeId, finalPos)
            }
            exoPlayer.release()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let { w ->
                val attrs = w.attributes
                attrs.screenBrightness = originalBrightness
                w.attributes = attrs
                WindowCompat.getInsetsController(w, w.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val exitPlayer: () -> Unit = {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onBack()
    }

    BackHandler(onBack = exitPlayer)

    // Listen to ExoPlayer events
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newIdx = exoPlayer.currentMediaItemIndex
                if (newIdx in videos.indices) {
                    currentVideoIndex = newIdx
                    loopStartMs = null
                    loopEndMs = null
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val d = exoPlayer.duration
                    if (d > 0L) durationMs = d
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Position ticker + A-B loop enforcement + periodic position persistence
    LaunchedEffect(exoPlayer, loopStartMs, loopEndMs) {
        var tickCount = 0
        while (true) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.takeIf { it > 0L } ?: durationMs
            currentPositionMs = pos
            if (dur > 0L) durationMs = dur

            val start = loopStartMs
            val end = loopEndMs
            if (start != null && end != null && end > start + 300L && pos >= end) {
                exoPlayer.seekTo(start)
            }

            tickCount++
            if (tickCount % 15 == 0 && exoPlayer.isPlaying) {
                onSavePosition(currentVideo.id, pos)
            }
            delay(130L)
        }
    }

    // Auto-hide controls while playing
    LaunchedEffect(isControlsVisible, isPlaying, showSpeedBar, isControlsLocked) {
        if (isControlsVisible && isPlaying && !showSpeedBar && !isControlsLocked) {
            delay(3800L)
            isControlsVisible = false
        }
    }

    // Auto-clear skip badge
    LaunchedEffect(skipIndicatorText) {
        if (skipIndicatorText != null) {
            delay(750L)
            skipIndicatorText = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
    ) {
        // Video Surface via Media3 Compose ContentFrame
        ContentFrame(
            player = exoPlayer,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = resizeMode.contentScale,
            modifier = Modifier.fillMaxSize()
        )

        // Gesture Interaction Layer (active when controls aren't locked)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isControlsLocked, playbackSpeed) {
                    detectTapGestures(
                        onTap = {
                            isControlsVisible = !isControlsVisible
                            if (!isControlsVisible) showSpeedBar = false
                        },
                        onDoubleTap = { offset ->
                            if (isControlsLocked) return@detectTapGestures
                            val width = containerSize.width.coerceAtLeast(1)
                            if (offset.x < width * 0.42f) {
                                val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                skipIndicatorText = "-10s"
                            } else if (offset.x > width * 0.58f) {
                                val target = (exoPlayer.currentPosition + 10_000L)
                                    .coerceAtMost(durationMs)
                                exoPlayer.seekTo(target)
                                skipIndicatorText = "+10s"
                            } else {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        },
                        onLongPress = {
                            if (!isControlsLocked && exoPlayer.isPlaying) {
                                isTurboHold = true
                                exoPlayer.setPlaybackSpeed(2.0f)
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (isTurboHold) {
                                isTurboHold = false
                                exoPlayer.setPlaybackSpeed(playbackSpeed)
                            }
                        }
                    )
                }
                .pointerInput(isControlsLocked, durationMs) {
                    if (isControlsLocked) return@pointerInput
                    var dragAxis = DragGestureAxis.NONE
                    var startDragPosMs = 0L
                    var startBrightness = 0.5f
                    var startVolume = 0.5f
                    var accumulatedX = 0f
                    var accumulatedY = 0f

                    detectDragGestures(
                        onDragStart = { startOffset ->
                            dragAxis = DragGestureAxis.NONE
                            accumulatedX = 0f
                            accumulatedY = 0f
                            startDragPosMs = exoPlayer.currentPosition

                            val windowAttrs = activity?.window?.attributes
                            startBrightness = windowAttrs?.screenBrightness
                                ?.takeIf { it in 0f..1f } ?: 0.5f

                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                .coerceAtLeast(1)
                            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            startVolume = curVol.toFloat() / maxVol.toFloat()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedX += dragAmount.x
                            accumulatedY += dragAmount.y

                            if (dragAxis == DragGestureAxis.NONE) {
                                val threshold = 18f
                                if (abs(accumulatedX) > threshold &&
                                    abs(accumulatedX) > abs(accumulatedY) * 1.2f
                                ) {
                                    dragAxis = DragGestureAxis.SEEK
                                } else if (abs(accumulatedY) > threshold) {
                                    dragAxis = if (change.position.x < containerSize.width / 2f) {
                                        DragGestureAxis.BRIGHTNESS
                                    } else {
                                        DragGestureAxis.VOLUME
                                    }
                                }
                            }

                            when (dragAxis) {
                                DragGestureAxis.BRIGHTNESS -> {
                                    val h = containerSize.height.coerceAtLeast(1).toFloat()
                                    val delta = -accumulatedY / (h * 0.65f)
                                    val next = (startBrightness + delta).coerceIn(0.05f, 1.0f)
                                    brightnessLevel = next
                                    activity?.window?.let { w ->
                                        val lp = w.attributes
                                        lp.screenBrightness = next
                                        w.attributes = lp
                                    }
                                }

                                DragGestureAxis.VOLUME -> {
                                    val h = containerSize.height.coerceAtLeast(1).toFloat()
                                    val delta = -accumulatedY / (h * 0.65f)
                                    val next = (startVolume + delta).coerceIn(0f, 1f)
                                    volumeFraction = next
                                    val maxVol = audioManager
                                        .getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        .coerceAtLeast(1)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        (next * maxVol).toInt(),
                                        0
                                    )
                                }

                                DragGestureAxis.SEEK -> {
                                    val w = containerSize.width.coerceAtLeast(1).toFloat()
                                    val spanMs = minOf(durationMs, 120_000L).coerceAtLeast(15_000L)
                                    val deltaMs = ((accumulatedX / w) * spanMs).toLong()
                                    scrubPreviewMs = (startDragPosMs + deltaMs)
                                        .coerceIn(0L, durationMs)
                                }

                                DragGestureAxis.NONE -> Unit
                            }
                        },
                        onDragEnd = {
                            if (dragAxis == DragGestureAxis.SEEK) {
                                scrubPreviewMs?.let { exoPlayer.seekTo(it) }
                            }
                            brightnessLevel = null
                            volumeFraction = null
                            scrubPreviewMs = null
                            dragAxis = DragGestureAxis.NONE
                        },
                        onDragCancel = {
                            brightnessLevel = null
                            volumeFraction = null
                            scrubPreviewMs = null
                            dragAxis = DragGestureAxis.NONE
                        }
                    )
                }
        )

        // 2.0x Turbo Hold Indicator
        AnimatedVisibility(
            visible = isTurboHold,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 18.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Rounded.FastForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "2.0x Turbo",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // Gesture Feedback Badges (Brightness / Volume / Double-Tap Seek / Scrub)
        brightnessLevel?.let { level ->
            GestureHudMeter(
                icon = Icons.Rounded.BrightnessHigh,
                label = "Brightness",
                progress = level,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        volumeFraction?.let { level ->
            GestureHudMeter(
                icon = if (level <= 0.01f) {
                    Icons.AutoMirrored.Rounded.VolumeOff
                } else {
                    Icons.AutoMirrored.Rounded.VolumeUp
                },
                label = "Volume",
                progress = level,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        scrubPreviewMs?.let { target ->
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = "${formatDuration(target)} / ${formatDuration(durationMs)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
        skipIndicatorText?.let { text ->
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(
                        if (text.startsWith("-")) Alignment.CenterStart else Alignment.CenterEnd
                    )
                    .padding(horizontal = 36.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = if (text.startsWith("-")) {
                            Icons.Rounded.FastRewind
                        } else {
                            Icons.Rounded.FastForward
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }

        // Locked Controls Floating Unlock Button
        if (isControlsLocked && isControlsVisible) {
            Surface(
                onClick = { isControlsLocked = false },
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
                    .testTag("unlock_player_controls_btn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = "Unlock player controls",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Controls Locked · Tap to Unlock",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }

        // Full Advanced Player HUD
        AnimatedVisibility(
            visible = isControlsVisible && !isControlsLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.72f),
                            0.28f to Color.Transparent,
                            0.65f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.86f)
                        )
                    )
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = exitPlayer,
                        modifier = Modifier.testTag("player_back_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentVideo.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatResolution(currentVideo.width, currentVideo.height)} · ${playbackSpeed}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            onCaptureFrameSnapshot(currentVideo, exoPlayer.currentPosition)
                        },
                        modifier = Modifier.testTag("player_snapshot_btn")
                    ) {
                        Icon(
                            Icons.Rounded.CameraAlt,
                            contentDescription = "Capture frame to Photo Vault",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = { isControlsLocked = true },
                        modifier = Modifier.testTag("player_lock_controls_btn")
                    ) {
                        Icon(
                            Icons.Rounded.LockOpen,
                            contentDescription = "Lock touch controls",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = { showInfoSheet = true },
                        modifier = Modifier.testTag("player_info_btn")
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = "Video details",
                            tint = Color.White
                        )
                    }
                }

                // Center Transport Cluster
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (exoPlayer.hasPreviousMediaItem()) exoPlayer.seekToPreviousMediaItem()
                        },
                        enabled = exoPlayer.hasPreviousMediaItem(),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("player_prev_btn")
                    ) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous video",
                            tint = if (exoPlayer.hasPreviousMediaItem()) Color.White else Color.White.copy(0.35f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("player_rewind_btn")
                    ) {
                        Icon(
                            Icons.Rounded.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Surface(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("player_play_pause_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            exoPlayer.seekTo(
                                (exoPlayer.currentPosition + 10_000L).coerceAtMost(durationMs)
                            )
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("player_forward_btn")
                    ) {
                        Icon(
                            Icons.Rounded.Forward10,
                            contentDescription = "Forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem()
                        },
                        enabled = exoPlayer.hasNextMediaItem(),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("player_next_btn")
                    ) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "Next video",
                            tint = if (exoPlayer.hasNextMediaItem()) Color.White else Color.White.copy(0.35f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Bottom Timeline & Pro Control Dock
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Speed Selector Strip (when expanded)
                    AnimatedVisibility(visible = showSpeedBar) {
                        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            speeds.forEach { speed ->
                                FilterChip(
                                    selected = playbackSpeed == speed,
                                    onClick = {
                                        playbackSpeed = speed
                                        exoPlayer.setPlaybackSpeed(speed)
                                        showSpeedBar = false
                                    },
                                    label = { Text("${speed}x") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = Color.Black.copy(alpha = 0.6f),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Timecodes & A-B loop indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(currentPositionMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        if (loopStartMs != null) {
                            val label = if (loopEndMs != null) {
                                "A–B Loop: ${formatDuration(loopStartMs!!)} → ${formatDuration(loopEndMs!!)}"
                            } else {
                                "Point A set at ${formatDuration(loopStartMs!!)} (tap A–B for B)"
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = formatDuration(durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                    }

                    // Scrubber with A-B Loop Overlay
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val safeDuration = durationMs.coerceAtLeast(1L).toFloat()
                        var isSeeking by remember { mutableStateOf(false) }
                        var sliderFraction by remember { mutableFloatStateOf(0f) }
                        val liveFraction = if (isSeeking) {
                            sliderFraction
                        } else {
                            (currentPositionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
                        }

                        Slider(
                            value = liveFraction,
                            onValueChange = { fraction ->
                                isSeeking = true
                                sliderFraction = fraction
                            },
                            onValueChangeFinished = {
                                exoPlayer.seekTo((sliderFraction * safeDuration).toLong())
                                isSeeking = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_seek_slider")
                        )

                        // Visual A-B Loop Markers
                        val start = loopStartMs
                        val end = loopEndMs
                        if (start != null) {
                            val primaryColor = MaterialTheme.colorScheme.primary
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .align(Alignment.Center)
                                    .padding(horizontal = 10.dp)
                            ) {
                                val startX = (start / safeDuration).coerceIn(0f, 1f) * size.width
                                drawCircle(
                                    color = Color(0xFF4ADE80),
                                    radius = 5.dp.toPx(),
                                    center = Offset(startX, size.height / 2f)
                                )
                                if (end != null) {
                                    val endX = (end / safeDuration).coerceIn(0f, 1f) * size.width
                                    drawLine(
                                        color = primaryColor.copy(alpha = 0.85f),
                                        start = Offset(startX, size.height / 2f),
                                        end = Offset(endX, size.height / 2f),
                                        strokeWidth = 3.dp.toPx()
                                    )
                                    drawCircle(
                                        color = Color(0xFF4ADE80),
                                        radius = 5.dp.toPx(),
                                        center = Offset(endX, size.height / 2f)
                                    )
                                }
                            }
                        }
                    }

                    // Pro Feature Pills Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerPillButton(
                            icon = Icons.Rounded.Speed,
                            label = "${playbackSpeed}x",
                            active = showSpeedBar || playbackSpeed != 1.0f,
                            testTag = "player_speed_btn",
                            onClick = { showSpeedBar = !showSpeedBar }
                        )
                        PlayerPillButton(
                            icon = Icons.Rounded.Loop,
                            label = when {
                                loopStartMs == null -> "A–B"
                                loopEndMs == null -> "Set B"
                                else -> "Clear A–B"
                            },
                            active = loopStartMs != null,
                            testTag = "player_ab_loop_btn",
                            onClick = {
                                val pos = exoPlayer.currentPosition
                                when {
                                    loopStartMs == null -> loopStartMs = pos
                                    loopEndMs == null -> {
                                        if (pos > (loopStartMs ?: 0L) + 300L) {
                                            loopEndMs = pos
                                        } else {
                                            loopStartMs = pos
                                        }
                                    }
                                    else -> {
                                        loopStartMs = null
                                        loopEndMs = null
                                    }
                                }
                            }
                        )
                        PlayerPillButton(
                            icon = Icons.Rounded.AspectRatio,
                            label = resizeMode.label,
                            active = resizeMode != VideoResizeMode.FIT,
                            testTag = "player_aspect_btn",
                            onClick = {
                                val entries = VideoResizeMode.entries
                                resizeMode = entries[(resizeMode.ordinal + 1) % entries.size]
                            }
                        )
                        PlayerPillButton(
                            icon = Icons.Rounded.RepeatOne,
                            label = if (repeatOne) "Loop 1" else "Auto Next",
                            active = repeatOne,
                            testTag = "player_repeat_btn",
                            onClick = {
                                repeatOne = !repeatOne
                                exoPlayer.repeatMode = if (repeatOne) {
                                    Player.REPEAT_MODE_ONE
                                } else {
                                    Player.REPEAT_MODE_OFF
                                }
                            }
                        )
                        PlayerPillButton(
                            icon = if (isMuted) {
                                Icons.AutoMirrored.Rounded.VolumeOff
                            } else {
                                Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            label = if (isMuted) "Muted" else "Sound",
                            active = isMuted,
                            testTag = "player_mute_btn",
                            onClick = {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                            }
                        )
                        PlayerPillButton(
                            icon = Icons.Rounded.ScreenRotation,
                            label = if (isLandscape) "Portrait" else "Full Rotate",
                            active = isLandscape,
                            testTag = "player_rotate_btn",
                            onClick = {
                                isLandscape = !isLandscape
                                activity?.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                                coroutineScope.launch {
                                    delay(300L)
                                    if (!isLandscape) {
                                        activity?.requestedOrientation =
                                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showInfoSheet) {
        val albumName = albums.firstOrNull { it.id == currentVideo.albumId }?.name
        MediaInfoBottomSheet(
            item = currentVideo,
            albumName = albumName,
            onRename = { newName -> onRenameMedia(currentVideo.id, newName) },
            onDismiss = { showInfoSheet = false }
        )
    }
}

@Composable
private fun PlayerPillButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
        } else {
            Color.White.copy(alpha = 0.14f)
        },
        modifier = Modifier
            .height(38.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.primary else Color.White
            )
        }
    }
}

@Composable
private fun GestureHudMeter(
    icon: ImageVector,
    label: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.78f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.width(176.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "$label ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
