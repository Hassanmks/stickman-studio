package ma.stickman.studio.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ma.stickman.studio.ui.theme.Bad
import ma.stickman.studio.ui.theme.Gold
import ma.stickman.studio.ui.theme.Good
import java.util.Locale

private val PHASES_BRAIN = listOf("gear" to "تشغيل", "robot" to "الذكاء", "check" to "الفحص", "package" to "الحفظ")
private val PHASES_PRODUCE = listOf("gear" to "تجهيز", "robot" to "الذكاء", "check" to "الفحوص", "film" to "التصيير", "package" to "الإنهاء")

/** 1.2.4: the model that really answers. Coarena runs Claude when it is on, else GPT, Gemini or Kimi on the same money. */
fun prettyModel(id: String): String {
    val brands = mapOf("claude" to "Claude", "gpt" to "GPT", "gemini" to "Gemini", "kimi" to "Kimi", "deepseek" to "DeepSeek", "grok" to "Grok")
    val num = Regex("k?\\d+")
    val out = mutableListOf<String>()
    for (t in id.substringAfter('/').split('-').filter { it.isNotBlank() }) {
        val isNum = t.matches(num)
        val prev = out.lastOrNull()
        if (isNum && prev != null && prev.last().isDigit() && prev.any { it.isDigit() } && !prev.contains(' ') && prev.matches(Regex("[KG]?[\\d.]+|GPT-[\\d.]+")))
            out[out.size - 1] = "$prev.${t.uppercase()}"
        else if (isNum && prev == "GPT") out[out.size - 1] = "GPT-$t"
        else out += brands[t] ?: t.uppercase().takeIf { isNum } ?: t.replaceFirstChar { it.uppercase() }
    }
    return out.joinToString(" ")
}

private fun modelName(m: String) = when {
    m.startsWith("coarena") -> "Coarena · " + prettyModel(m)
    m.startsWith("gemini") -> "Gemini Flash"
    m.contains(":free") -> "نموذج مجاني"
    m.isBlank() -> "…"
    else -> m.substringAfter('/')
}

