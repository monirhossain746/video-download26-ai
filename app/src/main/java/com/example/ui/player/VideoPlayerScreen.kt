package com.example.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.analyzer.FormatUtils
import com.example.data.local.DownloadDao
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.VortexCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    downloadId: Long,
    streamUrl: String,
    fallbackTitle: String,
    downloadDao: DownloadDao,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val scope = rememberCoroutineScope()

    var mediaTitle by remember { mutableStateOf(fallbackTitle.ifBlank { "Vortex Player" }) }
    var resolvedUri by remember { mutableStateOf<Uri?>(streamUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }) }
    var resumePositionMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(downloadId) {
        if (downloadId > 0L) {
            val entity = downloadDao.getById(downloadId)
            if (entity != null) {
                mediaTitle = entity.title
                resolvedUri = Uri.fromFile(File(entity.localFilePath))
                resumePositionMs = entity.lastPlaybackPositionMs
            }
        }
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isLandscape by remember { mutableStateOf(false) }

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showTrackDialogType by remember { mutableStateOf<Int?>(null) } // C.TRACK_TYPE_AUDIO or C.TRACK_TYPE_TEXT
    var currentTracks by remember { mutableStateOf(Tracks.EMPTY) }

    var gestureOverlayLabel by remember { mutableStateOf<String?>(null) }
    var gestureOverlayIsBrightness by remember { mutableStateOf(true) }
    var currentBrightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: 0.5f
        )
    }

    LaunchedEffect(resolvedUri) {
        val uri = resolvedUri ?: return@LaunchedEffect
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        if (resumePositionMs > 0L) {
            exoPlayer.seekTo(resumePositionMs)
        }
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            val finalPos = exoPlayer.currentPosition
            if (downloadId > 0L) {
                scope.launch {
                    downloadDao.updatePlaybackProgress(
                        id = downloadId,
                        playedAt = System.currentTimeMillis(),
                        positionMs = finalPos
                    )
                }
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(400L)
        }
    }

    LaunchedEffect(gestureOverlayLabel) {
        if (gestureOverlayLabel != null) {
            delay(1100L)
            gestureOverlayLabel = null
        }
    }

    BackHandler {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        val step = 10_000L
                        if (offset.x < size.width / 2f) {
                            exoPlayer.seekTo((exoPlayer.currentPosition - step).coerceAtLeast(0L))
                        } else {
                            exoPlayer.seekTo(exoPlayer.currentPosition + step)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val deltaFraction = -dragAmount / (size.height.toFloat().coerceAtLeast(400f))
                    if (change.position.x < size.width / 2f) {
                        // Brightness gesture
                        currentBrightness = (currentBrightness + deltaFraction).coerceIn(0.05f, 1.0f)
                        activity?.window?.let { win ->
                            val attrs = win.attributes
                            attrs.screenBrightness = currentBrightness
                            win.attributes = attrs
                        }
                        gestureOverlayIsBrightness = true
                        gestureOverlayLabel = "Brightness ${(currentBrightness * 100).toInt()}%"
                    } else {
                        // Volume gesture
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val step = if (deltaFraction > 0.03f) 1 else if (deltaFraction < -0.03f) -1 else 0
                        if (step != 0) {
                            val nextVol = (curVol + step).coerceIn(0, maxVol)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                            gestureOverlayIsBrightness = false
                            gestureOverlayLabel = "Volume ${((nextVol.toFloat() / maxVol) * 100).toInt()}%"
                        } else {
                            gestureOverlayIsBrightness = false
                            gestureOverlayLabel = "Volume ${((curVol.toFloat() / maxVol) * 100).toInt()}%"
                        }
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture HUD
        AnimatedVisibility(
            visible = gestureOverlayLabel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xBB090A14))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = if (gestureOverlayIsBrightness) Icons.Rounded.BrightnessHigh else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = VortexCyan
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = gestureOverlayLabel.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }

        // Player Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xAA070812), Color.Transparent, Color(0xCC070812))
                        )
                    )
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            onBack()
                        },
                        modifier = Modifier.testTag("player_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = mediaTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showTrackDialogType = C.TRACK_TYPE_TEXT }) {
                        Icon(Icons.Rounded.Subtitles, contentDescription = "Subtitles", tint = Color.White)
                    }
                    IconButton(onClick = { showTrackDialogType = C.TRACK_TYPE_AUDIO }) {
                        Icon(Icons.Rounded.AudioFile, contentDescription = "Audio track", tint = Color.White)
                    }
                    IconButton(onClick = { showSpeedDialog = true }) {
                        Icon(Icons.Rounded.Speed, contentDescription = "Playback speed", tint = Color.White)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                        IconButton(
                            onClick = {
                                val params = PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 9))
                                    .build()
                                activity.enterPictureInPictureMode(params)
                            },
                            modifier = Modifier.testTag("player_pip_btn")
                        ) {
                            Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = "Picture in picture", tint = Color.White)
                        }
                    }
                }

                // Center Play/Pause + Skip Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)) },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                    ) {
                        Icon(Icons.Rounded.Replay10, contentDescription = "Rewind 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp))
                    }

                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(ElectricPurple)
                            .testTag("player_play_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = { exoPlayer.seekTo(exoPlayer.currentPosition + 10_000L) },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                    ) {
                        Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }

                // Bottom Seekbar & Orientation Toggle
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
                    Slider(
                        value = currentPosMs.toFloat().coerceIn(0f, sliderMax),
                        onValueChange = { exoPlayer.seekTo(it.toLong()) },
                        valueRange = 0f..sliderMax,
                        colors = SliderDefaults.colors(
                            thumbColor = VortexCyan,
                            activeTrackColor = ElectricPurple,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${FormatUtils.formatDuration(currentPosMs)} / ${FormatUtils.formatDuration(durationMs)} • ${playbackSpeed}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                        IconButton(
                            onClick = {
                                isLandscape = !isLandscape
                                activity?.requestedOrientation = if (isLandscape) {
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            },
                            modifier = Modifier.testTag("player_fullscreen_btn")
                        ) {
                            Icon(
                                imageVector = if (isLandscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                contentDescription = "Toggle fullscreen orientation",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed") },
            text = {
                Column {
                    speeds.forEach { speed ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    playbackSpeed = speed
                                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = playbackSpeed == speed, onClick = {
                                playbackSpeed = speed
                                exoPlayer.playbackParameters = PlaybackParameters(speed)
                                showSpeedDialog = false
                            })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${speed}x")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Close") }
            }
        )
    }

    showTrackDialogType?.let { trackType ->
        val groups = currentTracks.groups.filter { it.type == trackType }
        val title = if (trackType == C.TRACK_TYPE_TEXT) "Subtitle Tracks" else "Audio Tracks"
        AlertDialog(
            onDismissRequest = { showTrackDialogType = null },
            title = { Text(title) },
            text = {
                Column {
                    if (groups.isEmpty()) {
                        Text("No alternate tracks embedded in this media file.")
                    } else {
                        if (trackType == C.TRACK_TYPE_TEXT) {
                            TextButton(onClick = {
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                                showTrackDialogType = null
                            }) {
                                Text("Disable Subtitles")
                            }
                        }
                        groups.forEachIndexed { idx, group ->
                            val format = group.getTrackFormat(0)
                            val label = format.label ?: format.language ?: "Track ${idx + 1}"
                            TextButton(onClick = {
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(trackType, false)
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                    .build()
                                showTrackDialogType = null
                            }) {
                                Text("$label ${if (group.isSelected) "✓" else ""}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackDialogType = null }) { Text("Close") }
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
