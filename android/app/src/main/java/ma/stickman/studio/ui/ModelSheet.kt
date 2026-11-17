package ma.stickman.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ma.stickman.studio.ui.theme.Bad
import ma.stickman.studio.ui.theme.Gold
import ma.stickman.studio.ui.theme.Good
import ma.stickman.studio.ui.theme.Sky
import ma.stickman.studio.ui.theme.Violet
import ma.stickman.studio.ui.theme.Warn
import java.util.Locale

// 1.2.9 «اختيار النموذج»: one bottom sheet in four coloured groups instead of one long dropdown. Every option
// shows what it really costs and takes (from model-bench.yml, then from this phone's own measured runs),
// how many videos today's Coarena balance buys with it, and whether Coarena has the model on right now.

enum class ModelGroup(val title: String, val why: String, val color: Color) {
    SMART("الأوضاع الذكية (موصى بها)", "يختار النظام النموذج المناسب لكل خطوة، وينتقل وحده إلى التالي إن أُوقف أحدها، بالرصيد نفسه.", Gold),
    COARENA("نموذج ثابت عبر Coarena", "نموذج واحد تختاره أنت لكل الخطوات، من رصيد Coarena اليومي. إن أُوقف يعمل الوضع المتوازن خلفه.", Sky),
    KEYS("مفاتيحك الأخرى (احتياط)", "تعمل بمفتاحك من Google AI Studio أو OpenRouter، خارج رصيد Coarena.", Violet),
    FREE("مجاني (آخر حل)", "بلا تكلفة، لكنه بطيء (20 إلى 30 دقيقة) وجودته أضعف. يستعمله النظام وحده حين ينفد كل شيء.", Color(0xFF8C8A84)),
}

/** One option. [perVideo] = full video in $ (null = not measured yet); [measured] = from a real run, not an estimate;
 *  [scriptMin] = minutes to write the script; [stars] = script quality 1..5 (null = not judged yet);
 *  [coarenaId] = the Coarena model behind it (for the live on/off dot). */
data class ModelChoice(val id: String, val group: ModelGroup, val title: String, val line: String,
                       val perVideo: Double?, val measured: Boolean, val scriptMin: Int?, val stars: Int?,
                       val badge: String? = null, val coarenaId: String? = null, val best: Boolean = false)

/** Numbers of 2026-11-14 (model-bench.yml on one real script, and the electricity video produced on GPT-6.1 Sol). */
val MODEL_CHOICES = listOf(
    ModelChoice("auto", ModelGroup.SMART, "متوازن", "Gemini 4 Argon يكتب السيناريو، وGPT-6.1 Terra لبقية الخطوات، وClaude أولًا حين يعود.",
        2.0, false, 10, 5, "الافتراضي", "gemini-4-argon", best = true),
    ModelChoice("economy", ModelGroup.SMART, "اقتصادي", "GPT-6.1 Terra لكل الخطوات، السيناريو أيضًا. أكثر فيديوهات بجودة أبسط.",
        0.7, false, 5, 3, "أكثر فيديوهات", "gpt-6-1-terra"),
    ModelChoice("coarena:gemini-4-argon", ModelGroup.COARENA, "Gemini 4 Argon", "الأغنى معلومات والأجمل دارجة في المقارنة.",
        2.6, false, 10, 5, "اختيارك في القراءة العمياء", "gemini-4-argon", best = true),
    ModelChoice("coarena:gpt-6-1-terra", ModelGroup.COARENA, "GPT-6.1 Terra", "اجتاز كل الفحوص من أول مرة، بنص أقصر وأبسط.",
        0.7, false, 5, 3, "الأرخص والأسرع", "gpt-6-1-terra"),
    ModelChoice("coarena:gpt-6-1-sol", ModelGroup.COARENA, "GPT-6.1 Sol", "جيد وسريع، وأغلى من Terra.",
        2.54, true, 7, 4, null, "gpt-6-1-sol"),
    ModelChoice("coarena:kimi-k3-1-pro", ModelGroup.COARENA, "Kimi K3.1 Pro", "لم يُجرَّب بعد على سيناريو كامل.",
        null, false, null, null, null, "kimi-k3-1-pro"),
    ModelChoice("coarena:claude-sonnet-5-5", ModelGroup.COARENA, "Claude Sonnet 5.5", "كان كاتب الاستوديو الأول (0.33$ للسيناريو).",
        0.6, false, 7, 5, null, "claude-sonnet-5-5"),
    ModelChoice("gemini", ModelGroup.KEYS, "Gemini Flash (مفتاحك)", "بمفتاح AI Studio المباشر. حصته المجانية تنفد يوميًا.",
        null, false, null, null),
    ModelChoice("free", ModelGroup.FREE, "النماذج المجانية", "نماذج OpenRouter المجانية: بطيئة، والسيناريو أضعف.",
        0.0, true, 25, 2),
)