@Composable
fun Running(vm: AppViewModel, title: String, etaHint: String) {
    val job by vm.job.collectAsState()
    val run = vm.run
    val live = vm.live
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    var askCancel by remember { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    // 1.2.5: time from GitHub's clock (run created, steps started), the estimate from measured runs of the same kind
    val eta = ma.stickman.studio.data.Timing.estimate(vm.local, job.stage.name, run, live?.model, now, live?.plan.orEmpty())
    val secs = if (run != null) eta.elapsedS else ((now - job.startedAt) / 1000).coerceAtLeast(0)
    val allSteps = run?.steps?.filterNot { StepNames.hidden(it.name) } ?: emptyList()
    val steps = allSteps.filterNot { it.name.startsWith("§") }
    val doneCount = steps.count { it.status == "completed" }
    val frac = if (run?.done == true) 1f else eta.fraction
    val shown by animateFloatAsState(frac, spring(stiffness = Spring.StiffnessVeryLow), label = "f")
    val produce = job.workflow == "produce.yml"
    val phases = if (produce) PHASES_PRODUCE else PHASES_BRAIN
    val curPhase = steps.firstOrNull { it.status == "in_progress" }?.let { StepNames.phase(it.name) }
        ?.let { if (produce) it else when (it) { 0 -> 0; 1 -> 1; 2 -> 2; else -> 3 } }
        ?: if (run?.done == true) phases.size else 0
    val list = rememberParallaxList()

    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Pipeline(if (produce) 3 else if (job.stage.name.startsWith("TOPICS")) 0 else 1) }
        item { JourneyLine(vm) }
        item {
            Card(tilt = true) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(); Spacer(Modifier.width(10.dp))
                        val queued = eta.queuedS != null
                        Text(if (queued) "في الطابور: ينتظر خادمًا من GitHub" else "يعمل على سحابة GitHub",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        RollingNumber("%d:%02d".format(secs / 60, secs % 60), MaterialTheme.typography.titleMedium)
                    }
                    Gap(10)
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (job.title.isNotBlank()) { Gap(4); Text(job.title, color = Gold, style = MaterialTheme.typography.bodyLarge) }
                    Gap(16)
                    PhaseLine(phases, curPhase)
                    Gap(14)
                    LinearProgressIndicator(progress = { shown }, Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant, color = Gold)
                    Gap(8)
                    val leftLine = if (run == null) "يبدأ التشغيل على GitHub…" else eta.line()
                    Text(leftLine, style = MaterialTheme.typography.bodyMedium,
                        color = if (eta.overS != null) Gold else MaterialTheme.colorScheme.onSurface)
                    Gap(2)
                    Text("الوقت من ساعة GitHub نفسها. يمكنك إغلاق التطبيق: يتابع الإشعار التقدّم، وفيه زر الإلغاء.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // what the AI does right now (cloud/agent.py writes it on the branch "status")
        item {
            // the eyes narrow while the AI works, open wide when the run is done and glance at every new line
            val eyes = remember { EyesState() }
            eyes.mood = when { run?.done == true && run.ok -> EyeMood.Happy; run?.done == true -> EyeMood.Worried
                live != null -> EyeMood.Thinking; else -> EyeMood.Calm }
            LaunchedEffect(live?.events?.size, live?.events?.lastOrNull()?.t) { if (live != null) eyes.news++ }
            androidx.compose.runtime.CompositionLocalProvider(LocalEyes provides eyes) {
            if (live == null) Card { Column {
                SectionTitle("ماذا يفعل الآن", fx = "eyes")
                Text(if (curPhase == 1) "ينتظر أول خبر من الذكاء الاصطناعي…" else "المتابعة الحية تظهر حين يعمل الذكاء الاصطناعي.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } } else LiveLog(live, running = run?.done != true, nowSec = now / 1000) {
                // 1.1.4: the work log in the style of a coding assistant (ui/StepLog.kt)
                val pal = logPalette()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveEyes(30.dp); Spacer(Modifier.width(10.dp))
                    Text("ماذا يفعل الآن", style = MaterialTheme.typography.titleMedium, color = pal.text, modifier = Modifier.weight(1f))
                    // 1.3.5: this figure is the step running now; the whole video's money is on «رحلة الفيديو» above
                    Text("هذه الخطوة ", style = MaterialTheme.typography.labelSmall, color = pal.muted)
                    RollingNumber("%.2f$".format(Locale.US, live.costUsd), MaterialTheme.typography.titleSmall, Gold)
                }
                // 1.2.5: where the money figure comes from, and today's balance read from Coarena itself
                Text(costSource(live.model) + (vm.coarenaLeft?.let { l -> " · المتبقي اليوم %.2f$ (%s)".format(Locale.US, l, ago(((now - vm.coarenaAt) / 1000).coerceAtLeast(0))) } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = pal.muted)
                byModelLine(live.byModel)?.let { Text("في هذه الخطوة: $it", style = MaterialTheme.typography.bodySmall, color = pal.muted) }
                Gap(8)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModelChip(vm, modelName(live.model), if (live.model.contains(":free")) pal.noteText else pal.accent)
                    LogChip("الخطوة ${live.turn}", pal.muted)
                }
            }
            }
        }
        item {
            Text(if (details) "أخفِ تفاصيل الخطوات" else "اعرض تفاصيل الخطوات (${doneCount}/${steps.size})", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(CircleShape).pressable { details = !details }.padding(8.dp))
        }
        if (details) {
            if (allSteps.isEmpty()) item { repeat(4) { Shimmer(Modifier.fillMaxWidth().height(44.dp)); Gap(8) } }
            else item { StepsLog(allSteps) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                run?.url?.let { GhostButton("فتح على GitHub", icon = Icons.AutoMirrored.Rounded.OpenInNew) { openUrl(ctx, it) } }
                if (run?.done != true) PauseButton(vm)
                GhostButton("إلغاء", icon = Icons.Rounded.Close) { askCancel = true }
            }
        }
        item { Gap(30) }
    }
    if (askCancel) AlertDialog(onDismissRequest = { askCancel = false }, title = { Text("إلغاء التشغيل؟") },
        text = { Text("يتوقف العمل على GitHub. ما أنجزه الذكاء الاصطناعي من السيناريو يبقى، و«أعد المحاولة» يكمل منه.") },
        confirmButton = { TextButton({ askCancel = false; vm.cancelRun() }) { Text("ألغِ", color = Bad) } },
        dismissButton = { TextButton({ askCancel = false }) { Text("تابع العمل") } })
}

/** Stations of the run joined by a line; done ones are green, the current one pulses. */
@Composable
private fun PhaseLine(phases: List<Pair<String, String>>, current: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        phases.forEachIndexed { i, (fx, l) ->
            val done = i < current
            val now = i == current
            val k by animateFloatAsState(if (now) 1.12f else 1f, spring(dampingRatio = 0.4f), label = "ph")
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(Modifier.size((40 * k).dp).clip(CircleShape)
                    .background(when { done -> Good.copy(alpha = 0.22f); now -> Gold.copy(alpha = 0.25f); else -> MaterialTheme.colorScheme.surfaceVariant }),
                    contentAlignment = Alignment.Center) {
                    if (done) Text("✓", color = Good, style = MaterialTheme.typography.titleMedium) else FxIcon(fx, 24.dp, active = now)
                }
                Gap(4)
                Text(l, style = MaterialTheme.typography.labelSmall, color = if (now) Gold else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** The icon of a live line (texts written by cloud/agent.py, describe()). */
private fun eventFx(text: String) = when {
    text.startsWith("يبحث") -> "magnifier"
    text.startsWith("يقرأ مصدرًا") -> "books"
    text.startsWith("يراجع صورة") -> "picture"
    text.startsWith("يكتب") -> "scroll"
    text.contains("صورة حقيقية") -> "picture"
    text.contains("يفحص") -> "shield"
    text.contains("يصيّر") || text.contains("معاينة") -> "film"
    text.contains("انتقل") -> "rocket"
    text.startsWith("أنهى") -> "check"
    else -> "gear"
}

/** Is the money of this step read from the provider's account (measured) or computed from a price (estimated)? */
private fun costSource(model: String) = when {
    model.startsWith("coarena") -> "التكلفة مقاسة من حساب Coarena"
    model.contains(":free") -> "نموذج مجاني: بلا تكلفة"
    model.isBlank() -> "التكلفة: تُعرف عند أول نداء"
    else -> "التكلفة تقديرية (محسوبة من السعر)"
}

private fun ago(s: Long) = when { s < 5 -> "الآن"; s < 60 -> "قبل ${s} ث"; else -> "قبل ${s / 60} د" }

private fun stepSeconds(s: ma.stickman.studio.data.Step): String? = runCatching {
    if (s.startedAt.isBlank() || s.completedAt.isBlank()) return null
    val d = java.time.Duration.between(java.time.Instant.parse(s.startedAt), java.time.Instant.parse(s.completedAt)).seconds
    if (d < 60) "${d} ث" else "%d:%02d".format(d / 60, d % 60)
}.getOrNull()
