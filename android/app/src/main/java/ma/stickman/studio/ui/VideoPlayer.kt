@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package ma.stickman.studio.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Rational
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.stickman.studio.data.ShotTime
import ma.stickman.studio.ui.theme.Gold
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

// ============================================================================================
// The studio player (1.1.1): plays at once and loops like TikTok, gestures (tap, hold for x2, double tap
// for +-5 s), a thin bar with a frame preview while scrubbing, scene marks and the 3-second hook mark,
// slow motion and frame by frame, "edit here", before/after, full screen and picture in picture.
// ============================================================================================

/** The one player of the app, shared by the inline view, the full screen and picture in picture. */
object PlayerHub {
    var player: ExoPlayer? = null
    var pip by mutableStateOf(false)
    var file: File? = null
}

@Stable
class StudioPlayerState(val player: ExoPlayer, val file: File) {
    var playing by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var speed by mutableFloatStateOf(1f)
    var firstFrame by mutableStateOf(false)
    var showingPrev by mutableStateOf(false)
}

@Composable
fun rememberStudioPlayer(file: File): StudioPlayerState {
    val ctx = LocalContext.current
    val st = remember(file) {
        // every file goes through the growing-file reader: it also plays a video that is still downloading
        val src = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory { ma.stickman.studio.data.GrowingFileDataSource() }
        val p = ExoPlayer.Builder(ctx).setMediaSourceFactory(src).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
            repeatMode = Player.REPEAT_MODE_ONE          // loops without a black flash
            setSeekParameters(SeekParameters.EXACT)       // frame-exact scrubbing and stepping
            prepare(); playWhenReady = true               // starts by itself, like TikTok
        }
        PlayerHub.player = p; PlayerHub.file = file
        StudioPlayerState(p, file)
    }
    DisposableEffect(st) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { st.playing = isPlaying }
            override fun onRenderedFirstFrame() { st.firstFrame = true }
            override fun onPlaybackStateChanged(s: Int) { if (st.player.duration > 0) st.duration = st.player.duration }
        }
        st.player.addListener(l)
        onDispose {
            st.player.removeListener(l)
            if (!PlayerHub.pip) { st.player.release(); if (PlayerHub.player === st.player) PlayerHub.player = null }
        }
    }
    // pause in the background (except in picture in picture), go on when back
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, st) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP && !PlayerHub.pip) st.player.pause()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(st) {
        while (true) { st.position = st.player.currentPosition; if (st.player.duration > 0) st.duration = st.player.duration; delay(50) }
    }
    return st
}

private fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

private fun Context.activity(): Activity? { var c = this; while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }; return null }

fun enterPip(ctx: Context) {
    val a = ctx.activity() ?: return
    if (Build.VERSION.SDK_INT < 26) return
    PlayerHub.pip = true
    runCatching { a.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build()) }
        .onFailure { PlayerHub.pip = false }
}

/** Only the video, for picture in picture (MainActivity shows this while the window is small). */
@Composable
fun PipVideo() {
    val p = PlayerHub.player ?: return
    AndroidView({ PlayerView(it).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; player = p } },
        Modifier.fillMaxSize().background(Color.Black))
}

/** The video surface with the gestures, the poster, the bar and the TikTok layer. Used inline and full screen. */
@Composable
fun StudioVideo(st: StudioPlayerState, poster: File?, timeline: List<ShotTime>, tiktok: TikTokLayer?, attached: Boolean,
                modifier: Modifier = Modifier, onFullscreen: (() -> Unit)? = null) {
    val fb = LocalFeedback.current
    val scope = rememberCoroutineScope()
    var flash by remember { mutableStateOf<String?>(null) }   // the big icon that appears on a gesture
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(flash) { if (flash != null) { delay(650); flash = null } }
    Box(modifier.background(Color.Black)) {
        AndroidView({ PlayerView(it).apply {
            useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
        } }, Modifier.fillMaxSize(), update = { it.player = if (attached) st.player else null })
        // the cover until the first frame is on screen: never a black box
        if (!st.firstFrame && poster?.exists() == true) {
            val bmp = rememberFileBitmap(poster.path, 1080)
            bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        tiktok?.let { TikTokOverlay(it) }
        Box(Modifier.fillMaxSize().pointerInput(st) {
            detectTapGestures(
                onTap = {
                    if (st.player.isPlaying) { st.player.pause(); flash = "pause" } else { st.player.play(); flash = "play" }
                    fb.tap()
                },
                onDoubleTap = { o ->
                    val back = o.x < size.width / 2
                    st.player.seekTo((st.player.currentPosition + if (back) -5000 else 5000).coerceIn(0, st.player.duration.coerceAtLeast(0)))
                    flash = if (back) "back" else "fwd"; fb.tick()
                },
                onLongPress = { st.player.setPlaybackSpeed(2f); holding = true; fb.long() },
                onPress = { tryAwaitRelease(); if (holding) { holding = false; st.player.setPlaybackSpeed(st.speed) } },
            )
        })
        AnimatedVisibility(flash != null, Modifier.align(Alignment.Center), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            val icon = when (flash) { "pause" -> Icons.Rounded.Pause; "back" -> Icons.Rounded.FastRewind; "fwd" -> Icons.Rounded.FastForward; else -> Icons.Rounded.PlayArrow }
            Box(Modifier.size(76.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
        }
        if (holding) Text("×2", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter)
            .padding(top = 14.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 4.dp))
        onFullscreen?.let { go ->
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))
                .pressable { go() }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Fullscreen, "ملء الشاشة", tint = Color.White) }
        }
        ScrubBar(st, timeline, Modifier.align(Alignment.BottomCenter))
    }
}

