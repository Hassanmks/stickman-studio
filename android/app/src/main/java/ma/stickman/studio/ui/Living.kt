package ma.stickman.studio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ma.stickman.studio.ui.theme.LocalMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

// ============================================================================================
// Living icons (1.1.1): every Fluent icon breathes, floats and catches a light now and then, each with its
// own rhythm; with `active` it plays its own gesture (the magnifier searches, the rocket shakes, the
// hourglass turns...). Nothing moves when the system "remove animations" setting is on (LocalMotion = 0);
// Compose stops the frame clock when the screen is off or the app is in the background.
// ============================================================================================

private fun phase(name: String) = (abs(name.hashCode()) % 1000) / 1000f

/** The gesture of an icon at time [t] (seconds), as layer changes. Strength [k]: 0.35 idle, 1 active. */
private fun gesture(name: String, t: Float, k: Float): FloatArray {
    // rotation, translationX (fraction of size), translationY (fraction), scaleX, scaleY, rotationY
    val w = 2f * PI.toFloat()
    val s = sin(w * t)
    return when (name) {
        "magnifier" -> floatArrayOf(-10f * k * sin(w * t * 0.7f), 0.10f * k * sin(w * t * 0.7f), 0.04f * k * sin(w * t * 1.4f), 1f, 1f, 0f)
        "hourglass" -> { val turn = ((t * 0.5f) % 1f); val r = if (turn > 0.8f) (turn - 0.8f) / 0.2f * 180f else 0f
            floatArrayOf(r * (if (k > 0.5f) 1f else 0f), 0f, 0f, 1f, 1f, 0f) }
        "rocket" -> floatArrayOf(2.5f * k * sin(w * t * 9f), 0.02f * k * sin(w * t * 11f), -0.06f * k * (0.5f + 0.5f * sin(w * t * 0.8f)), 1f, 1f, 0f)
        "gear" -> floatArrayOf(((t * 40f * (0.4f + k)) % 360f), 0f, 0f, 1f, 1f, 0f)
        "bell" -> floatArrayOf(14f * k * sin(w * t * 2.2f) * (0.5f + 0.5f * sin(w * t * 0.4f)), 0f, 0f, 1f, 1f, 0f)
        "coin", "globe" -> floatArrayOf(0f, 0f, 0f, 1f, 1f, (t * 120f * (0.3f + k)) % 360f)
        "warning" -> floatArrayOf(8f * k * sin(w * t * 6f) * (if ((t % 2f) < 0.6f) 1f else 0.1f), 0f, 0f, 1f, 1f, 0f)
        "fire" -> floatArrayOf(2f * s, 0f, 0f, 1f - 0.04f * k * sin(w * t * 3.1f), 1f + 0.08f * k * sin(w * t * 4.3f), 0f)
        "sparkles" -> { val p = 1f + 0.12f * k * sin(w * t * 1.6f); floatArrayOf(6f * k * s, 0f, 0f, p, p, 0f) }
        "robot" -> floatArrayOf(6f * k * sin(w * t * 0.9f), 0f, 0.02f * k * sin(w * t * 1.8f), 1f, 1f, 0f)
        "clapper" -> { val snap = if (k > 0.5f && (t % 1.6f) < 0.18f) -12f else 0f; floatArrayOf(snap, 0f, 0f, 1f, 1f, 0f) }
        "film" -> floatArrayOf(0f, 0.05f * k * s, 0f, 1f, 1f, 0f)
        "stopwatch" -> floatArrayOf(if (k > 0.5f) ((t * 4f).toInt() % 2) * 4f - 2f else 0f, 0f, 0f, 1f, 1f, 0f)
        "scroll", "books" -> floatArrayOf(3f * k * s, 0f, 0.02f * k * sin(w * t * 1.3f), 1f, 1f, 0f)
        "picture", "palette" -> floatArrayOf(4f * k * s, 0f, 0f, 1f, 1f, 8f * k * sin(w * t * 0.6f))
        "party", "trophy" -> { val b = 1f + 0.08f * k * abs(sin(w * t * 1.2f)); floatArrayOf(5f * k * s, 0f, -0.03f * k * abs(s), b, b, 0f) }
        "check" -> { val b = 1f + 0.06f * k * abs(sin(w * t * 1.5f)); floatArrayOf(0f, 0f, 0f, b, b, 0f) }
        "package" -> floatArrayOf(0f, 0f, -0.03f * k * abs(sin(w * t * 1.4f)), 1f + 0.03f * k * s, 1f - 0.03f * k * s, 0f)
        "mic", "speech" -> { val b = 1f + 0.05f * k * abs(sin(w * t * 3f)); floatArrayOf(0f, 0f, 0f, b, b, 0f) }
        else -> floatArrayOf(3f * k * s, 0f, 0f, 1f, 1f, 0f)
    }
}

