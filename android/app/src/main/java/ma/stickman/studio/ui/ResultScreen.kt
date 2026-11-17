@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ma.stickman.studio.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ma.stickman.studio.ui.theme.Bad
import ma.stickman.studio.ui.theme.Gold
import ma.stickman.studio.ui.theme.Good
import ma.stickman.studio.ui.theme.Sky
import ma.stickman.studio.ui.theme.Violet
import ma.stickman.studio.work.Reminder
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

@Composable
fun ResultView(vm: AppViewModel, toast: (String) -> Unit) {
    val job by vm.job.collectAsState()
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current
    val f = vm.video
    val r = vm.result
    var confirm by remember { mutableStateOf(false) }
    var change by rememberSaveable { mutableStateOf("") }
    var overlay by rememberSaveable { mutableStateOf(false) }
    val list = rememberParallaxList()
    val st = f?.let { rememberStudioPlayer(it) }
    var full by remember { mutableStateOf(false) }
    val poster = f?.let { java.io.File(it.parentFile, it.nameWithoutExtension + ".jpg") }
    val prev = f?.let { java.io.File(it.parentFile, it.nameWithoutExtension + ".prev.mp4").takeIf { p -> p.exists() } }
    val timeline = r?.timeline ?: emptyList()
    val tiktok = if (overlay) TikTokLayer("قناتك", r?.seo?.description?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: (r?.title ?: "")) else null

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FxIcon("party", 44.dp, Modifier.tilt3d(1.5f, false)); Spacer(Modifier.width(10.dp))
                    Column {
                        Text("الفيديو جاهز", style = MaterialTheme.typography.headlineSmall)
                        Text(r?.title ?: job.title.ifBlank { job.slug }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                if (f != null && st != null) PhoneFrame(false) {
                    StudioVideo(st, poster, timeline, tiktok, attached = !full, modifier = Modifier.fillMaxSize(), onFullscreen = { full = true })
                } else VideoBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (vm.busy != null) Busy(vm.busy!!, vm.progress)
                        else GoldButton("نزّل الفيديو", Modifier.padding(24.dp), icon = Icons.Rounded.CloudDownload) { vm.fetchVideo(job.runId, job.slug) }
                    }
                }
            }
            if (f != null && st != null) item {
                PlayerTools(st, timeline, prev) { line -> change = line; toast("كُتب الوقت والمشهد في خانة «تعديل قبل النشر» بالأسفل") }
                Gap(6)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("معاينة واجهة تيك توك فوق الفيديو (بوصفك الحقيقي، لترى ما قد يختفي تحت الأزرار)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Switch(overlay, { overlay = it })
                }
            }
            if (f != null) item {
                GoldButton("انشر على تيك توك", fx = "phone") {
                    val caption = r?.seo?.description.orEmpty()
                    shareTikTok(ctx, f, caption)
                    if (caption.isNotBlank()) toast("الوصف والهاشتاغات منسوخة: في تيك توك اضغط مطوّلًا على خانة الوصف ثم «لصق»")
                }
                Gap(8)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("مشاركة", Modifier.weight(1f), icon = Icons.Rounded.Share) { share(ctx, f) }
                    GhostButton("حفظ في المعرض", Modifier.weight(1f), icon = Icons.Rounded.SaveAlt) { vm.saveToGallery() }
                }
                // 1.3.8: the same video with another opening (test which hook keeps viewers), and the subtitles file
                val alt = java.io.File(f.parentFile, f.nameWithoutExtension + ".hook2.mp4")
                val srt = java.io.File(f.parentFile, f.nameWithoutExtension + ".srt")
                if (alt.exists() || srt.exists()) {
                    Gap(8)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (alt.exists()) GhostButton("خطّاف بديل", Modifier.weight(1f), icon = Icons.Rounded.Share) { share(ctx, alt) }
                        if (srt.exists()) GhostButton("ملف الترجمة", Modifier.weight(1f), icon = Icons.Rounded.Share) { shareFile(ctx, srt, "application/x-subrip") }
                    }
                }
            }
            r?.let { res ->
                item { ResultCard(res) }
                item { JourneyLine(vm) }
                item { ProjectTime(vm, job.slug) }
                item { BestTime(res.seo.bestTime, s.remindPost, res.title) { v -> vm.update { it.copy(remindPost = v) } } }
                if (f != null) item { CoverPicker(vm) }
                item { Text("حزمة النشر", style = MaterialTheme.typography.titleLarge) }
                item { CopyBlock("الوصف", res.seo.description, onCopied = toast) }
                item { CopyBlock("التعليق المثبّت", res.seo.pinned, onCopied = toast) }
                item { CopyBlock("نص الغلاف", res.seo.cover, onCopied = toast) }
                item { CopyBlock("كلمات البحث", res.seo.keywords, collapsed = true, onCopied = toast) }
                if (res.notes.isNotBlank()) item {
                    Text("ملاحظة: ${res.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Card { Column {
                    SectionTitle("تعديل قبل النشر", icon = Icons.Rounded.Edit)
                    Text("بالصوت نفسه: الصور، اللقطات، الإيقاع، الكلمات المفتاحية، الألوان. كلام جديد يحتاج صوتًا جديدًا.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(8)
                    OutlinedTextField(change, { change = it }, Modifier.fillMaxWidth(), label = { Text("ما الذي تريد تغييره؟") },
                        placeholder = { Text("مثال: أبطئ اللقطات الأولى، وغيّر صورة اللقطة 20") }, shape = RoundedCornerShape(16.dp), maxLines = 4)
                    Gap(8)
                    GhostButton("عدّل وأعد التصيير", icon = Icons.Rounded.Edit, enabled = change.isNotBlank() && vm.busy == null) { vm.revise(change); change = "" }
                } }
            }
            r?.series?.takeIf { it.hasNext }?.let { sr -> item {
                Card { Column {
                    SectionTitle("الجزء ${sr.part} من ${sr.parts}", fx = "books")
                    Text("«${sr.title}». التالي: ${sr.nextTitle}", style = MaterialTheme.typography.bodyMedium)
                    Text("يكتب السيناريو مبنيًّا على هذا الجزء، ويبدأ بتذكير قصير.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(8)
                    GoldButton("نشرته — نظّف واكتب الجزء ${sr.part + 1}", fx = "scroll", enabled = vm.busy == null) { vm.nextPart(true) }
                    Gap(6)
                    GhostButton("اكتب الجزء ${sr.part + 1} دون تنظيف", fx = "sparkles", enabled = vm.busy == null) { vm.nextPart(false) }
                } }
            } }
            item {
                GoldButton("نشرته — نظّف وابدأ فيديو جديدًا", fx = "check") { confirm = true }
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    GhostButton("فيديو جديد دون تنظيف", fx = "sparkles") { vm.reset() }
                }
                Gap(30)
            }
        }
        Confetti(vm.celebrate)
    }
    if (full && st != null) FullscreenVideo(st, poster, timeline, tiktok) { full = false }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("تنظيف ملفات GitHub؟") },
        text = { Text("تُحذف الصور والصوت والملفات المؤقتة لهذا الفيديو من GitHub، وتبقى الملفات النصية (السيناريو والسيو). وعلى هاتفك يبقى الفيديو في المعرض، مع غلافه وأرقامه في السجل.") },
        confirmButton = { TextButton({ confirm = false; vm.published() }) { Text("نظّف") } },
        dismissButton = { TextButton({ confirm = false }) { Text("لاحقًا") } },
    )
}

