package ma.stickman.studio.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ma.stickman.studio.data.LiveEvent
import ma.stickman.studio.data.LiveStatus
import ma.stickman.studio.data.Step
import ma.stickman.studio.ui.theme.Cairo
import ma.stickman.studio.ui.theme.LocalLight
import ma.stickman.studio.ui.theme.LocalMotion

// ============================================================================================
// The step list in the style of a coding assistant's work log (1.1.4): warm dark surface, ivory text, a
// terracotta accent for the step running now, one quiet row per action with its icon on a thin thread,
// the command in a monospace box when the row is opened, its result in one line, ✓ / ✕ and the duration,
// thumbnails for pictures, the agent's plan as a checklist and its short thoughts in grey italics.
// Our own design and icons: no logo or brand of anyone else.
// ============================================================================================

@Immutable
data class LogPalette(
    val card: Color, val row: Color, val line: Color, val text: Color, val muted: Color, val faint: Color,
    val accent: Color, val good: Color, val bad: Color, val code: Color, val codeText: Color, val noteBg: Color, val noteText: Color,
)

private val DarkLog = LogPalette(
    card = Color(0xFF262624), row = Color(0xFF2E2D2A), line = Color(0xFF3D3B36), text = Color(0xFFF4F3EE),
    muted = Color(0xFFA9A69C), faint = Color(0xFF75736C), accent = Color(0xFFD97757), good = Color(0xFF79B987),
    bad = Color(0xFFE5705F), code = Color(0xFF1C1B19), codeText = Color(0xFFE8E4D8), noteBg = Color(0xFF3B2B20), noteText = Color(0xFFF2BC97),
)
private val LightLog = LogPalette(
    card = Color(0xFFFAF9F5), row = Color(0xFFF1EFE8), line = Color(0xFFE2DED3), text = Color(0xFF1F1E1D),
    muted = Color(0xFF6B6860), faint = Color(0xFF9C998F), accent = Color(0xFFC2613F), good = Color(0xFF3F8F55),
    bad = Color(0xFFC0473A), code = Color(0xFFF0EEE6), codeText = Color(0xFF2B2A27), noteBg = Color(0xFFFBE9DD), noteText = Color(0xFF8A4424),
)

@Composable
fun logPalette() = if (LocalLight.current) LightLog else DarkLog

private val Mono = FontFamily.Monospace

private fun kindIcon(kind: String, text: String): ImageVector = when (kind) {
    "search" -> Icons.Rounded.Search
    "news" -> Icons.Rounded.Newspaper
    "web" -> Icons.Rounded.Language
    "read" -> Icons.Rounded.Description
    "look" -> Icons.Rounded.Visibility
    "photo" -> Icons.Rounded.PhotoCamera
    "picture" -> Icons.Rounded.Image
    "write" -> Icons.Rounded.EditNote
    "edit" -> Icons.Rounded.Edit
    "folder" -> Icons.Rounded.FolderOpen
    "check" -> Icons.Rounded.Shield
    "film" -> Icons.Rounded.Movie
    "gear" -> Icons.Rounded.Settings
    "mic" -> Icons.Rounded.Mic
    "package" -> Icons.Rounded.Inventory2
    "trash" -> Icons.Rounded.DeleteOutline
    "switch" -> Icons.Rounded.SwapHoriz
    "plan" -> Icons.Rounded.Checklist
    "done" -> Icons.Rounded.TaskAlt
    "think" -> Icons.Rounded.AutoAwesome
    "run" -> Icons.Rounded.Terminal
    // live.json of 1.1.3 and older: no kind, guess it from the line
    else -> when {
        text.startsWith("يبحث") -> Icons.Rounded.Search
        text.startsWith("يقرأ مصدرًا") -> Icons.Rounded.Language
        text.startsWith("يراجع صورة") -> Icons.Rounded.Visibility
        text.startsWith("يكتب") -> Icons.Rounded.EditNote
        text.contains("صورة حقيقية") -> Icons.Rounded.PhotoCamera
        text.contains("يفحص") -> Icons.Rounded.Shield
        text.contains("معاينة") || text.contains("الفيديو") -> Icons.Rounded.Movie
        text.startsWith("انتقل") -> Icons.Rounded.SwapHoriz
        text.startsWith("أنهى") -> Icons.Rounded.TaskAlt
        else -> Icons.Rounded.Terminal
    }
}