/** Thin bar: scene marks, the 3 s hook mark, and a frame preview above the finger while dragging. */
@Composable
private fun ScrubBar(st: StudioPlayerState, timeline: List<ShotTime>, modifier: Modifier) {
    val dur = st.duration.coerceAtLeast(1)
    var drag by remember { mutableStateOf<Float?>(null) }
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    val complete = !ma.stickman.studio.data.Growing.downloading(st.file)
    val retriever = remember(st.file, complete) { MediaMetadataRetriever().apply { runCatching { setDataSource(st.file.path) } } }
    DisposableEffect(retriever) { onDispose { runCatching { retriever.release() } } }
    val fb = LocalFeedback.current
    LaunchedEffect(drag?.let { (it * 40).roundToInt() }) {   // a new preview frame every 1/40 of the video
        val d = drag ?: return@LaunchedEffect
        thumb = withContext(Dispatchers.IO) {
            runCatching {
                val us = (d * dur * 1000).toLong()
                if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 180, 320)
                else retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }.getOrNull()
        }
    }
    val sceneStarts = remember(timeline) { timeline.filter { it.shot == 0 }.map { it.t0 } }
    BoxWithConstraints(modifier.fillMaxWidth().height(44.dp)) {
        val wPx = with(LocalDensity.current) { maxWidth.toPx() }
        val frac = drag ?: (st.position.toFloat() / dur).coerceIn(0f, 1f)
        drag?.let { d ->
            Column(Modifier.absoluteOffset { IntOffset(((d * wPx) - 45.dp.toPx()).roundToInt().coerceIn(0, (wPx - 90.dp.toPx()).roundToInt()), (-200).dp.roundToPx()) },
                horizontalAlignment = Alignment.CenterHorizontally) {
                thumb?.let { Image(it.asImageBitmap(), null, Modifier.width(90.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(10.dp))) }
                Text(fmt((d * dur).toLong()), color = Color.White, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        Canvas(Modifier.fillMaxSize().pointerInput(st) {
            detectHorizontalDragGestures(
                onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f); fb.tick() },
                onDragEnd = { drag?.let { st.player.seekTo((it * dur).toLong()) }; drag = null; thumb = null },
                onDragCancel = { drag = null; thumb = null },
                onHorizontalDrag = { c, _ -> drag = (c.position.x / size.width).coerceIn(0f, 1f) },
            )
        }.pointerInput(st) { detectTapGestures { o -> st.player.seekTo(((o.x / size.width) * dur).toLong()) } }) {
            val h = if (drag != null) 6.dp.toPx() else 3.dp.toPx()
            val y = size.height - 10.dp.toPx()
            // RTL screens still read the time line left to right, like every video player
            drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h))
            drawRoundRect(Gold, Offset(0f, y - h / 2), Size(size.width * frac, h), CornerRadius(h))
            sceneStarts.forEach { t -> val x = (t * 1000 / dur).toFloat() * size.width
                drawRect(Color.Black.copy(alpha = 0.7f), Offset(x - 1.5f, y - h), Size(3f, h * 2)) }
            val hook = 3000f / dur * size.width
            drawCircle(Color(0xFFFF5A5A), 3.5.dp.toPx(), Offset(hook, y - h - 5.dp.toPx()))
            drawCircle(Color.White, (if (drag != null) 8 else 5).dp.toPx(), Offset(size.width * frac, y))
        }
    }
}

