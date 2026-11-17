package ma.stickman.studio.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import ma.stickman.studio.R
import ma.stickman.studio.ui.theme.Gold
import ma.stickman.studio.ui.theme.Good
import ma.stickman.studio.ui.theme.LocalLight
import ma.stickman.studio.ui.theme.LocalMotion
import ma.stickman.studio.ui.theme.Sky
import ma.stickman.studio.ui.theme.Violet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ============================================================================================
// Touch feedback: a different vibration (and optional soft sound) for a tap, a success, an error.
// ============================================================================================

/** Short UI sounds, generated once into res/raw (tap, success, error). Loaded lazily, played quietly. */
object Sfx {
    private var pool: SoundPool? = null
    private val ids = IntArray(3)

    fun init(ctx: Context) {
        if (pool != null) return
        pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build().also { p ->
            ids[0] = p.load(ctx, R.raw.tap, 1); ids[1] = p.load(ctx, R.raw.success, 1); ids[2] = p.load(ctx, R.raw.error, 1)
        }
    }

    fun play(i: Int, volume: Float = 0.35f) { pool?.play(ids[i], volume, volume, 1, 0, 1f) }
}

@Stable
class Feedback(private val view: View?, private val haptics: Boolean, private val sounds: Boolean) {
    private fun buzz(kind: Int) { if (haptics) view?.performHapticFeedback(kind) }

    fun tap() { buzz(HapticFeedbackConstants.VIRTUAL_KEY); if (sounds) Sfx.play(0, 0.22f) }
    fun tick() = buzz(HapticFeedbackConstants.CLOCK_TICK)
    fun success() {
        buzz(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
        if (sounds) Sfx.play(1)
    }
    fun error() {
        buzz(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
        if (sounds) Sfx.play(2)
    }
    fun long() = buzz(HapticFeedbackConstants.LONG_PRESS)
}

val LocalFeedback = staticCompositionLocalOf { Feedback(null, false, false) }

// ============================================================================================
// Phone tilt: one gravity listener for the whole app; cards lean a little and a sheen slides over them.
// ============================================================================================

@Stable
class Tilt {
    var x by mutableFloatStateOf(0f)   // -1 (tilted left) .. 1 (right)
    var y by mutableFloatStateOf(0f)   // -1 (top away) .. 1 (top towards you)
    var on = false
}

val LocalTilt = staticCompositionLocalOf { Tilt() }

@Suppress("UNUSED_PARAMETER")
@Composable
fun ProvideTilt(enabled: Boolean, content: @Composable () -> Unit) {
    // 1.3.5 (speed): the leaning cards were removed in 1.2.0, but the gravity sensor still ran 50 times a second
    // while the app was open. Nothing reads it any more, so it is no longer started.
    val tilt = remember { Tilt() }
    CompositionLocalProvider(LocalTilt provides tilt) { content() }
}

/** 1.2.0: the calm design has no leaning cards or sheens; kept as a no-op so every call site still compiles. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.tilt3d(strength: Float = 1f, sheen: Boolean = true): Modifier = this

// ============================================================================================
// Motion helpers
// ============================================================================================

/** Items of a list come in one after the other (fade + rise), once. */
fun Modifier.stagger(index: Int): Modifier = composed {
    val motion = LocalMotion.current
    val a = remember { Animatable(if (motion == 0f) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (motion == 0f) return@LaunchedEffect
        delay((index.coerceAtMost(10) * 45L))
        a.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    this.graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationY = (1f - a.value) * 14f }
}

/** A number that rolls digit by digit when it changes, like the counters in the videos. */
@Composable
fun RollingNumber(text: String, style: TextStyle = MaterialTheme.typography.titleLarge, color: Color = Color.Unspecified) {
    val motion = LocalMotion.current
    // digits read left to right even in the Arabic layout ("0.39$", "95%")
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row {
            text.forEachIndexed { i, ch ->
                AnimatedContent(ch, label = "digit$i", transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(tween((260 * motion).toInt())) { if (up) it else -it } + fadeIn(tween((200 * motion).toInt())))
                        .togetherWith(slideOutVertically(tween((260 * motion).toInt())) { if (up) -it else it } + fadeOut(tween((160 * motion).toInt())))
                }) { c -> Text(c.toString(), style = style, color = color) }
            }
        }
    }
}

/** Counts from 0 up to [value] once (for result numbers). */
@Composable
fun countUp(value: Float, millis: Int = 900): Float {
    val motion = LocalMotion.current
    val a = remember(value) { Animatable(if (motion == 0f) value else 0f) }
    LaunchedEffect(value) { if (motion > 0f) a.animateTo(value, tween(millis, easing = FastOutSlowInEasing)) }
    return a.value
}

private class Piece(val x: Float, val vx: Float, val vy: Float, val spin: Float, val color: Color, val w: Float, val h: Float)

/** Golden confetti over the whole screen, once, when [key] changes to a new value. */
@Composable
fun Confetti(key: Any?, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    if (key == null || motion == 0f) return
    val t = remember(key) { Animatable(0f) }
    val pieces = remember(key) {
        val r = Random(key.hashCode())
        val colors = listOf(Gold, Gold, Color(0xFFF2BC97), Color(0xFFE8E4D8))
        List(40) { Piece(r.nextFloat(), (r.nextFloat() - 0.5f) * 0.5f, 0.6f + r.nextFloat() * 0.9f, r.nextFloat() * 720f - 360f,
            colors[r.nextInt(colors.size)], 8f + r.nextFloat() * 10f, 5f + r.nextFloat() * 6f) }
    }
    LaunchedEffect(key) { t.animateTo(1f, tween(2600, easing = LinearEasing)) }
    if (t.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val p = t.value
        pieces.forEach { c ->
            val x = (c.x + c.vx * p) * size.width
            val y = (-0.1f + c.vy * p * 1.25f + 0.35f * p * p) * size.height
            val a = (1f - p).coerceIn(0f, 1f)
            rotate(c.spin * p, Offset(x, y)) {
                drawRect(c.color.copy(alpha = a), Offset(x - c.w / 2, y - c.h / 2), androidx.compose.ui.geometry.Size(c.w, c.h))
            }
        }
    }
}

/** 1.2.0: a plain warm background (cream by day, charcoal by night), like a sheet of paper. */
@Composable
fun DepthBackground(scroll: () -> Float = { 0f }, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.background
    Canvas(modifier.fillMaxSize()) { drawRect(base) }
}

/** How far the current list is scrolled (pixels): the background reads it in its draw phase for parallax. */
object ScrollBus { val y = androidx.compose.runtime.mutableFloatStateOf(0f) }

/** A list state that also tells the background how far it is scrolled. */
@Composable
fun rememberParallaxList(): androidx.compose.foundation.lazy.LazyListState {
    val st = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(st) {
        androidx.compose.runtime.snapshotFlow { st.firstVisibleItemIndex * 600f + st.firstVisibleItemScrollOffset }
            .collect { ScrollBus.y.floatValue = it }
    }
    return st
}