fun modelChoice(id: String) = MODEL_CHOICES.firstOrNull { it.id == id }
    ?: if (id == "coarena") MODEL_CHOICES.first() else null

/** The script cost this phone measured for a Coarena model (mean of its last runs), and how many runs. */
private fun measuredScript(vm: AppViewModel, coarenaId: String?): Pair<Double, Int>? {
    coarenaId ?: return null
    val l = vm.local.timings().filter { it.ok && it.stage == "SCRIPT_RUNNING" && it.model == "coarena/$coarenaId" }
        .mapNotNull { it.costUsd }.takeLast(5)
    return if (l.isEmpty()) null else l.average() to l.size
}

/** The summary of the current choice, shown in Settings; tapping it opens the sheet. */
@Composable
fun ModelSummary(vm: AppViewModel, current: String, onOpen: () -> Unit) {
    val c = modelChoice(current)
    val g = c?.group ?: ModelGroup.KEYS
    Card(onClick = onOpen, accent = g.color) { Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(g.color)); Spacer(Modifier.width(8.dp))
            Text(c?.title ?: current.substringAfter('/'), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("غيّر", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.padding(top = 4.dp))
        Text(c?.line ?: "نموذج من OpenRouter بمفتاحك.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        c?.let { videosLine(vm, it, withCost = true)?.let { l -> Text(l, style = MaterialTheme.typography.bodySmall, color = g.color) } }
    } }
}

/** How many videos today's Coarena balance buys with this option; [withCost] adds the price (for the summary card,
 *  which has no price badge). */
