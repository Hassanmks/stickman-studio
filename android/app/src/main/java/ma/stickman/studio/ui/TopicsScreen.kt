@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ma.stickman.studio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ma.stickman.studio.data.LogEntry
import ma.stickman.studio.data.Topic
import ma.stickman.studio.ui.theme.Bad
import ma.stickman.studio.ui.theme.Gold
import ma.stickman.studio.ui.theme.Good
import ma.stickman.studio.ui.theme.LocalMotion
import ma.stickman.studio.ui.theme.Sky
import ma.stickman.studio.ui.theme.Violet
import ma.stickman.studio.ui.theme.Warn

private fun axisOf(a: String): Triple<String, Color, String> = when (a) {
    "history" -> Triple("تاريخ وسياسة", Gold, "scroll"); "event" -> Triple("حدث قريب", Sky, "fire"); else -> Triple("محور آخر", Violet, "bulb")
}

/** A past video that looks like this topic: from the agent, or by shared words with videos/LOG.md. */
private fun similar(t: Topic, log: List<LogEntry>): String {
    if (t.similarTo.isNotBlank()) return t.similarTo
    fun words(s: String) = s.split(Regex("[\\s،؟?!.«»:]+")).filter { it.length > 3 }.toSet()
    val w = words(t.title) + words(t.hook)
    return log.firstOrNull { e -> (words(e.title) intersect w).size >= 2 }?.title.orEmpty()
}

@Composable
fun Topics(vm: AppViewModel) {
    val t = vm.topics
    val s by vm.settings.collectAsState()
    val saved by vm.local.saved.collectAsState()
    val hidden by vm.local.hidden.collectAsState()
    var chosen by remember { mutableStateOf<Topic?>(null) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    val list = rememberParallaxList()
    if (t == null) {
        LaunchedEffect(Unit) { vm.refresh() }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(4) { SkeletonCard() } }
        return
    }
    val shown = when (filter) {
        "saved" -> saved
        "all" -> t.topics
        else -> t.topics.filter { it.axis == filter }
    }.filter { showHidden || filter == "saved" || it.slug !in hidden }
    val hiddenHere = t.topics.count { it.slug in hidden }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Pipeline(0)
            Gap(10)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("اختر موضوعًا", style = MaterialTheme.typography.headlineSmall)
                    Text("مقترحات ${t.date} · اسحب البطاقة للحفظ أو للإخفاء، واضغطها لترى تفاصيلها", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("الكل (${t.topics.size})", filter == "all") { filter = "all" } }
                item { Chip("تاريخ وسياسة", filter == "history", Gold, "scroll") { filter = "history" } }
                item { Chip("حدث قريب", filter == "event", Sky, "fire") { filter = "event" } }
                item { Chip("محاور أخرى", filter == "other", Violet, "bulb") { filter = "other" } }
                item { Chip("المحفوظة (${saved.size})", filter == "saved", Good, "bookmark") { filter = "saved" } }
            }
        }
        if (hiddenHere > 0 && filter != "saved") item {
            Text(if (showHidden) "أخفِ المخفية" else "أظهر المخفية ($hiddenHere)", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(CircleShape).pressable { showHidden = !showHidden }.padding(8.dp))
        }
        if (shown.isEmpty()) item {
            EmptyState("bookmark", if (filter == "saved") "لا مواضيع محفوظة بعد. اسحب أي بطاقة لتحفظها." else "لا مواضيع هنا.")
        }
        itemsIndexed(shown, key = { _, x -> filter + x.slug }) { i, x ->
            Box(Modifier.stagger(i)) {
                SwipeCard(x, saved.any { it.slug == x.slug }, x.slug in hidden, similar(x, vm.log),
                    onSave = { vm.toggleSaved(x) }, onHide = { vm.hideTopic(x) }, onChoose = { chosen = x },
                    fromSaved = filter == "saved" && t.topics.none { it.slug == x.slug }, onAskSaved = { vm.useSaved(x) })
            }
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (t.notes.isNotBlank()) {
                    Text("ملاحظة: ${t.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Gap(10)
                }
                GhostButton("مواضيع أخرى (المحفوظة تبقى)", icon = Icons.Rounded.Refresh) { vm.newTopics("") }
            }
        }
    }
    chosen?.let { topic -> ChooseSheet(topic, s.voice, onDismiss = { chosen = null }) { voice, extra -> chosen = null; vm.chooseTopic(topic, voice, extra) } }
}

/** Swipe one way to save for later, the other way to hide; tap to flip the card and see the details. */
@Composable
private fun SwipeCard(x: Topic, isSaved: Boolean, isHidden: Boolean, similarTo: String, onSave: () -> Unit, onHide: () -> Unit,
                      onChoose: () -> Unit, fromSaved: Boolean, onAskSaved: () -> Unit) {
    val fb = LocalFeedback.current
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> { fb.success(); onSave(); false }
            SwipeToDismissBoxValue.EndToStart -> { fb.tick(); onHide(); false }
            else -> false
        }
    }, positionalThreshold = { it * 0.35f })
    SwipeToDismissBox(state, backgroundContent = {
        if (state.dismissDirection == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox   // nothing behind a card at rest
        val toSave = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
        val c = if (toSave) Good else Bad
        Row(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(c.copy(alpha = 0.18f)).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (toSave) Arrangement.Start else Arrangement.End) {
            FxIcon(if (toSave) "bookmark" else "wastebasket", 36.dp); Spacer(Modifier.width(8.dp))
            Text(if (toSave) (if (isSaved) "أزل من المحفوظات" else "احفظ لوقت لاحق") else "أخفِ", color = c, style = MaterialTheme.typography.titleSmall)
        }
    }) { FlipTopic(x, isSaved, isHidden, similarTo, onChoose, onSave, fromSaved, onAskSaved) }
}

