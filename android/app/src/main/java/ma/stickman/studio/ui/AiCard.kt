package ma.stickman.studio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ma.stickman.studio.data.Budget
import ma.stickman.studio.data.Stage
import ma.stickman.studio.ui.theme.Bad
import ma.stickman.studio.ui.theme.Gold
import ma.stickman.studio.ui.theme.Good
import ma.stickman.studio.ui.theme.Sky
import ma.stickman.studio.ui.theme.Violet
import ma.stickman.studio.ui.theme.Warn
import ma.stickman.studio.work.WatchWorker
import java.time.Instant
import java.util.Locale

// 1.3.3 «رصيد الذكاء الاصطناعي»: one question first (how many videos today's money buys), then what runs now,
// which model each task gets, and the other keys. Refreshes itself: every minute, every 15 s while a step runs.
// Tapping it opens the spend history. Storage and cloud speed moved to their own folded card «السحابة».

private val PROVIDER_COLOR = mapOf("coarena" to Gold, "gemini" to Sky, "openrouter" to Violet)
private fun usd(v: Double) = "%.2f$".format(Locale.US, v)

@Composable
fun AiCard(vm: AppViewModel) {
    val s by vm.settings.collectAsState()
    val job by vm.job.collectAsState()
    val running = job.stage in WatchWorker.RUNNING
    var sheet by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (true) {
            vm.refreshBudget()
            repeat(if (running) 3 else 12) { delay(5_000); now = System.currentTimeMillis() }
        }
    }
    val left = vm.coarenaLeft; val used = vm.coarenaUsed
    val per = Budget.perVideo(vm.ledger)
    Card(onClick = { sheet = true }) { Column {
        SectionTitle("رصيد الذكاء الاصطناعي", fx = "robot", trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.budgetAt > 0) Text(ago(now - vm.budgetAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton({ vm.refreshBudget(true) }) { Icon(Icons.Rounded.Refresh, "حدّث الرصيد") }
            }
        })

        // ---- the answer: videos today, reset, forecast
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Column(Modifier.weight(1f)) {
                val n = if (left != null && per != null && per > 0) (left / per).toInt() else null
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(n?.toString() ?: "–", fontSize = 44.sp, fontWeight = FontWeight.Bold,
                        color = when { n == null -> MaterialTheme.colorScheme.onSurface; n == 0 -> Bad; n == 1 -> Warn; else -> Good })
                    Spacer(Modifier.width(8.dp))
                    Text(when (n) { null -> "فيديو"; 0 -> "لا يكفي فيديو كاملًا"; 1 -> "فيديو واحد"; 2 -> "فيديوهان"; in 3..10 -> "فيديوهات"; else -> "فيديو" } + if (n != null && n > 0) " يكفيها رصيد اليوم" else "",
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                }
                per?.let { Text("الفيديو الكامل يكلّف عادة ${usd(it)} (السيناريو والإنتاج)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("يتجدد الرصيد بعد ${Budget.dur(Budget.msToReset(now))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (left != null && used != null) {
                    val out = Budget.runsOutAt(used, left, now)
                    Text(if (out != null) "بهذا الإيقاع ينفد نحو الساعة " + hm(out) else if (used >= 0.05) "بهذا الإيقاع يكفي حتى التجديد" else "لم يُصرف شيء اليوم بعد",
                        style = MaterialTheme.typography.labelMedium, color = if (out != null) Warn else Good)
                }
            }
            val tot = if (used != null && left != null) used + left else null
            Ring(if (left != null && tot != null && tot > 0) (left / tot).toFloat() else 0f, left?.let { usd(it) } ?: "–", "Coarena اليوم",
                when { left == null -> Good; left < 0.5 -> Bad; left < 1.5 -> Warn; else -> Good })
        }

        // ---- live: what is being spent right now
        AnimatedVisibility(running) {
            val l = vm.live
            val pulse by rememberInfiniteTransition(label = "p").animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
            Column(Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Gold.copy(alpha = 0.10f)).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).alpha(pulse).clip(CircleShape).background(Gold)); Spacer(Modifier.width(8.dp))
                    Text(buildString {
                        append("الآن: ").append(stageName(job.stage))
                        l?.model?.takeIf { it.isNotBlank() }?.let { append(" على ").append(Budget.modelName(it)) }
                    }, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
                    l?.let { Text("هذه الخطوة: " + usd(it.costUsd), style = MaterialTheme.typography.labelLarge, color = Gold) }
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape), color = Gold, trackColor = Gold.copy(alpha = 0.15f))
            }
        }

        // ---- which model each task gets now (shared order with the cloud, live on/off from Coarena)
        val lad = vm.ladders.forChoice(s.model)
        if (lad != null) {
            val on = vm.coarenaOn
            fun first(l: List<String>) = l.firstOrNull { on == null || it in on }
            Spacer(Modifier.height(4.dp))
            LadderRow("السيناريو", first(lad.first), lad.first, on)
            LadderRow("بقية الخطوات", first(lad.second), lad.second, on)
            if (on != null && "claude-sonnet-5-5" !in on && "claude-opus-5-5" !in on)
                Text("Claude موقوف حاليًا في Coarena، ويعود أولًا وحده حين يُشغَّل.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text("النموذج الحالي خارج Coarena: ${modelChoice(s.model)?.title ?: s.model}", style = MaterialTheme.typography.labelMedium)

        // ---- the other keys, with real numbers
        Spacer(Modifier.height(4.dp))
        val gToday = Budget.sinceReset(vm.ledger, "gemini")
        ProviderBar("Gemini Flash (مفتاحك)", "bulb", null, null,
            if (s.geminiSynced) "صُرف اليوم ${usd(gToday)} (تقديري)" else "غير مضاف", if (s.geminiSynced) 1 else 0)
        if (s.hasOpenRouterKey) vm.credit.let { c ->
            ProviderBar("OpenRouter (الاحتياط)", "globe", c?.remaining, c?.limit,
                when { c == null -> "…"; c.remaining != null -> usd(c.remaining) + " متبقٍّ"; else -> "مستعمل " + usd(c.usage) },
                when { c == null -> 0; (c.remaining ?: 1.0) < 0.3 -> 0; else -> 1 })
        }
        Text("اضغط لسجل الإنفاق ←", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp).align(Alignment.End))
    } }
    if (sheet) SpendSheet(vm) { sheet = false }
}