/** Consecutive identical actions become one row «×N» (three «يقرأ مصدرًا» in a row). */
private data class Row1(val e: LiveEvent, val n: Int, val okAll: Boolean?, val durSum: Double?)

private fun merge(events: List<LiveEvent>): List<Row1> {
    val out = mutableListOf<Row1>()
    for (e in events) {
        val last = out.lastOrNull()
        if (last != null && e.kind != "think" && e.kind != "switch" && last.e.text == e.text && last.e.kind == e.kind && e.thumb.isEmpty()) {
            val ok = when { last.okAll == false || e.ok == false -> false; last.okAll == null || e.ok == null -> null; else -> true }
            val d = if (last.durSum != null && e.dur != null) last.durSum + e.dur else last.durSum ?: e.dur
            out[out.lastIndex] = Row1(e.copy(result = e.result.ifBlank { last.e.result }), last.n + 1, ok, d)
        } else out += Row1(e, 1, e.ok, e.dur)
    }
    return out
}

fun secs(d: Double): String = when {
    d < 1 -> "أقل من ث"
    d < 60 -> "${d.toInt()} ث"
    else -> "%d:%02d".format((d / 60).toInt(), (d % 60).toInt())
}

private fun ago(s: Long) = when { s < 5 -> "الآن"; s < 60 -> "قبل $s ث"; else -> "قبل ${s / 60} د" }

/** A small terracotta arc that turns: the action running now. */
@Composable
fun LogSpinner(color: Color, size: Dp = 14.dp) {
    val motion = LocalMotion.current
    val a = if (motion == 0f) 0f else {
        val inf = rememberInfiniteTransition(label = "spin")
        inf.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a").value
    }
    Canvas(Modifier.size(size)) {
        val w = this.size.minDimension * 0.16f
        drawCircle(color.copy(alpha = 0.18f), radius = this.size.minDimension / 2 - w / 2, style = Stroke(w))
        drawArc(color, if (motion == 0f) 0f else a, 100f, false, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Text with a soft light passing over it, for the line running now. */
@Composable
private fun shimmer(base: Color, light: Color): Brush {
    val motion = LocalMotion.current
    if (motion == 0f) return Brush.linearGradient(listOf(base, base))
    val inf = rememberInfiniteTransition(label = "shimmer")
    val x by inf.animateFloat(-300f, 1300f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "x")
    return Brush.linearGradient(listOf(base, light, base), start = Offset(x - 200f, 0f), end = Offset(x + 200f, 0f))
}

@Composable
private fun Thumb(b64: String, size: Dp) {
    val bmp: ImageBitmap? = rememberB64Bitmap(b64)
    bmp?.let { Image(it, null, Modifier.size(size).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }
}

/** Command / path box: left-to-right, monospace, scrolls sideways instead of wrapping long commands. */
@Composable
fun CodeBox(text: String, p: LogPalette) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.code).border(1.dp, p.line, RoundedCornerShape(10.dp))
            .horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(text.take(400), style = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 17.sp, color = p.codeText,
                textDirection = TextDirection.Ltr))
        }
    }
}