/** Duration, sync, cost and production time, counted up; the gates; the cost by provider. */
@Composable
private fun ResultCard(r: ma.stickman.studio.data.VideoResult) {
    Card(tilt = true, accent = Good) { Column {
        SectionTitle("بطاقة النتيجة", fx = "trophy")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val d = countUp(r.durationS.toFloat()).toInt()
            StatTile("%d:%02d".format(d / 60, d % 60), "المدة", "stopwatch", Modifier.weight(1f))
            r.matchRate?.let { m -> StatTile("${countUp((m * 100).toFloat()).toInt()}%", "التزامن", "check", Modifier.weight(1f), Good) }
            r.totalCost?.let { c -> StatTile("%.2f$".format(Locale.US, countUp(c.toFloat())), "التكلفة", "coin", Modifier.weight(1f), Gold) }
            r.scriptMinutes?.let { m -> StatTile("${m.toInt()}", "دقيقة للسيناريو", "scroll", Modifier.weight(1f)) }
        }
        Gap(12)
        val sync = r.sync
        CheckLine(r.matchRate?.let { it >= 0.9 } ?: sync.contains("مطابقة"), "التزامن: " + (r.matchRate?.let { "${(it * 100).toInt()}% من الكلمات مطابقة" } ?: sync.ifBlank { "—" }))
        // a delivered video passed every gate (the cloud stops otherwise)
        CheckLine(true, if (sync.contains("16/16")) "فحص الإطارات 16/16" else "فحص الإطارات ناجح")
        CheckLine(true, "فحص الحركة ناجح")
        CheckLine(true, "الخطوط الحمراء سليمة")
        if (r.costBy.isNotEmpty()) {
            Gap(8)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val names = mapOf("coarena" to "Coarena", "gemini" to "Gemini", "openrouter" to "OpenRouter")
                r.costBy.filterValues { it > 0 }.forEach { (k, v) -> Pill("${names[k] ?: k}: %.2f$".format(Locale.US, v), Sky) }
                r.costUsd?.let { Pill("الإنتاج: %.2f$".format(Locale.US, it), Violet) }
            }
        }
    } }
}