@Composable
private fun FlipTopic(x: Topic, isSaved: Boolean, isHidden: Boolean, similarTo: String, onChoose: () -> Unit, onSave: () -> Unit,
                      fromSaved: Boolean, onAskSaved: () -> Unit) {
    var back by rememberSaveable(x.slug) { mutableStateOf(false) }
    val motion = LocalMotion.current
    val rot by animateFloatAsState(if (back) 180f else 0f, if (motion == 0f) androidx.compose.animation.core.tween(0) else spring(dampingRatio = 0.7f, stiffness = 260f), label = "flip")
    val (label, color, fx) = axisOf(x.axis)
    Box(Modifier.graphicsLayer { rotationY = rot; cameraDistance = 16f * density; alpha = if (isHidden) 0.55f else 1f }) {
        Card(onClick = { back = !back }, tilt = !back, accent = if (isSaved) Good else null) {
            if (rot <= 90f) Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Text("${x.n}", color = color, style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.width(8.dp)); FxIcon(fx, 24.dp); Spacer(Modifier.width(4.dp)); Pill(label, color)
                    Spacer(Modifier.weight(1f))
                    if (isSaved) FxIcon("bookmark", 22.dp)
                }
                Gap(10)
                Text(x.title, style = MaterialTheme.typography.titleLarge)
                Gap(6)
                Text("«${x.hook}»", style = MaterialTheme.typography.bodyLarge, color = Gold)
                Gap(10)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (x.hookScore > 0) Pill("الخطّاف " + "★".repeat(x.hookScore) + "☆".repeat(5 - x.hookScore),
                        when { x.hookScore >= 4 -> Good; x.hookScore == 3 -> Warn; else -> Bad })
                    Pill(if (x.photosOk) "✓ صور حقيقية متوفرة" else "صور حقيقية قليلة", if (x.photosOk) Good else Warn)
                    if (x.sensitive) Pill("حساس: اقرأ التفاصيل", Bad) else Pill("بلا حساسية", Good)
                    if (similarTo.isNotBlank()) Pill("قريب من: ${similarTo.take(30)}", Warn)
                    if (x.parts > 1) Pill("سلسلة من ${x.parts} أجزاء", Gold)
                }
            } else Column(Modifier.graphicsLayer { rotationY = 180f }) {
                Text(x.title, style = MaterialTheme.typography.titleMedium, color = Gold)
                Gap(8)
                Detail("fire", "لماذا الآن", x.whyNow)
                Detail("picture", "الصور الحقيقية", x.realPhotos)
                Detail("shield", "الحساسية", x.sensitivity)
                if (similarTo.isNotBlank()) Detail("warning", "قريب من فيديو سابق", similarTo)
                if (x.parts > 1) Detail("books", "سلسلة من ${x.parts} أجزاء", x.partTitles.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n")
                    .ifBlank { "كل جزء قصة كاملة. يبدأ هذا الاختيار بالجزء 1، ثم تطلب الجزء التالي من شاشة الفيديو الجاهز." })
                Gap(12)
                if (fromSaved) GoldButton("اطلب هذا الموضوع", fx = "rocket") { onAskSaved() }
                else GoldButton("اكتب السيناريو", fx = "scroll") { onChoose() }
                Gap(4)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(if (isSaved) "أزل من المحفوظات" else "احفظ", fx = "bookmark") { onSave() }
                    GhostButton("اقلب", icon = Icons.Rounded.Refresh) { back = false }
                }
            }
        }
    }
}

@Composable
private fun Detail(fx: String, k: String, v: String) {
    if (v.isBlank()) return
    Row(Modifier.padding(vertical = 4.dp)) {
        FxIcon(fx, 22.dp); Spacer(Modifier.width(8.dp))
        Column {
            Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(v, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ChooseSheet(t: Topic, defaultVoice: String, onDismiss: () -> Unit, go: (String, String) -> Unit) {
    var female by rememberSaveable { mutableStateOf(defaultVoice == "female") }
    var extra by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(t.title, style = MaterialTheme.typography.titleLarge)
            Text("«${t.hook}»", color = Gold, style = MaterialTheme.typography.bodyMedium)
            Gap(16)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                FxIcon("mic", 34.dp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (female) "صوت أنثوي (Kore أو Aoede)" else "Enzo — الصوت الرجالي الدائم", style = MaterialTheme.typography.titleSmall)
                    Text("الصوت الأنثوي فقط حين تريده لهذا الفيديو", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(female, { female = it })
            }
            Gap(14)
            OutlinedTextField(extra, { extra = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظة للسيناريو (اختياري)") },
                placeholder = { Text("مثال: ركّز على الأرقام، واذكر المصادر المغربية") }, shape = RoundedCornerShape(16.dp), maxLines = 3)
            Gap(18)
            GoldButton("اكتب السيناريو", fx = "scroll") { go(if (female) "female" else "male", extra) }
        }
    }
}