private fun videosLine(vm: AppViewModel, c: ModelChoice, withCost: Boolean = false): String? {
    val per = c.perVideo ?: return null
    if (per <= 0.0) return if (withCost) "بلا تكلفة" else null
    val cost = if (withCost) "نحو %.2f$ للفيديو · ".format(Locale.US, per) else ""
    if (c.group == ModelGroup.KEYS) return cost.ifBlank { null }
    val left = vm.coarenaLeft ?: return cost.ifBlank { null }
    val n = (left / per).toInt()
    return cost + if (n >= 1) "يكفي رصيد اليوم (%.2f$) لـ %d فيديو".format(Locale.US, left, n)
        else "رصيد اليوم (%.2f$) لا يكفي فيديو كاملًا، ويتجدد عند منتصف الليل".format(Locale.US, left)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelSheet(vm: AppViewModel, current: String, pick: (String) -> Unit, dismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { vm.refreshCoarena(); vm.loadCoarenaModels() }
    var more by remember { mutableStateOf(false) }
    LaunchedEffect(more) { if (more && vm.models.isEmpty()) vm.loadModels() }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.background) {
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("اختر النموذج", style = MaterialTheme.typography.headlineSmall)
                Text("الأرقام من مقارنة حقيقية على سيناريو كامل، ثم من فيديوهاتك أنت. «تقديري» حتى تُقاس عندك.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                vm.coarenaLeft?.let { Text("رصيد Coarena المتبقي اليوم: %.2f$".format(Locale.US, it), style = MaterialTheme.typography.labelLarge,
                    color = Gold, modifier = Modifier.padding(top = 6.dp)) }
            }
            ModelGroup.entries.forEach { g ->
                item {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(g.color)); Spacer(Modifier.width(8.dp))
                        Text(g.title, style = MaterialTheme.typography.titleSmall, color = g.color, modifier = Modifier.weight(1f))
                        Help(g.title, g.why)
                    }
                }
                MODEL_CHOICES.filter { it.group == g }.forEach { c -> item { ChoiceCard(vm, c, c.id == current || (current == "coarena" && c.id == "auto")) { pick(c.id); dismiss() } } }
                if (g == ModelGroup.KEYS) item {
                    Text(if (more) "أخفِ نماذج OpenRouter المدفوعة" else "المزيد: نماذج OpenRouter المدفوعة", style = MaterialTheme.typography.labelLarge,
                        color = Violet, modifier = Modifier.clip(CircleShape).pressable { more = !more }.padding(8.dp))
                    if (more) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        vm.models.filterNot { it.free }.take(40).forEach { m ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .border(1.dp, if (m.id == current) Violet else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                .pressable { pick(m.id); dismiss() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(m.name.ifBlank { m.id }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("%.2f$ / %.2f$ للمليون".format(Locale.US, m.inPrice, m.outPrice), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (vm.models.isEmpty()) Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceCard(vm: AppViewModel, c: ModelChoice, selected: Boolean, onPick: () -> Unit) {
    val on = vm.coarenaOn
    // live state from Coarena's own model list: 1 on, -1 switched off, 0 unknown (list not read yet)
    val live = when { c.coarenaId == null -> null; on == null -> 0; c.coarenaId in on -> 1; else -> -1 }
    val off = live == -1
    val color = if (off) Color(0xFF8C8A84) else c.group.color
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape)
        .background(if (selected) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
        .border(if (selected) 2.dp else 1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant, shape)
        .pressable(onClick = onPick).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (c.best) { Text("⭐", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.width(6.dp)) }
            Text(c.title, style = MaterialTheme.typography.titleMedium, color = if (off) color else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            live?.let { StatusDot(it) ; Spacer(Modifier.width(4.dp))
                Text(when (it) { 1 -> "متاح الآن"; -1 -> "موقوف الآن في Coarena"; else -> "…" }, style = MaterialTheme.typography.labelSmall,
                    color = when (it) { 1 -> Good; -1 -> Bad; else -> MaterialTheme.colorScheme.onSurfaceVariant }) }
            if (selected) { Spacer(Modifier.width(8.dp)); Text("✓", color = color, style = MaterialTheme.typography.titleMedium) }
        }
        Spacer(Modifier.padding(top = 4.dp))
        Text(c.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(top = 8.dp))
        val m = measuredScript(vm, c.coarenaId)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            c.badge?.let { Pill(it, if (c.best) Warn else color) }
            Pill(when {
                c.perVideo == null -> "💰 لم يُقس بعد"
                c.perVideo == 0.0 -> "💰 مجاني"
                else -> "💰 %.2f$ للفيديو (%s)".format(Locale.US, c.perVideo, if (c.measured) "مقاس" else "تقديري")
            }, color)
            m?.let { (cost, n) -> Pill("✍️ السيناريو عندك %.2f$ (آخر %d)".format(Locale.US, cost, n), Good) }
            c.scriptMin?.let { Pill("⏱ السيناريو نحو $it د", color) }
            c.stars?.let { Pill("الجودة " + "★".repeat(it) + "☆".repeat(5 - it), color) }
        }
        videosLine(vm, c)?.takeIf { !off }?.let {
            Spacer(Modifier.padding(top = 6.dp)); Text(it, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}