/** One row on the thread: icon (or spinner) on the start side, the line, its result, ✓ / ✕ and duration. */
@Composable
private fun LogRow(r: Row1, p: LogPalette, first: Boolean, last: Boolean, active: Boolean, nowSec: Long, open: Boolean, onTap: () -> Unit) {
    val e = r.e
    val think = e.kind == "think"
    val switch = e.kind == "switch"
    val canOpen = e.detail.isNotBlank() || (e.result.isNotBlank() && e.result.length > 46)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(10.dp))
        .background(if (open) p.row else Color.Transparent)
        .then(if (canOpen) Modifier.clickable(onClick = onTap) else Modifier).padding(end = 6.dp)) {
        // the thread: a thin line through the icons, joining the actions
        Box(Modifier.width(30.dp).fillMaxHeight().drawBehind {
            val x = size.width / 2
            if (!first) drawLine(p.line, Offset(x, 0f), Offset(x, 10.dp.toPx()), 1.dp.toPx())
            if (!last) drawLine(p.line, Offset(x, 30.dp.toPx()), Offset(x, size.height), 1.dp.toPx())
        }, contentAlignment = Alignment.TopCenter) {
            Box(Modifier.padding(top = 10.dp).size(20.dp), contentAlignment = Alignment.Center) {
                when {
                    active -> LogSpinner(p.accent, 16.dp)
                    think -> Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(13.dp), tint = p.faint)
                    switch -> Icon(Icons.Rounded.WarningAmber, null, Modifier.size(16.dp), tint = p.noteText)
                    else -> Icon(kindIcon(e.kind, e.text), null, Modifier.size(16.dp), tint = if (e.ok == false) p.bad else p.muted)
                }
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val style = if (think) TextStyle(fontFamily = Cairo, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 20.sp, color = p.faint)
                else TextStyle(fontFamily = Cairo, fontWeight = FontWeight(if (active) 700 else 600), fontSize = 14.sp, lineHeight = 21.sp)
                Text(e.text, Modifier.weight(1f, fill = false), maxLines = if (open || think) 4 else 2, overflow = TextOverflow.Ellipsis,
                    style = if (active && !think) style.copy(brush = shimmer(p.text, p.accent)) else style.copy(color = if (think) p.faint else p.text))
                if (r.n > 1) {
                    Spacer(Modifier.width(6.dp))
                    Text("×${r.n}", style = TextStyle(fontFamily = Mono, fontSize = 11.sp, color = p.muted),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(p.row).padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            if (e.result.isNotBlank() && !think) {
                Text(e.result, maxLines = if (open) 6 else 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = Cairo, fontSize = 12.sp, lineHeight = 18.sp,
                        color = when { switch -> p.noteText; r.okAll == false -> p.bad; else -> p.muted }))
            }
            AnimatedVisibility(open && e.detail.isNotBlank(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column { Spacer(Modifier.height(6.dp)); CodeBox(e.detail, p) }
            }
        }
        if (e.thumb.isNotBlank()) { Spacer(Modifier.width(8.dp)); Box(Modifier.padding(top = 6.dp)) { Thumb(e.thumb, if (open) 72.dp else 40.dp) } }
        if (!think) {
            Column(Modifier.padding(start = 8.dp, top = 9.dp), horizontalAlignment = Alignment.End) {
                when {
                    active -> {}
                    r.okAll == true -> Text("✓", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.good))
                    r.okAll == false && !switch -> Text("✕", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.bad))
                }
                val d = r.durSum
                Text(if (active) ago(nowSec - e.t) else if (d != null && d >= 1) secs(d) else "",
                    style = TextStyle(fontFamily = Cairo, fontSize = 11.sp, color = p.faint))
            }
        }
    }
}

/** «ماذا يفعل الآن» in the work-log style. */
@Composable
fun LiveLog(live: LiveStatus, running: Boolean, nowSec: Long, header: @Composable () -> Unit) {
    val p = logPalette()
    val opened = remember { mutableStateMapOf<Int, Boolean>() }
    var all by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.card).border(1.dp, p.line, RoundedCornerShape(22.dp))
        .padding(14.dp).animateContentSize()) {
        header()
        if (live.note.isNotBlank() && running) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.noteBg).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WarningAmber, null, Modifier.size(18.dp), tint = p.noteText)
                Spacer(Modifier.width(8.dp))
                Text(live.note, style = TextStyle(fontFamily = Cairo, fontSize = 13.sp, lineHeight = 20.sp, color = p.noteText))
            }
        }
        if (live.plan.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("الخطة", style = TextStyle(fontFamily = Cairo, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = p.faint))
            Spacer(Modifier.height(4.dp))
            live.plan.forEach { it ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                        when (it.status) {
                            "done" -> Text("✓", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.good))
                            "now" -> if (running) LogSpinner(p.accent, 13.dp) else Box(Modifier.size(8.dp).clip(CircleShape).background(p.accent))
                            else -> Box(Modifier.size(10.dp).clip(CircleShape).border(1.5.dp, p.faint, CircleShape))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(it.text, style = TextStyle(fontFamily = Cairo, fontSize = 13.5.sp,
                        fontWeight = if (it.status == "now") FontWeight.Bold else FontWeight.Medium,
                        color = when (it.status) { "done" -> p.muted; "now" -> p.text; else -> p.faint },
                        textDecoration = if (it.status == "done") TextDecoration.LineThrough else null))
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        }
        Spacer(Modifier.height(8.dp))
        val rows = merge(live.events)
        val keep = 14
        val hidden = if (all) 0 else (rows.size - keep).coerceAtLeast(0)
        if (hidden > 0) {
            Text("اعرض الأقدم ($hidden)", style = TextStyle(fontFamily = Cairo, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = p.accent),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { all = true }.padding(horizontal = 8.dp, vertical = 6.dp))
        }
        val shown = rows.drop(hidden)
        val activeIdx = if (running) shown.indexOfLast { it.e.kind != "think" }.takeIf { it >= 0 && shown[it].e.ok == null } ?: -1 else -1
        shown.forEachIndexed { i, r ->
            val key = i + hidden
            LogRow(r, p, first = i == 0 && hidden == 0, last = i == shown.lastIndex, active = i == activeIdx, nowSec = nowSec,
                open = opened[key] == true) { opened[key] = opened[key] != true }
        }
        if (!running && live.summary.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.row).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.TaskAlt, null, Modifier.size(18.dp), tint = p.good)
                    Spacer(Modifier.width(8.dp))
                    Text("الخلاصة", style = TextStyle(fontFamily = Cairo, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = p.text))
                }
                Spacer(Modifier.height(4.dp))
                Text(live.summary, style = TextStyle(fontFamily = Cairo, fontSize = 13.sp, lineHeight = 21.sp, color = p.muted))
            }
        }
    }
}