/** What TikTok puts over a post: the account, the caption, the music line and the buttons on the side. */
data class TikTokLayer(val account: String, val caption: String)

@Composable
private fun TikTokOverlay(t: TikTokLayer) {
    // TikTok keeps its buttons on the right even in Arabic: lay this layer out left to right
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(160.dp).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp, top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Gold), contentAlignment = Alignment.Center) { Text("س", color = Color.Black, fontWeight = FontWeight.Bold) }
            listOf(Icons.Rounded.Favorite to "12.4K", Icons.Rounded.ChatBubble to "318", Icons.Rounded.Bookmark to "1.2K", Icons.Rounded.Reply to "640")
                .forEach { (ic, n) -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(ic, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    Text(n, color = Color.White, fontSize = 9.sp)
                } }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 54.dp, bottom = 34.dp)) {
            val rtl = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content)
            Text("@${t.account}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, style = rtl)
            Text(t.caption, color = Color.White, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, style = rtl)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text(" الصوت الأصلي", color = Color.White, fontSize = 10.sp,
                    style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
            }
        }
    }
    }
}

/** Under the video: speeds, frame by frame, before/after, picture in picture and "edit here". */
@Composable
fun PlayerTools(st: StudioPlayerState, timeline: List<ShotTime>, prev: File?, onEditHere: (String) -> Unit) {
    val ctx = LocalContext.current
    val fb = LocalFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf(0.25f, 0.5f, 1f, 1.5f, 2f)) { v ->
                val on = st.speed == v
                Text(if (v == 1f) "عادي" else "×${if (v % 1f == 0f) v.toInt() else v}", style = MaterialTheme.typography.labelLarge,
                    color = if (on) Color.Black else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clip(CircleShape).background(if (on) Gold else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .pressable { st.speed = v; st.player.setPlaybackSpeed(v) }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolChip(Icons.Rounded.SkipPrevious, "إطار") { st.player.pause(); st.player.seekTo((st.player.currentPosition - 33).coerceAtLeast(0)); fb.tick() }
            ToolChip(if (st.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, fmt(st.position)) {
                if (st.player.isPlaying) st.player.pause() else st.player.play() }
            ToolChip(Icons.Rounded.SkipNext, "إطار") { st.player.pause(); st.player.seekTo(st.player.currentPosition + 33); fb.tick() }
            Spacer(Modifier.weight(1f))
            if (Build.VERSION.SDK_INT >= 26) ToolChip(Icons.Rounded.PictureInPictureAlt, "نافذة") { enterPip(ctx) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("عدّل هنا", icon = Icons.Rounded.Edit) {
                st.player.pause()
                val s = st.position / 1000.0
                val shot = timeline.lastOrNull { it.t0 <= s } ?: timeline.firstOrNull()
                onEditHere(if (shot != null) "عند ${fmt(st.position)} (المشهد ${shot.scene + 1}، اللقطة ${shot.shot + 1}: «${shot.text}»): "
                    else "عند ${fmt(st.position)}: ")
            }
            if (prev != null) GhostButton(if (st.showingPrev) "اعرض الجديد" else "اعرض القديم", fx = "picture") {
                val pos = st.player.currentPosition
                st.showingPrev = !st.showingPrev
                st.player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(if (st.showingPrev) prev else st.file)), pos)
                st.player.prepare(); st.player.play()
            }
        }
    }
}

@Composable
private fun ToolChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).pressable { onClick() }
        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Full screen: the same player, swipe down (or back) to close. */
@Composable
fun FullscreenVideo(st: StudioPlayerState, poster: File?, timeline: List<ShotTime>, tiktok: TikTokLayer?, onClose: () -> Unit) {
    val dy = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (1f - abs(dy.value) / 1400f).coerceIn(0.3f, 1f)))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { scope.launch { if (dy.value > 220f) onClose() else dy.animateTo(0f) } },
                    onVerticalDrag = { _, d -> scope.launch { dy.snapTo((dy.value + d).coerceAtLeast(0f)) } })
            }) {
            StudioVideo(st, poster, timeline, tiktok, attached = true,
                modifier = Modifier.fillMaxSize().systemBarsPadding().graphicsLayer { translationY = dy.value; val k = 1f - dy.value / 3000f; scaleX = k; scaleY = k })
        }
    }
}