/** 1.2.5: the whole video in time, as GitHub measured it: each cloud step, the user's own time between them,
 *  and three totals (from the topics to the video, the machines' time, the user's time). */
@Composable
fun ProjectTime(vm: AppViewModel, slug: String) {
    val prodCost = vm.result?.costUsd
    val p = remember(slug, vm.timingsVersion, prodCost) { ma.stickman.studio.data.Timing.project(vm.local, slug, prodCost) } ?: return
    val dur = ma.stickman.studio.data.Eta.Companion::dur
    Card { Column {
        SectionTitle("الوقت الحقيقي لهذا الفيديو", fx = "stopwatch")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(if (p.complete) dur(p.totalS) else "—", "من الفكرة إلى الفيديو", "calendar", Modifier.weight(1f))
            StatTile(dur(p.machineS), "وقت السحابة", "gear", Modifier.weight(1f), Sky)
            StatTile(if (p.complete) dur(p.userS) else "—", "وقتك أنت", "mic", Modifier.weight(1f), Gold)
        }
        Gap(10)
        p.legs.forEach { l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    color = if (l.byUser) Gold else MaterialTheme.colorScheme.onSurface)
                l.costUsd?.let { Text("%.2f$  ".format(Locale.US, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(dur(l.seconds), style = MaterialTheme.typography.titleSmall)
            }
        }
        Gap(6)
        if (!p.complete) Text("المواضيع والسيناريو لهذا الفيديو جرت قبل 1.2.5 فلم تُقس؛ تظهر كاملة من الفيديو القادم.",
            style = MaterialTheme.typography.bodySmall, color = Gold)
        Text("مقاس من ساعة GitHub" + (if (p.queueS > 20) "، منه ${dur(p.queueS)} انتظار للخوادم" else "") + ". «وقتك» هو ما بين الخطوات (الصوت والصور والمراجعة).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}

/** Countdown to the best posting time, and the reminder switch. */
@Composable
private fun BestTime(best: String, remind: Boolean, title: String, setRemind: (Boolean) -> Unit) {
    if (best.isBlank()) return
    val ctx = LocalContext.current
    val target = remember(best) { Reminder.time(best) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Card { Column {
        SectionTitle("أفضل وقت للنشر", fx = "calendar")
        Text(best, style = MaterialTheme.typography.bodyLarge)
        target?.let { t ->
            val left = Duration.between(LocalDateTime.now(ZoneId.of("Africa/Casablanca")), t).seconds.coerceAtLeast(0)
            Gap(6)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("بعد ", style = MaterialTheme.typography.titleMedium)
                RollingNumber("%d:%02d:%02d".format(left / 3600, (left % 3600) / 60, left % 60), MaterialTheme.typography.titleLarge, Gold)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ذكّرني بإشعار في هذا الوقت", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(remind, { v -> setRemind(v); if (v) Reminder.schedule(ctx, best, title) else androidx.work.WorkManager.getInstance(ctx).cancelUniqueWork("remind") })
        }
    } }
}

/** Eight frames of the video: tap one to keep it as the cover (saved in the gallery). */
@Composable
private fun CoverPicker(vm: AppViewModel) {
    var frames by remember { mutableStateOf<List<Pair<Long, ImageBitmap>>?>(null) }
    var pick by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    Card { Column {
        SectionTitle("الغلاف", fx = "picture")
        if (frames == null) GhostButton("اختر الغلاف من إطارات الفيديو", fx = "eyes") {
            frames = emptyList()
            scope.launch { frames = vm.frames().map { it.first to it.second.asImageBitmap() } }
        } else if (frames!!.isEmpty()) Busy("يستخرج الإطارات…")
        else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(frames!!) { (us, b) ->
                    Image(b, null, Modifier.width(84.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(10.dp))
                        .border(3.dp, if (pick == us) Gold else Color.Transparent, RoundedCornerShape(10.dp))
                        .pressable { pick = us }, contentScale = ContentScale.Crop)
                }
            }
            Gap(10)
            GoldButton("احفظ هذا الغلاف", fx = "picture", enabled = pick != null) { pick?.let { vm.saveCover(it) } }
        }
    } }
}

/** The video inside a phone, with an optional TikTok layer showing where its buttons and caption sit. */
@Composable
private fun PhoneFrame(overlay: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).tilt3d(0.5f, false), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().aspectRatio(9f / 17.6f).clip(RoundedCornerShape(38.dp)).background(Color(0xFF050506))
            .border(2.dp, Color(0xFF3A3A44), RoundedCornerShape(38.dp)).padding(8.dp)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp)).background(Color.Black)) {
                content()
                if (overlay) Canvas(Modifier.fillMaxSize()) {
                    // safe zones of CLAUDE.md: nothing under 84 % of the height, nothing right of 90 % between 42 and 84 %
                    drawRect(Color.Black.copy(alpha = 0.45f), Offset(0f, size.height * 0.84f), Size(size.width, size.height * 0.16f))
                    drawRect(Color.Black.copy(alpha = 0.35f), Offset(size.width * 0.86f, size.height * 0.42f), Size(size.width * 0.14f, size.height * 0.42f))
                    val x = size.width * 0.93f
                    listOf(0.48f, 0.57f, 0.66f, 0.75f).forEach { y -> drawCircle(Color.White.copy(alpha = 0.85f), size.width * 0.04f, Offset(x, size.height * y), style = Stroke(3f)) }
                    drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(size.width * 0.05f, size.height * 0.88f), Size(size.width * 0.55f, 10f), CornerRadius(5f))
                    drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(size.width * 0.05f, size.height * 0.92f), Size(size.width * 0.75f, 10f), CornerRadius(5f))
                    drawLine(Bad, Offset(0f, size.height * 0.84f), Offset(size.width, size.height * 0.84f), 2f)
                }
            }
            Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).width(70.dp).height(18.dp).clip(CircleShape).background(Color.Black))
        }
    }
}

