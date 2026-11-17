@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ma.stickman.studio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import ma.stickman.studio.data.Handoff
import ma.stickman.studio.data.ScriptInfo
import ma.stickman.studio.ui.theme.Bad
import ma.stickman.studio.ui.theme.Gold
import ma.stickman.studio.ui.theme.Good
import ma.stickman.studio.ui.theme.Sky
import ma.stickman.studio.ui.theme.Violet
import ma.stickman.studio.ui.theme.Warn

private fun isAudio(mime: String) = mime.startsWith("audio") || mime == "video/webm"

@Composable
fun Prepare(vm: AppViewModel, toast: (String) -> Unit) {
    val h = vm.handoff
    val ctx = LocalContext.current
    var extra by rememberSaveable { mutableStateOf("") }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { vm.addFiles(it) }
    // 1.3.8: the modern photo picker: tap both ChatGPT sheets (and any other picture) at once, then Done
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { vm.addFiles(it) }
    val list = rememberParallaxList()
    if (h == null) {
        LaunchedEffect(Unit) { vm.refresh() }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { SkeletonCard() } }
        return
    }
    LaunchedEffect(h.slug) { if (vm.script == null) vm.loadScript(h.slug) }
    val hasAudio = vm.files.any { isAudio(vm.describe(it).second) }
    val sheets = vm.files.count { vm.checks[it]?.let { c -> !c.audio && c.looksLikeSheet } == true }
    val step = when { !hasAudio -> 0; sheets < h.imagePrompts.size -> 1; else -> 2 }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Pipeline(2) }
        item {
            Text("السيناريو جاهز", style = MaterialTheme.typography.headlineSmall)
            Text(h.title, style = MaterialTheme.typography.titleMedium, color = Gold)
            if (h.checks.isNotBlank()) { Gap(4); Pill("✓ ${h.checks}", Good) }
        }
        item { Stepper(step, hasAudio, sheets, h.imagePrompts.size) }
        vm.script?.let { sc -> item { ScriptCard(sc) } }
        item {
            Card(accent = if (step == 0) Gold else null) { Column {
                SectionTitle("1. الصوت من Google AI Studio", fx = "mic", trailing = { Pill(h.voiceName, Sky) })
                Text("الزر ينسخ النص كاملًا ويفتح صفحة الصوت. هناك: اضغط مطوّلًا في خانة النص ← «لصق»، اختر الصوت ${h.voiceName}، ثم Run ونزّل الملف وشاركه مع هذا التطبيق.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(10)
                GoldButton("انسخ النص وافتح AI Studio", fx = "mic") {
                    copy(ctx, "نص الصوت", h.voiceText); toast("نُسخ نص الصوت كاملًا: الصقه، ثم اختر ${h.voiceName} واضغط Run")
                    openUrl(ctx, "https://aistudio.google.com/generate-speech")
                }
                Gap(8)
                CopyBlock("نص الصوت", h.voiceText, collapsed = true, onCopied = toast)
            } }
        }
        item {
            Card(accent = if (step == 1) Gold else null) { Column {
                SectionTitle("2. الصور من ChatGPT", fx = "picture", trailing = { Pill("${h.chatgptImages} صورة", Violet) })
                Text("الزر يضع الأمر جاهزًا في خانة الكتابة في تطبيق ChatGPT: اضغط «إرسال» فقط، ثم نزّل الصورة بزر التنزيل (لا لقطة شاشة) وشاركها مع هذا التطبيق.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(10)
                h.imagePrompts.forEachIndexed { i, p ->
                    val label = if (h.imagePrompts.size > 1) "أمر اللوحة ${i + 1}" else "أمر الصور"
                    GoldButton("انسخ $label وافتح ChatGPT", fx = "picture") {
                        copy(ctx, label, p)
                        if (sendText(ctx, "com.openai.chatgpt", p)) toast("$label جاهز في ChatGPT: اضغط «إرسال»")
                        else { toast("نُسخ $label. الصقه في ChatGPT"); openUrl(ctx, "https://chatgpt.com/") }
                    }
                    Gap(8)
                    CopyBlock(label, p, ltr = true, collapsed = true, onCopied = toast); Gap(8)
                }
                if (h.realPhotos.isNotEmpty()) {
                    Gap(6)
                    Text("صور حقيقية جلبها الذكاء الاصطناعي بنفسه:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(6)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        h.realPhotos.forEach { Pill(it, Good) }
                    }
                }
            } }
        }
        item {
            Card(accent = if (step == 2) Gold else null) { Column(Modifier.animateContentSize()) {
                SectionTitle("3. الملفات", fx = "package")
                if (vm.files.isEmpty()) Text("شارك الصوت والصورة من AI Studio وChatGPT بزر «مشاركة»، أو اخترهما من هنا.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                vm.files.forEach { u -> FileRow(vm, u, h) }
                Gap(10)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("اختر الصوت", fx = "mic") { pickAudio.launch("audio/*") }
                    GhostButton("اختر الصور", fx = "picture") { pickImages.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                }
            } }
        }
        item {
            OutlinedTextField(extra, { extra = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظة للإنتاج (اختياري)") },
                shape = RoundedCornerShape(16.dp), maxLines = 3)
        }
        item { Checklist(vm, h, vm.script) }
        item {
            val st by vm.settings.collectAsState()
            GoldButton(if (!hasAudio) "أضف ملف الصوت أولًا" else if (st.previewFirst) "تم — جهّز المعاينة" else "تم — أنتج الفيديو", fx = "rocket",
                enabled = hasAudio, loading = vm.busy != null) { vm.produce(extra) }
            if (h.notes.isNotBlank()) { Gap(10); Text("ملاحظة: ${h.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Gap(8)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                GhostButton("موضوع آخر", fx = "magnifier") { vm.reset() }
            }
            Gap(30)
        }
    }
}

/** 1 voice · 2 pictures · 3 send, with a tick on each step that is done. */
@Composable
private fun Stepper(current: Int, hasAudio: Boolean, sheets: Int, wantSheets: Int) {
    val items = listOf("الصوت" to hasAudio, "الصور" to (sheets >= wantSheets), "الإرسال" to false)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { i, (l, done) ->
            val now = i == current
            Box(Modifier.size(30.dp).clip(CircleShape).background(when { done -> Good; now -> Gold; else -> MaterialTheme.colorScheme.surfaceVariant }),
                contentAlignment = Alignment.Center) {
                Text(if (done) "✓" else "${i + 1}", color = if (done || now) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(6.dp))
            Text(l, style = MaterialTheme.typography.labelLarge, color = if (now) Gold else MaterialTheme.colorScheme.onSurface)
            if (i < items.lastIndex) Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(2.dp)
                .background(if (done) Good else MaterialTheme.colorScheme.outline))
        }
    }
}

/** Parts of the story on a time line (hook, promise, chapters, open loop, climax, ending) + the scene reader. */
@Composable
private fun ScriptCard(sc: ScriptInfo) {
    var read by rememberSaveable { mutableStateOf(false) }
    val parts = listOf(0.03f to Bad, 0.07f to Warn, 0.15f to Sky, 0.35f to Violet, 0.28f to Gold, 0.12f to Good)
    val names = listOf("الخطّاف", "الوعد", "الخلفية", "الصراع", "الذروة", "الخاتمة")
    Card { Column(Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))) {
        SectionTitle("السيناريو", fx = "scroll")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("%d:%02d".format(sc.estSeconds / 60, sc.estSeconds % 60), "مدة تقريبية", "stopwatch", Modifier.weight(1f))
            StatTile("${sc.scenes.size}", "مشهد", "film", Modifier.weight(1f))
            StatTile("${sc.shots}", "لقطة", "eyes", Modifier.weight(1f))
            StatTile("${sc.images}", "صورة", "picture", Modifier.weight(1f))
        }
        Gap(12)
        Canvas(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
            var x = 0f
            parts.forEach { (f, c) -> drawRect(c, Offset(size.width - x - f * size.width, 0f), Size(f * size.width, size.height)); x += f * size.width }
        }
        Gap(6)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            names.forEachIndexed { i, n -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(parts[i].second)); Spacer(Modifier.width(4.dp))
                Text(n, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
        Gap(10)
        Text(if (read) "أخفِ المشاهد" else "اقرأ السيناريو مشهدًا مشهدًا", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(CircleShape).pressable { read = !read }.padding(vertical = 6.dp, horizontal = 4.dp))
        if (read) sc.scenes.forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 6.dp).stagger(i)) {
                Text("%d:%02d".format(s.startS.toInt() / 60, s.startS.toInt() % 60), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(38.dp))
                Column(Modifier.weight(1f)) {
                    Text(highlight(s.narration, s.keywords), style = MaterialTheme.typography.bodyLarge)
                    Text("${s.shots} لقطة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } }
}

/** The keywords of the scene in bold gold, as they will look in the video. */
private fun highlight(text: String, keys: List<String>) = buildAnnotatedString {
    val norm = { s: String -> s.replace(Regex("[\\p{P}\\u064B-\\u0652]"), "") }
    val k = keys.map(norm).filter { it.isNotBlank() }.toSet()
    text.split(" ").forEachIndexed { i, w ->
        if (i > 0) append(" ")
        if (norm(w) in k) withStyle(SpanStyle(color = Gold, fontWeight = FontWeight.Bold)) { append(w) } else append(w)
    }
}

@Composable
private fun FileRow(vm: AppViewModel, u: Uri, h: Handoff) {
    val (name, mime) = remember(u) { vm.describe(u) }
    val audio = isAudio(mime)
    val c = vm.checks[u]
    val ctx = LocalContext.current
    AnimatedVisibility(true, enter = scaleIn(spring(stiffness = Spring.StiffnessMedium)) + fadeIn()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 12.dp, top = 6.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FxIcon(if (audio) "mic" else "picture", 24.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    c?.let {
                        val info = when {
                            it.audio -> "صوت"
                            it.looksLikeScreenshot -> "${it.width}×${it.height} · تبدو لقطة شاشة"
                            it.width != null -> "${it.width}×${it.height} · لوحة"
                            else -> ""
                        }
                        if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.labelSmall,
                            color = if (it.looksLikeScreenshot) Bad else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { vm.removeFile(u) }) { Icon(Icons.Rounded.Close, "احذف $name") }
            }
            if (audio) Box(Modifier.padding(end = 12.dp, top = 4.dp)) { AudioPlayer(u, c?.seconds) }
            else Box(Modifier.padding(end = 12.dp, top = 6.dp)) { SheetPreview(u, h.chatgptImages) { u.path?.let { viewImage(ctx, java.io.File(it)) } } }
        }
    }
}

/** The ChatGPT sheet with its expected grid drawn over it (3 columns, numbered): wrong count = visible at once. */
@Composable
private fun SheetPreview(u: Uri, n: Int, onClick: () -> Unit) {
    val bmp = rememberFileBitmap(u.path, 400) ?: return
    Box(Modifier.fillMaxWidth(0.7f).clip(RoundedCornerShape(12.dp)).pressable(onClick = onClick)) {
        Image(bmp, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        if (n > 0) {
            val rows = (n + 2) / 3
            val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
            Canvas(Modifier.matchParentSize()) {
                val cw = size.width / 3; val ch = size.height / rows
                for (i in 0 until n) {
                    val x = (i % 3) * cw; val y = (i / 3) * ch     // left to right, top to bottom, as the prompt says
                    drawRect(Gold.copy(alpha = 0.8f), Offset(x + 3, y + 3), Size(cw - 6, ch - 6), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                    drawCircle(Gold, 22f, Offset(x + 26, y + 26))
                    val t = textMeasurer.measure("${i + 1}", androidx.compose.ui.text.TextStyle(color = Color.Black, fontWeight = FontWeight.Bold))
                    drawText(t, topLeft = Offset(x + 26 - t.size.width / 2f, y + 26 - t.size.height / 2f))
                }
            }
        }
    }
}

@Composable
private fun Checklist(vm: AppViewModel, h: Handoff, sc: ScriptInfo?) {
    if (vm.files.isEmpty()) return
    val cs = vm.files.mapNotNull { vm.checks[it] }
    val audio = cs.count { it.audio }
    val sheets = cs.count { !it.audio && it.looksLikeSheet }
    val shots = cs.count { !it.audio && it.looksLikeScreenshot }
    val wantSheets = h.imagePrompts.size
    val secs = cs.lastOrNull { it.audio }?.seconds
    val est = sc?.estSeconds
    Card { Column {
        SectionTitle("قبل الإرسال", fx = "check")
        CheckLine(audio >= 1, when {
            audio == 0 -> "ملف الصوت ناقص"
            audio > 1 -> "$audio ملفات صوت: سيُستعمل آخرها"
            else -> "ملف الصوت" + (secs?.let { " (%d:%02d)".format(it / 60, it % 60) } ?: "")
        }, warn = audio > 1)
        // the voice must say the whole script: much shorter = a paragraph is probably missing
        if (secs != null && est != null && est > 0) {
            val r = secs.toFloat() / est
            CheckLine(r in 0.6f..1.9f, when {
                r < 0.6f -> "الصوت أقصر بكثير من المتوقع (نحو %d:%02d): ربما نقصت فقرة".format(est / 60, est % 60)
                r > 1.9f -> "الصوت أطول بكثير من المتوقع: تأكد أنه بلا تكرار"
                else -> "طول الصوت مناسب للسيناريو"
            }, warn = r < 0.75f || r > 1.6f)
        }
        if (wantSheets > 0) CheckLine(sheets >= wantSheets,
            if (sheets >= wantSheets) (if (wantSheets > 1) "لوحتا ChatGPT" else "لوحة ChatGPT") + " · ${h.chatgptImages} صورة متوقعة"
            else "لوحة ChatGPT ناقصة ($sheets من $wantSheets): ستُستعمل الصور الاحتياطية", warn = sheets < wantSheets)
        if (shots > 0) CheckLine(false, "$shots صورة تبدو لقطة شاشة: نزّل الصورة من ChatGPT بزر التنزيل")
    } }
}