/** One Fluent icon, alive. */
@Composable
fun LivingIcon(name: String, id: Int, size: Dp, modifier: Modifier = Modifier, active: Boolean = false) {
    val motion = LocalMotion.current
    if (motion == 0f) {
        Image(painterResource(id), null, modifier.size(size)); return
    }
    val ph = remember(name) { phase(name) }
    // 1.3.5 (speed): no endless clock per icon any more. An active icon moves all the time (it means "working now");
    // a resting icon sits still and plays a short burst (its gesture + the light crossing it) every 7-9 seconds,
    // staggered by its name, so the screen stops redrawing between bursts. Same motion, far fewer frames.
    val t = remember { Animatable(ph * 7f) }
    LaunchedEffect(active) {
        if (active) while (true) t.animateTo(t.value + 600f, tween(600_000, easing = LinearEasing))
        else {
            delay(600L + (ph * 2500).toLong())
            while (true) {
                // jump to the start of a light pass, play it (1.3 s), rest
                val base = t.value - ((t.value + ph * 5f) % 5.2f) + 5.2f
                t.snapTo(base); t.animateTo(base + 1.3f, tween(1300, easing = LinearEasing))
                delay(7000L + (ph * 2000).toLong())
            }
        }
    }
    // pop when the icon appears or becomes active
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(active) { pop.snapTo(if (active) 0.75f else 0.9f); pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow)) }
    val k = if (active) 1f else 0.35f
    Image(painterResource(id), null, modifier.size(size)
        .graphicsLayer {
            val tt = if (active) t.value + ph * 7f else 0f
            val g = gesture(name, tt, k)
            val breathe = if (active) 1f + 0.03f * sin(2f * PI.toFloat() * tt / (3.2f + ph * 1.4f)) else 1f
            val float = if (active) 0.035f * sin(2f * PI.toFloat() * tt / (4.1f + ph)) else 0f
            rotationZ = g[0]
            translationX = g[1] * this.size.width
            translationY = (g[2] + float) * this.size.height
            scaleX = g[3] * breathe * pop.value
            scaleY = g[4] * breathe * pop.value
            rotationY = g[5]
            cameraDistance = 10f * density
            // the sheen touches the icon pixels only; an offscreen layer only while it is drawn
            compositingStrategy = if (((t.value + ph * 5f) % 5.2f) / 1.1f < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        .drawWithContent {
            drawContent()
            // a soft light crosses the icon about every 5 seconds
            val cycle = ((t.value + ph * 5f) % 5.2f) / 1.1f
            if (cycle < 1f) {
                val x = -this.size.width * 0.6f + cycle * this.size.width * 2.2f
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                    start = Offset(x - this.size.width * 0.35f, 0f), end = Offset(x + this.size.width * 0.35f, this.size.height)),
                    blendMode = BlendMode.SrcAtop)
            }
        })
}

// ============================================================================================
// Living eyes for «ماذا يفعل الآن»: they blink at random, look at the newest line, narrow while the AI
// thinks, open wide on success and look where you touch them.
// ============================================================================================

enum class EyeMood { Calm, Thinking, Happy, Worried }

