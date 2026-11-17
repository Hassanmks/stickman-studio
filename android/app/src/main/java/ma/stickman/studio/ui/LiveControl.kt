package ma.stickman.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ma.stickman.studio.data.Stage
import ma.stickman.studio.ui.theme.Bad
import ma.stickman.studio.ui.theme.Gold
import ma.stickman.studio.ui.theme.Good
import ma.stickman.studio.ui.theme.Warn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 1.3.0 «بدّل وهو يعمل، وأوقف مؤقتًا»: the model chip of the running screen opens a short sheet of the four
// main choices; the user says whether the change is for this step only or for every next step too. The pause
// button stops after the agent's current turn (a checkpoint) and the paused screen resumes it now, or by
// itself after Coarena's daily reset.

/** Four quick choices; the full sheet has the rest. */
private val QUICK = listOf("auto", "economy", "coarena:gemini-4-argon", "coarena:gpt-6-1-sol")

/** The chip «Coarena · GPT-6.1 Terra ⌄»: tap to switch; shows a pending switch until the agent confirms it. */
@Composable
fun ModelChip(vm: AppViewModel, label: String, color: Color) {
    var open by remember { mutableStateOf(false) }
    val pending = vm.switchTo
    val text = if (pending != null) "⏳ يبدّل إلى " + (modelChoice(pending)?.title ?: pending) + "…" else "$label  ⌄"
    Box(Modifier.clip(RoundedCornerShape(50)).pressable(label = "بدّل النموذج") { open = true }) {
        LogChip(text, if (pending != null) Warn else color)
    }
    if (open) QuickSwitchSheet(vm) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSwitchSheet(vm: AppViewModel, dismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val live = vm.live
    val s by vm.settings.collectAsState()
    var picked by remember { mutableStateOf<String?>(null) }
    var full by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadCoarenaModels(); vm.refreshCoarena() }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.background) {
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("بدّل النموذج وهو يعمل", style = MaterialTheme.typography.headlineSmall)
                val now = live?.model?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("coarena")) "Coarena · " + prettyModel(it) else prettyModel(it) }
                Text("الآن يعمل: " + (now ?: "…") + (live?.let { " · كلّف %.2f$".format(Locale.US, it.costUsd) } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = Gold)
                Text(if (vm.aiNow()) "يكمل من النقطة نفسها بالمحادثة نفسها. النداء الأول بعد التبديل أغلى قليلًا (يُبنى تخزين جديد)."
                     else "لا ذكاء اصطناعي في هذه المرحلة الآن: يُطبَّق اختيارك على خطوة الذكاء القادمة.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            QUICK.mapNotNull { modelChoice(it) }.forEach { c ->
                item {
                    val on = vm.coarenaOn
                    val live2 = when { c.coarenaId == null -> null; on == null -> 0; c.coarenaId in on -> 1; else -> -1 }
                    val sel = (live?.choice?.ifBlank { null } ?: s.model) == c.id
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (sel) c.group.color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
                        .border(if (sel) 2.dp else 1.dp, if (sel) c.group.color else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .pressable(enabled = live2 != -1) { picked = c.id }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(c.group.color)); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text((if (c.best) "⭐ " else "") + c.title, style = MaterialTheme.typography.titleSmall)
                            Text(c.perVideo?.let { "نحو %.2f$ للفيديو · السيناريو نحو %s د".format(Locale.US, it, c.scriptMin ?: "?") } ?: "لم يُقس بعد",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        live2?.let { StatusDot(it) }
                        if (sel) { Spacer(Modifier.width(8.dp)); Text("✓", color = c.group.color) }
                    }
                }
            }
            item {
                Text("كل النماذج…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(CircleShape).pressable { full = true }.padding(8.dp))
            }
        }
    }
    if (full) ModelSheet(vm, live?.choice?.ifBlank { null } ?: s.model, { m -> picked = m }) { full = false }
    picked?.let { m ->
        val c = modelChoice(m)
        AlertDialog(onDismissRequest = { picked = null },
            title = { Text("التبديل إلى " + (c?.title ?: m.substringAfter('/'))) },
            text = { Text((if (c?.group == ModelGroup.FREE) "تنبيه: النماذج المجانية أبطأ بكثير وجودتها أضعف.\n\n" else "") +
                "لهذه الخطوة فقط، أم لكل الخطوات القادمة أيضًا (المواضيع والسيناريو والتجهيز والإنهاء)؟") },
            confirmButton = { TextButton({ vm.switchModel(m, forAll = true); picked = null; dismiss() }) { Text("ولكل الخطوات القادمة") } },
            dismissButton = { TextButton({ vm.switchModel(m, forAll = false); picked = null; dismiss() }) { Text("لهذه الخطوة فقط") } })
    }
}

