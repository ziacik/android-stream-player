package sk.ziacik.androidstreamplayer.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import sk.ziacik.androidstreamplayer.R
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult

private const val SEEK_STEP_MS = 10_000L
private const val OSD_TIMEOUT_MS = 5_000L
private const val SEEK_FEEDBACK_MS = 750L

private val PlayerNearBlack = Color(0xFF09080B)
private val PlayerBurgundySoft = Color(0xFF8B3445)
private val PlayerChampagne = Color(0xFFF1D4A8)
private val PlayerChampagneSoft = Color(0xFFFFE8C4)
private val PlayerMuted = Color.White.copy(alpha = 0.66f)
private val PlayerTrack = Color.White.copy(alpha = 0.18f)
private val PlayerBufferedTrack = Color.White.copy(alpha = 0.34f)

@UnstableApi
@Composable
fun KinoPlayerScreen(
    player: Player,
    result: TorrentSearchResult?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var overlayVisible by remember { mutableStateOf(true) }
    var overlayVersion by remember { mutableIntStateOf(0) }
    var focusedFocus by remember { mutableStateOf(KinoPlayerFocus.PLAY_PAUSE) }
    var positionMs by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var durationMs by remember { mutableStateOf(player.safeDurationMs()) }
    var bufferedPositionMs by remember { mutableLongStateOf(player.bufferedPosition.coerceAtLeast(0L)) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var isBuffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var playbackError by remember { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var seekFeedbackVersion by remember { mutableIntStateOf(0) }
    var scrubPositionMs by remember { mutableStateOf<Long?>(null) }

    fun showOverlay() {
        overlayVisible = true
        overlayVersion += 1
    }

    fun showSeekFeedback(deltaMs: Long) {
        seekFeedback = if (deltaMs < 0) "−10 s" else "+10 s"
        seekFeedbackVersion += 1
    }

    fun seek(deltaMs: Long, showFullOverlay: Boolean) {
        val target = seekTargetMs(
            currentMs = player.currentPosition.coerceAtLeast(0L),
            deltaMs = deltaMs,
            durationMs = player.safeDurationMs(),
        )
        player.seekTo(target)
        positionMs = target
        showSeekFeedback(deltaMs)
        if (showFullOverlay) showOverlay()
    }

    fun commitScrub() {
        val target = scrubPositionMs ?: return
        player.seekTo(target)
        positionMs = target
        scrubPositionMs = null
        showOverlay()
    }

    fun advanceScrub(deltaMs: Long) {
        val base = scrubPositionMs ?: positionMs
        scrubPositionMs = seekTargetMs(
            currentMs = base,
            deltaMs = deltaMs,
            durationMs = durationMs,
        )
        showOverlay()
    }

    fun togglePlayback() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        isPlaying = player.isPlaying
        focusedFocus = KinoPlayerFocus.PLAY_PAUSE
        showOverlay()
    }

    fun activateFocusedControl() {
        when (focusedFocus) {
            KinoPlayerFocus.SEEK_BACK -> seek(-SEEK_STEP_MS, showFullOverlay = true)
            KinoPlayerFocus.PLAY_PAUSE -> togglePlayback()
            KinoPlayerFocus.SEEK_FORWARD -> seek(SEEK_STEP_MS, showFullOverlay = true)
            KinoPlayerFocus.PROGRESS -> commitScrub()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.safeDurationMs()
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            isBuffering = player.playbackState == Player.STATE_BUFFERING
            delay(500L)
        }
    }

    LaunchedEffect(
        overlayVisible,
        overlayVersion,
        isPlaying,
        playbackError,
        focusedFocus,
    ) {
        if (
            !overlayVisible ||
            !isPlaying ||
            playbackError ||
            focusedFocus == KinoPlayerFocus.PROGRESS
        ) {
            return@LaunchedEffect
        }
        delay(OSD_TIMEOUT_MS)
        overlayVisible = false
    }

    LaunchedEffect(seekFeedbackVersion) {
        if (seekFeedback == null) return@LaunchedEffect
        delay(SEEK_FEEDBACK_MS)
        seekFeedback = null
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
                if (shouldRevealOverlayForPlaybackState(value, player.playWhenReady)) {
                    showOverlay()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) showOverlay()
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = true
                showOverlay()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    BackHandler {
        if (overlayVisible) {
            scrubPositionMs = null
            overlayVisible = false
        } else {
            player.stop()
            onExit()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                val isDown = event.type == KeyEventType.KeyDown
                val isUp = event.type == KeyEventType.KeyUp

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    -> {
                        val direction = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1

                        if (!overlayVisible) {
                            if (isUp) {
                                val action = kinoHorizontalAction(
                                    overlayVisible = false,
                                    focus = focusedFocus,
                                    direction = direction,
                                    repeatCount = 0,
                                )
                                if (action is KinoPlayerAction.SeekBy) {
                                    seek(action.deltaMs, showFullOverlay = action.showOverlay)
                                }
                            }
                            true
                        } else if (focusedFocus == KinoPlayerFocus.PROGRESS) {
                            if (isDown) {
                                val action = kinoHorizontalAction(
                                    overlayVisible = true,
                                    focus = focusedFocus,
                                    direction = direction,
                                    repeatCount = event.nativeKeyEvent.repeatCount,
                                )
                                if (action is KinoPlayerAction.ScrubBy) {
                                    advanceScrub(action.deltaMs)
                                }
                            } else if (isUp) {
                                commitScrub()
                            }
                            true
                        } else {
                            if (isUp) {
                                val action = kinoHorizontalAction(
                                    overlayVisible = true,
                                    focus = focusedFocus,
                                    direction = direction,
                                    repeatCount = 0,
                                )
                                if (action is KinoPlayerAction.MoveFocus) {
                                    focusedFocus = action.focus
                                    showOverlay()
                                }
                            }
                            true
                        }
                    }

                    KeyEvent.KEYCODE_MEDIA_REWIND,
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    -> {
                        if (isUp) {
                            val delta = if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                                -SEEK_STEP_MS
                            } else {
                                SEEK_STEP_MS
                            }
                            seek(delta, showFullOverlay = overlayVisible)
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    -> {
                        if (isUp) {
                            if (!overlayVisible) {
                                focusedFocus = KinoPlayerFocus.PLAY_PAUSE
                                showOverlay()
                            } else {
                                val direction = if (keyCode == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                                val nextFocus = kinoVerticalFocus(focusedFocus, direction)
                                if (nextFocus != focusedFocus) {
                                    scrubPositionMs = null
                                    focusedFocus = nextFocus
                                }
                                showOverlay()
                            }
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    -> {
                        if (isUp) {
                            if (!overlayVisible) {
                                togglePlayback()
                            } else {
                                activateFocusedControl()
                            }
                        }
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    KeyEvent.KEYCODE_MEDIA_PAUSE,
                    -> {
                        if (isUp) togglePlayback()
                        true
                    }

                    else -> false
                }
            }
            .focusable()
            .testTag("kino-player"),
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    setKeepContentOnPlayerReset(true)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    this.player = player
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier
                .fillMaxSize()
                .testTag("player-view"),
        )

        if (overlayVisible) {
            KinoPlayerOverlay(
                title = result?.title ?: "Now playing",
                quality = result?.quality,
                positionMs = positionMs,
                durationMs = durationMs,
                bufferedPositionMs = bufferedPositionMs,
                isPlaying = isPlaying,
                focusedFocus = focusedFocus,
                scrubPositionMs = scrubPositionMs,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isBuffering && !playbackError) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp)
                    .testTag("player-buffering"),
                color = PlayerChampagne,
                strokeWidth = 3.dp,
            )
        }

        seekFeedback?.let { feedback ->
            val backward = feedback.startsWith("−")
            Column(
                modifier = Modifier
                    .align(if (backward) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 74.dp)
                    .background(PlayerNearBlack.copy(alpha = 0.78f), CircleShape)
                    .border(1.dp, PlayerChampagne.copy(alpha = 0.30f), CircleShape)
                    .padding(horizontal = 24.dp, vertical = 17.dp)
                    .testTag("player-seek-feedback"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (backward) "↶" else "↷",
                    color = PlayerChampagneSoft,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = feedback,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (playbackError) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(PlayerNearBlack.copy(alpha = 0.9f), RoundedCornerShape(22.dp))
                    .border(1.dp, PlayerBurgundySoft.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 34.dp, vertical = 24.dp)
                    .testTag("player-error"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Couldn’t continue playback",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Press Back to return to your results.",
                    color = PlayerMuted,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
internal fun KinoPlayerOverlay(
    title: String,
    quality: String?,
    positionMs: Long,
    durationMs: Long?,
    bufferedPositionMs: Long,
    isPlaying: Boolean,
    focusedFocus: KinoPlayerFocus,
    scrubPositionMs: Long?,
    modifier: Modifier = Modifier,
) {
    val displayPositionMs = scrubPositionMs ?: positionMs

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.42f to Color.Transparent,
                    0.72f to PlayerNearBlack.copy(alpha = 0.56f),
                    1f to PlayerNearBlack.copy(alpha = 0.96f),
                ),
            )
            .testTag("player-overlay"),
    ) {
        Image(
            painter = painterResource(R.drawable.kino_wordmark_banner),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 36.dp, top = 26.dp)
                .width(112.dp)
                .height(38.dp),
        )

        Text(
            text = "BACK  Results",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 38.dp, top = 34.dp),
            color = PlayerMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 56.dp, vertical = 34.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                quality?.let { PlayerQualityBadge(it) }
            }

            Spacer(Modifier.height(14.dp))

            if (focusedFocus == KinoPlayerFocus.PROGRESS && scrubPositionMs != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = formatPlaybackTime(scrubPositionMs),
                        modifier = Modifier
                            .background(PlayerChampagne, RoundedCornerShape(12.dp))
                            .padding(horizontal = 18.dp, vertical = 7.dp)
                            .testTag("player-scrub-time"),
                        color = PlayerNearBlack,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            PlayerTimeline(
                positionMs = displayPositionMs,
                durationMs = durationMs,
                bufferedPositionMs = bufferedPositionMs,
                focused = focusedFocus == KinoPlayerFocus.PROGRESS,
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPlaybackTime(displayPositionMs),
                    color = PlayerChampagneSoft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = durationMs?.let { "  /  ${formatPlaybackTime(it)}" } ?: "",
                    color = PlayerMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PlayerControlButton(
                        text = "−10",
                        focused = focusedFocus == KinoPlayerFocus.SEEK_BACK,
                        modifier = Modifier.testTag("player-seek-back"),
                    )
                    PlayerControlButton(
                        text = if (isPlaying) "Ⅱ" else "▶",
                        focused = focusedFocus == KinoPlayerFocus.PLAY_PAUSE,
                        emphasized = true,
                        modifier = Modifier.testTag("player-play-pause"),
                    )
                    PlayerControlButton(
                        text = "+10",
                        focused = focusedFocus == KinoPlayerFocus.SEEK_FORWARD,
                        modifier = Modifier.testTag("player-seek-forward"),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerQualityBadge(quality: String) {
    Text(
        text = quality,
        modifier = Modifier
            .background(PlayerChampagne.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
            .border(1.dp, PlayerChampagne.copy(alpha = 0.34f), RoundedCornerShape(100.dp))
            .padding(horizontal = 11.dp, vertical = 4.dp),
        color = PlayerChampagne,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun PlayerTimeline(
    positionMs: Long,
    durationMs: Long?,
    bufferedPositionMs: Long,
    focused: Boolean,
) {
    val duration = durationMs?.takeIf { it > 0L }
    val playedProgress = duration
        ?.let { (positionMs.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
        ?: 0f
    val bufferedProgress = duration
        ?.let { (bufferedPositionMs.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
        ?: 0f
    val trackHeight = if (focused) 8.dp else 5.dp
    val markerSize = if (focused) 18.dp else 12.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (focused) 24.dp else 16.dp)
            .then(
                if (focused) {
                    Modifier
                        .background(PlayerChampagne.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            PlayerChampagne.copy(alpha = 0.30f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 8.dp)
                } else {
                    Modifier
                },
            )
            .testTag("player-progress"),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(PlayerTrack),
        )
        if (bufferedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedProgress)
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(PlayerBufferedTrack),
            )
        }
        if (playedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(playedProgress)
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(PlayerBurgundySoft, PlayerChampagne),
                        ),
                    ),
            )
        }
        val markerOffset = (maxWidth - markerSize) * playedProgress
        Box(
            modifier = Modifier
                .padding(start = markerOffset)
                .size(markerSize)
                .shadow(if (focused) 10.dp else 5.dp, CircleShape)
                .background(PlayerChampagneSoft, CircleShape),
        )
    }
}

@Composable
private fun PlayerControlButton(
    text: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val size = if (emphasized) 52.dp else 44.dp
    val shape = CircleShape
    val background = if (focused) {
        PlayerChampagne
    } else {
        PlayerNearBlack.copy(alpha = 0.82f)
    }
    val content = if (focused) PlayerNearBlack else Color.White

    Box(
        modifier = modifier
            .scale(if (focused) 1.09f else 1f)
            .then(if (focused) Modifier.shadow(9.dp, shape) else Modifier)
            .size(size)
            .background(background, shape)
            .border(
                1.dp,
                if (focused) PlayerChampagneSoft else Color.White.copy(alpha = 0.14f),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            fontSize = if (emphasized) 20.sp else 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun Player.safeDurationMs(): Long? = duration
    .takeIf { it != C.TIME_UNSET && it > 0L }
