@file:OptIn(ExperimentalMaterial3Api::class)

package ma.stickman.studio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ma.stickman.studio.data.CoachInfo
import ma.stickman.studio.ui.theme.Good
import ma.stickman.studio.ui.theme.ReplyStyle
import java.util.Locale

// ============================================================================================
// 1.2.0 «المدرّب»: the score of every video (0-100), the trend of the last five, the lessons it learned from the
// TikTok numbers (with their evidence), and the experiment it will run in the next video.
// ============================================================================================

/** One line of progress: "متوسط آخر 5 فيديوهات ↑ 8%" with a thin line of the last 10 scores. */
@Composable
fun ProgressLine(c: CoachInfo?, modifier: Modifier = Modifier) {
    val scores = c?.scores?.takeLast(10)?.map { it.score.toFloat() }.orEmpty()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FxIcon("coach", 20.dp, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val t = c?.trendPct
            Text(when {
                c == null -> "المدرّب لم يقيّم أي فيديو بعد"
                c.avgLast5 == null -> "علامات الفيديوهات تظهر بعد أول تقييم"
                t == null -> "متوسط آخر الفيديوهات: ${c.avgLast5.toInt()}/100"
                else -> "متوسط آخر 5 فيديوهات: ${c.avgLast5.toInt()}/100  ${if (t >= 0) "↑" else "↓"} ${"%.0f".format(Locale.US, kotlin.math.abs(t))}%"
            }, style = MaterialTheme.typography.labelLarge)
            if (c != null) Text(if (c.withStats == 0) "خذ لقطة لتحليلات فيديو منشور في تيك توك ليتعلّم منها" else "تعلّم من أرقام ${c.withStats} فيديو منشور",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (scores.size >= 2) Sparkline(scores, Modifier.width(88.dp).height(28.dp))
    }
}

@Composable
fun Sparkline(values: List<Float>, modifier: Modifier) {
    val c = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val lo = (values.minOrNull() ?: 0f) - 5f; val hi = (values.maxOrNull() ?: 100f) + 5f
        val p = Path()
        values.forEachIndexed { i, v ->
            // newest on the left, like the Arabic reading direction
            val x = size.width - i * size.width / (values.size - 1)
            val y = size.height - (v - lo) / (hi - lo).coerceAtLeast(1f) * size.height
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, c, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        val lx = 0f
        val ly = size.height - (values.last() - lo) / (hi - lo).coerceAtLeast(1f) * size.height
        drawCircle(c, 3.dp.toPx(), Offset(lx, ly))
    }
}

/** The small card on the home screen. */
@Composable
fun CoachMini(vm: AppViewModel) {
    LaunchedEffect(Unit) { if (vm.coach == null) vm.loadCoach() }
    val c = vm.coach
    Card(onClick = { vm.showCoach = true }) {
        Column {
            ProgressLine(c)
            // 1.3.4: the newest published video with its real TikTok numbers, against the channel's own average
            val videos by vm.local.videos.collectAsState()
            videos.filter { it.publishedAt > 0 && it.hasStats }.maxByOrNull { it.statsAt }?.let { v ->
                Spacer(Modifier.height(10.dp))
                Text("آخر فيديو «${v.title}»: " + ma.stickman.studio.data.Insights.verdict(v, videos),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
            }
            c?.lessons?.firstOrNull()?.let { l ->
                Spacer(Modifier.height(10.dp))
                Text("آخر درس: ${l.text}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            c?.next?.let { e ->
                Spacer(Modifier.height(6.dp))
                Text("التجربة القادمة: ${e.value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
        }
    }
}

@Composable
fun CoachSheet(vm: AppViewModel, dismiss: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadCoach() }
    val c = vm.coach
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.background) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SparkMark(26.dp); Spacer(Modifier.width(10.dp))
                    Text("المدرّب", style = MaterialTheme.typography.headlineSmall)
                }
                Text("يقيّم كل فيديو بمعايير ثابتة، ويتعلّم من أرقام تيك توك، ويغيّر شيئًا واحدًا في كل فيديو ليعرف ما ينجح.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { Card { ProgressLine(c) } }
            if (c == null) item {
                Text("لم يكتب المدرّب تقريره الأول بعد. يعمل بعد كل فيديو، وكل يوم أحد مراجعةً أسبوعية.", style = ReplyStyle)
            } else {
                c.next?.let { e -> item {
                    Card(accent = MaterialTheme.colorScheme.primary) { Column {
                        SectionTitle("التجربة في الفيديو القادم", fx = "bulb")
                        Text("${e.variable}: ${e.value}", style = MaterialTheme.typography.titleSmall)
                        if (e.why.isNotBlank()) Text(e.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                } }
                item { Text("دفتر الدروس", style = MaterialTheme.typography.titleMedium) }
                if (c.lessons.isEmpty()) item {
                    Text("لا دروس بعد: يلزم أرقام فيديوهين منشورين على الأقل. أرسلها من «المشاريع» بلقطة شاشة.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                c.lessons.forEach { l -> item {
                    Row(verticalAlignment = Alignment.Top) {
                        androidx.compose.foundation.layout.Box(Modifier.padding(top = 10.dp).size(7.dp).clip(CircleShape)
                            .background(if (l.strength == "strong") Good else MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(l.text, style = ReplyStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                            if (l.evidence.isNotBlank()) Text(l.evidence, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } }
                item { AxisChart(vm.local.videos.value) }
                if (c.review.isNotBlank()) {
                    item { Text("المراجعة الأسبوعية", style = MaterialTheme.typography.titleMedium) }
                    item { Markdown(c.review) }
                }
                if (c.scores.isNotEmpty()) {
                    item { Text("علامات الفيديوهات", style = MaterialTheme.typography.titleMedium) }
                    c.scores.takeLast(12).reversed().forEach { s -> item {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.title.ifBlank { s.slug }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("${s.score.toInt()}", style = MaterialTheme.typography.titleSmall,
                                    color = if (s.score >= 75) Good else MaterialTheme.colorScheme.primary)
                            }
                            ScoreBar(s.score.toFloat() / 100f)
                            if (s.weakest.isNotEmpty()) Text("يُحسَّن: " + s.weakest.joinToString("، "), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } }
                }
                item { Text("آخر تحديث: ${c.updated}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun ScoreBar(f: Float) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val c = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(5.dp)) {
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f))
        drawRoundRect(c, topLeft = Offset(size.width * (1 - f.coerceIn(0f, 1f)), 0f),
            size = androidx.compose.ui.geometry.Size(size.width * f.coerceIn(0f, 1f), size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f))
    }
}

@Suppress("unused") private val unusedColor = Color.Unspecified