@Stable
class EyesState {
    var mood by mutableStateOf(EyeMood.Calm)
    var news by mutableIntStateOf(0)      // +1 for every new line: the eyes glance at it
}

val LocalEyes = staticCompositionLocalOf<EyesState?> { null }

@Composable
fun LiveEyes(size: Dp, modifier: Modifier = Modifier) {
    val st = LocalEyes.current ?: remember { EyesState() }
    val motion = LocalMotion.current
    val blink = remember { Animatable(1f) }          // 1 open, 0 closed
    val lookX = remember { Animatable(0f) }
    val lookY = remember { Animatable(0f) }
    val open = remember { Animatable(1f) }           // mood opening
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(motion) {
        if (motion == 0f) return@LaunchedEffect
        val r = Random(System.nanoTime())
        while (true) {
            delay(1800L + r.nextLong(3800))
            blink.animateTo(0f, tween(70)); blink.animateTo(1f, tween(110))
            if (r.nextFloat() < 0.18f) { delay(120); blink.animateTo(0f, tween(60)); blink.animateTo(1f, tween(100)) }
        }
    }
    LaunchedEffect(motion) {
        if (motion == 0f) return@LaunchedEffect
        val r = Random(System.nanoTime() + 7)
        while (true) {   // idle wandering
            delay(1400L + r.nextLong(2600))
            launch { lookX.animateTo(r.nextFloat() * 1.6f - 0.8f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) }
            lookY.animateTo(r.nextFloat() * 0.8f - 0.3f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
        }
    }
    LaunchedEffect(st.news) {   // a new line arrived below: glance down at it
        if (motion == 0f || st.news == 0) return@LaunchedEffect
        launch { lookX.animateTo(-0.2f, tween(160)) }
        lookY.animateTo(0.9f, tween(160)); delay(700)
        lookY.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(st.mood) {
        open.animateTo(when (st.mood) { EyeMood.Thinking -> 0.62f; EyeMood.Happy -> 1.15f; EyeMood.Worried -> 0.85f; else -> 1f },
            spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
    }
    Canvas(modifier.size(size).pointerInput(Unit) {
        detectTapGestures { p ->
            scope.launch { lookX.animateTo(((p.x / this@pointerInput.size.width) * 2f - 1f).coerceIn(-1f, 1f), tween(120)) }
            scope.launch { lookY.animateTo(((p.y / this@pointerInput.size.height) * 2f - 1f).coerceIn(-1f, 1f), tween(120)) }
            scope.launch { blink.animateTo(0f, tween(60)); blink.animateTo(1f, tween(120)) }
        }
    }) {
        val w = this.size.width; val h = this.size.height
        val ew = w * 0.42f; val eh = h * 0.62f * open.value.coerceAtMost(1.1f)
        val lid = (blink.value).coerceIn(0.05f, 1f)
        listOf(w * 0.27f, w * 0.73f).forEach { cx ->
            val cy = h * 0.5f
            val top = cy - eh / 2 * lid; val hh = eh * lid
            // eyeball with a soft 3D shade
            drawOval(Brush.radialGradient(listOf(Color.White, Color(0xFFE6E8EE)), Offset(cx - ew * 0.15f, top + hh * 0.3f), ew),
                Offset(cx - ew / 2, top), Size(ew, hh))
            drawOval(Color(0x33000000), Offset(cx - ew / 2, top), Size(ew, hh), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.02f))
            if (lid > 0.25f) {
                val pr = ew * (if (st.mood == EyeMood.Happy) 0.30f else 0.26f)
                val px = cx + lookX.value * (ew / 2 - pr) * 0.9f
                val py = cy + lookY.value * (hh / 2 - pr) * 0.8f
                drawCircle(Color(0xFF3B2A1A), pr, Offset(px, py))
                drawCircle(Color(0xFF0E0E10), pr * 0.55f, Offset(px, py))
                drawCircle(Color.White.copy(alpha = 0.9f), pr * 0.22f, Offset(px - pr * 0.35f, py - pr * 0.35f))
            }
        }
    }
}