@Composable
private fun LadderRow(task: String, model: String?, ladder: List<String>, on: Set<String>?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(task, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(92.dp))
        Text(model?.let { Budget.modelName(it) } ?: "لا نموذج متاح الآن", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
        // the whole ladder as small dots: green = on, red = off in Coarena, grey = unknown
        ladder.take(6).forEach { m ->
            Box(Modifier.padding(start = 3.dp).size(if (m == model) 10.dp else 6.dp).clip(CircleShape)
                .background(when { on == null -> MaterialTheme.colorScheme.outline; m in on -> Good; else -> Bad.copy(alpha = 0.6f) }))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpendSheet(vm: AppViewModel, dismiss: () -> Unit) {
    val e = vm.ledger
    val titles = remember(vm.log) { vm.log.associate { it.slug to it.title } }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("سجل الإنفاق", style = MaterialTheme.typography.headlineSmall)
            if (e.isEmpty()) { Text("لا تشغيلات مسجلة بعد. يظهر هنا كل تشغيل مدفوع في السحابة.", color = MaterialTheme.colorScheme.onSurfaceVariant); return@Column }

            // 7 days, stacked by provider
            val days = Budget.lastDays(e)
            val maxDay = days.maxOf { it.second.values.sum() }.coerceAtLeast(0.5)
            Text("آخر 7 أيام", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                days.forEach { (d, by) ->
                    val total = by.values.sum()
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (total > 0) "%.1f".format(Locale.US, total) else "", style = MaterialTheme.typography.labelSmall)
                        val track = MaterialTheme.colorScheme.surfaceVariant
                        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                            drawRoundRect(track, cornerRadius = CornerRadius(8f, 8f))
                            var y = size.height
                            listOf("coarena", "gemini", "openrouter").forEach { k ->
                                val h = (((by[k] ?: 0.0) / maxDay) * size.height).toFloat()
                                if (h > 0f) { y -= h; drawRoundRect(PROVIDER_COLOR.getValue(k), Offset(0f, y), Size(size.width, h), CornerRadius(8f, 8f)) }
                            }
                        }
                        Text(if (d == days.last().first) "اليوم" else "${d.dayOfMonth}/${d.monthValue}", style = MaterialTheme.typography.labelSmall,
                            color = if (d == days.last().first) Gold else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf("coarena" to "Coarena", "gemini" to "Gemini", "openrouter" to "OpenRouter").forEach { (k, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(PROVIDER_COLOR.getValue(k))); Spacer(Modifier.width(5.dp))
                        Text(n, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text("مجموع الأسبوع: " + usd(days.sumOf { it.second.values.sum() }), style = MaterialTheme.typography.labelLarge)

            // each step
            Text("متوسط كل مرحلة", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("topics" to "sparkles", "script" to "scroll", "produce" to "film").forEach { (st, fx) ->
                    val c = Budget.stepCost(e, st); val m = Budget.stepMinutes(e, st)
                    StatTile(c?.let { usd(it) } ?: "–", Budget.stepName(st) + (m?.let { " · ${it.toInt()} د" } ?: ""), fx, Modifier.weight(1f))
                }
            }
            Text("المواضيع دفعة من 10 أفكار، فلا تُحسب في تكلفة الفيديو الواحد.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // each model
            val models = Budget.models(e)
            if (models.isNotEmpty()) {
                Text("متوسط كل نموذج للتشغيل", style = MaterialTheme.typography.titleSmall)
                models.forEach { (m, avg, n) ->
                    Row { Text(Budget.modelName(m), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${usd(avg)} · $n تشغيل", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            // dearest / cheapest video
            val v = Budget.videos(e)
            if (v.size >= 2) {
                Text("الفيديوهات", style = MaterialTheme.typography.titleSmall)
                listOf("الأغلى" to v.first(), "الأرخص" to v.last()).forEach { (lab, x) ->
                    Row { Text("$lab: ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(titles[x.first] ?: x.first, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(usd(x.second), style = MaterialTheme.typography.labelLarge) }
                }
            }
            Text("من سجل السحابة: ${e.size} تشغيلًا مدفوعًا منذ ${Instant.ofEpochMilli(e.first().at).atZone(Budget.MOROCCO).toLocalDate()}.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** «السحابة»: GitHub storage and speed, folded by default (they are not AI money). */
@Composable
fun CloudCard(vm: AppViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    val st = vm.storage
    Card(onClick = { open = !open }) { Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FxIcon("package", 22.dp); Spacer(Modifier.width(10.dp))
            Text("السحابة", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            st?.let { (mb, lim) -> Text("${mb.toInt()} من ${lim.toInt()} ميغابايت", style = MaterialTheme.typography.labelMedium,
                color = if (mb / lim > 0.8) Bad else MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.width(6.dp))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(open) { Column(Modifier.padding(top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(st?.let { (mb, lim) -> (mb / lim).toFloat() } ?: 0f, st?.let { "${it.first.toInt()}" } ?: "–", "ميغابايت من ${st?.second?.toInt() ?: 2000}",
                    if ((st?.let { it.first / it.second } ?: 0.0) > 0.8) Bad else Good)
                Spacer(Modifier.width(12.dp))
                Text("ملفات التشغيلات المؤقتة. تنظّفها مهمة التنظيف كل ليلة وبعد كل إنتاج، والدقائق مجانية لأن المستودع عام.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
            vm.cloudSpeed?.let { sp ->
                Text("سرعة السحابة (آخر 7 أيام)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Text(sp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        // 1.3.5: the last automatic cleanup in one line; «clean now» only appears when a place is nearly full
        vm.cleanup?.let { c ->
            val whenTxt = runCatching { java.time.OffsetDateTime.parse(c.checked).atZoneSameInstant(Budget.MOROCCO).let {
                val d = java.time.LocalDate.now(Budget.MOROCCO)
                (if (it.toLocalDate() == d) "اليوم" else if (it.toLocalDate() == d.minusDays(1)) "أمس" else it.toLocalDate().toString()) +
                    " %02d:%02d".format(it.hour, it.minute) } }.getOrDefault("")
            Text("آخر تنظيف تلقائي: $whenTxt" + (c.freedMb?.let { f -> if (c.items > 0) "، حُذف ${f.toInt()} ميغابايت (${c.items} ملف)" else "، لا شيء زائد" } ?: "") +
                if (c.fullest >= 0.8) " · قارب الحد" else " · الحالة سليمة",
                style = MaterialTheme.typography.bodySmall, color = if (c.fullest >= 0.8) Bad else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp))
            if (c.fullest >= 0.8) GhostButton("نظّف المؤقت الآن", fx = "check") { vm.cleanNow() }
        }
    } }
}

private fun ago(ms: Long): String = when { ms < 10_000 -> "الآن"; ms < 60_000 -> "قبل ${ms / 1000} ث"; else -> "قبل ${ms / 60_000} د" }
private fun hm(ms: Long) = Instant.ofEpochMilli(ms).atZone(Budget.MOROCCO).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
private fun stageName(s: Stage) = when (s) {
    Stage.TOPICS_RUNNING -> "المواضيع"; Stage.SCRIPT_RUNNING -> "السيناريو"; Stage.PREVIEWING -> "التجهيز"; Stage.PRODUCING -> "الإنتاج"; else -> "تشغيل"
}