/** The GitHub steps, same style: a title per server job, ✓ / ✕ / spinner, «تُخطّيت», duration; a step without an
 *  Arabic name shows «خطوة تقنية» and its raw name only when opened. */
@Composable
fun StepsLog(steps: List<Step>) {
    val p = logPalette()
    val opened = remember { mutableStateMapOf<Int, Boolean>() }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.card).border(1.dp, p.line, RoundedCornerShape(22.dp))
        .padding(horizontal = 12.dp, vertical = 10.dp)) {
        steps.forEachIndexed { i, s ->
            if (s.name.startsWith("§")) {
                Row(Modifier.padding(top = if (i == 0) 2.dp else 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(StepNames.job(s.name.drop(1)), style = TextStyle(fontFamily = Cairo, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = p.accent))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(p.line))
                }
                return@forEachIndexed
            }
            val known = StepNames.known(s.name)
            val running = s.status == "in_progress"
            val skipped = s.conclusion == "skipped"
            val open = opened[i] == true
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (open || running) p.row else Color.Transparent)
                .then(if (!known) Modifier.clickable { opened[i] = !open } else Modifier).padding(horizontal = 6.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    when {
                        running -> LogSpinner(p.accent, 15.dp)
                        s.conclusion == "success" -> Text("✓", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.good))
                        s.conclusion == "failure" -> Text("✕", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.bad))
                        skipped -> Text("–", style = TextStyle(fontSize = 13.sp, color = p.faint))
                        else -> Box(Modifier.size(9.dp).clip(CircleShape).border(1.5.dp, p.faint, CircleShape))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (known) StepNames.label(s.name) else "خطوة تقنية", style = TextStyle(fontFamily = Cairo, fontSize = 14.sp,
                        fontWeight = if (running) FontWeight.Bold else FontWeight.Medium,
                        color = if (skipped || s.status == "queued" || s.status == "pending") p.faint else p.text))
                    if (open) { Spacer(Modifier.height(4.dp)); CodeBox(s.name, p) }
                }
                Text(when { skipped -> "تُخطّيت"; else -> stepTime(s) ?: "" }, style = TextStyle(fontFamily = Cairo, fontSize = 11.sp, color = p.faint))
            }
        }
    }
}

private fun stepTime(s: Step): String? = runCatching {
    if (s.startedAt.isBlank() || s.completedAt.isBlank()) return null
    val d = java.time.Duration.between(java.time.Instant.parse(s.startedAt), java.time.Instant.parse(s.completedAt)).seconds
    if (d < 1) "أقل من ث" else if (d < 60) "$d ث" else "%d:%02d".format(d / 60, d % 60)
}.getOrNull()

@Composable
fun LogChip(text: String, color: Color) {
    val p = logPalette()
    Text(text, style = TextStyle(fontFamily = Cairo, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = color),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.13f)).border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp))
}