/** Dollars per model inside this step, e.g. «Terra 0.05$ + Argon 0.40$» (only when more than one worked). */
fun byModelLine(m: Map<String, Double>): String? =
    if (m.size < 2) null else m.entries.joinToString(" + ") { (k, v) -> prettyModel(k) + " %.2f$".format(Locale.US, v) }

/** Stage.PAUSED: what stopped, where, what it cost, and how to continue. */
@Composable
fun PausedView(vm: AppViewModel) {
    val job by vm.job.collectAsState()
    val s by vm.settings.collectAsState()
    var pick by remember { mutableStateOf(false) }
    val at = vm.timingsVersion.let { vm.resumeAt() }
    val stepName = when (job.failedFrom) {
        Stage.TOPICS_RUNNING -> "المواضيع"; Stage.SCRIPT_RUNNING -> "السيناريو"; Stage.PREVIEWING -> "التجهيز والمعاينة"; else -> "الإنتاج"
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(accent = Warn) { Column {
                SectionTitle("⏸ متوقف مؤقتًا", fx = "hourglass")
                Text(stepName + (job.title.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""), style = MaterialTheme.typography.titleMedium, color = Gold)
                Gap(6)
                Text(job.error.ifBlank { "توقّف مؤقتًا" }, style = MaterialTheme.typography.bodyMedium)
                Gap(6)
                Text("العمل المنجز محفوظ: المحادثة مع النموذج، والملفات، والتكلفة حتى الآن. الاستئناف يكمل من الخطوة نفسها، ولا يستهلك شيئًا أثناء التوقف. نقطة الاستئناف صالحة 4 أيام.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                vm.coarenaLeft?.let { Gap(6); Text("رصيد Coarena المتبقي اليوم: %.2f$".format(Locale.US, it), style = MaterialTheme.typography.labelLarge, color = Gold) }
            } }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GoldButton("استأنف الآن", Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow) { vm.resumeRun() }
                GhostButton("استأنف بنموذج آخر…", Modifier.fillMaxWidth()) { pick = true }
                if (at > System.currentTimeMillis()) {
                    val t = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))
                    Text("سيستأنف وحده الساعة $t بعد تجدد الرصيد.", style = MaterialTheme.typography.bodyMedium, color = Good)
                    GhostButton("ألغِ الاستئناف التلقائي", Modifier.fillMaxWidth()) { vm.cancelScheduledResume() }
                } else GhostButton("استأنف عند تجدد الرصيد (منتصف الليل)", Modifier.fillMaxWidth(), icon = Icons.Rounded.Schedule) { vm.resumeAtReset() }
                GhostButton("تخلَّ عن هذه الخطوة", Modifier.fillMaxWidth(), icon = Icons.Rounded.Close) { vm.abandonPaused() }
            }
        }
    }
    if (pick) ModelSheet(vm, s.model, { m -> vm.resumeRun(m) }) { pick = false }
}

/** The pause button next to «إلغاء». */
@Composable
fun PauseButton(vm: AppViewModel, modifier: Modifier = Modifier) {
    var ask by remember { mutableStateOf(false) }
    GhostButton(if (vm.pauseRequested) "يتوقف…" else "إيقاف مؤقت", modifier, icon = Icons.Rounded.Schedule, enabled = !vm.pauseRequested) { ask = true }
    if (ask) AlertDialog(onDismissRequest = { ask = false }, title = { Text("إيقاف مؤقت؟") },
        text = { Text(if (vm.aiNow()) "يكمل الذكاء الاصطناعي خطوته الحالية ثم يحفظ كل شيء ويتوقف. تستأنف متى شئت من النقطة نفسها، دون أن يضيع ما أُنجز."
                      else "يتوقف التشغيل الآن، ويبقى ما اكتمل محفوظًا. الاستئناف يكمل الباقي.") },
        confirmButton = { TextButton({ ask = false; vm.pauseRun() }) { Text("أوقف مؤقتًا") } },
        dismissButton = { TextButton({ ask = false }) { Text("تابع العمل") } })
}