/** "95% تزامن · 1:29 · 0.39$" for the result and history screens. */
fun stats(r: ma.stickman.studio.data.VideoResult): String = listOfNotNull(
    r.matchRate?.let { "${(it * 100).toInt()}% تزامن" },
    r.durationS.takeIf { it > 0 }?.let { "%d:%02d".format(it.toInt() / 60, it.toInt() % 60) },
    r.totalCost?.takeIf { it > 0 }?.let { "%.2f$".format(Locale.US, it) },
).joinToString(" · ")

fun shareFile(ctx: android.content.Context, f: File, mime: String) {
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), f.name))
}

fun share(ctx: android.content.Context, f: File) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "شارك الفيديو").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Straight to TikTok's own "share" screen when it is installed; otherwise the normal share sheet. */
fun shareTikTok(ctx: android.content.Context, f: File, caption: String = "") {
    // TikTok does not promise to fill its caption from another app: the caption goes to the clipboard first
    // (one long press -> paste), and also as text with the video (used when TikTok accepts it).
    if (caption.isNotBlank()) copy(ctx, "وصف تيك توك", caption)
    val pkg = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill").firstOrNull {
        runCatching { ctx.packageManager.getPackageInfo(it, 0) }.isSuccess
    } ?: return share(ctx, f)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_SEND).setType("video/mp4").setPackage(pkg).putExtra(Intent.EXTRA_STREAM, uri)
            .apply { if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { share(ctx, f) }
}
