@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ma.stickman.studio.madar

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ma.stickman.studio.ui.*
import ma.stickman.studio.ui.Card
import ma.stickman.studio.ui.theme.*
import org.json.JSONObject
import java.io.File

// TaleSpark 2.0 «مَدار»: the look of TaleSpark 1.x (bottom tabs, flat cards, line icons, snackbars, sounds and haptics),
// with the maps path inside: topic -> Gemini -> check -> ElevenLabs -> (sheet) -> the PC makes the film.
private val Water = Color(0xFF1F95D4); private val Liquid = Color(0xFFFFC21A); private val Glass = Color(0xFFDFE6EE)
private const val GEMINI = "com.google.android.apps.bard"; private const val CHATGPT = "com.openai.chatgpt"
private val ELEVEN = listOf("io.elevenlabs.app", "com.elevenlabs.app", "io.elevenlabs.android")
private val IDEAS = listOf(
    "مضيق جبل طارق: لماذا لم يُبنَ جسر بين المغرب وإسبانيا؟", "قناة السويس: كم يخسر العالم إن أُغلقت أسبوعاً؟",
    "مضيق ملقا: أخطر عنق زجاجة لتجارة الصين", "سد النهضة: لماذا تخاف مصر من النيل الأزرق؟",
    "قناة بنما: لماذا تجف أهم قناة في الأمريكتين؟", "جزر الكوريل: لماذا لم تنتهِ الحرب العالمية الثانية بين روسيا واليابان؟",
    "بحر الصين الجنوبي: الجزر الصناعية الصينية", "مضيق البوسفور: المدينة التي تقف على قارتين",
    "طريق الحرير الجديد: الصين إلى أوروبا براً", "الصحراء المغربية: ميناء الداخلة الأطلسي",
)
/** the approved AI Studio narrator style (CLAUDE.md, item 9): Riko, live fusha, every punctuation pause kept */
private const val STYLE = "A live, engaged human documentary narrator speaking pure Modern Standard Arabic (fusha) with perfect classical pronunciation, no dialect at all. Warm, natural and expressive like a real person telling a gripping story, rich vocal modulation, never robotic or flat. Respect every punctuation mark fully: a short breath at each comma, a clear stop at each period, a suspenseful pause at each colon and ellipsis, a rising questioning tone at each question mark, and real energy and emphasis at each exclamation mark. Lean on the key numbers and names."
private val TABS = listOf(Triple("الاستوديو", "clapper", 0), Triple("الأفلام", "film", 1), Triple("الإعدادات", "gear", 2))

private fun clip(c: Context): String? = (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(c)?.toString()
private fun copyText(c: Context, s: String) = (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("مدار", s))
private fun installed(c: Context, pkg: String) = runCatching { c.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
/** the text goes into the app's own input box (share) and to the clipboard, so a paste always works */
private fun sendTo(c: Context, pkgs: List<String>, text: String, web: String) {
    copyText(c, text); val pkg = pkgs.firstOrNull { installed(c, it) }
    val ok = pkg != null && runCatching { c.startActivity(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    if (!ok) runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
private fun nameOf(c: Context, u: Uri): String = runCatching { c.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull() ?: u.lastPathSegment ?: "ملف"

/** what the screens share: the PC, the current film and the messages at the bottom */
class Madar(val store: Store, val kit: Kit) {
    val host = Host(store)
    var pc by mutableStateOf<JSONObject?>(null); var pcChecked by mutableStateOf(false)
    var jobId by mutableStateOf(store.job); var job by mutableStateOf<JSONObject?>(null)
    var busy by mutableStateOf<String?>(null)
    var toast: (String, Int) -> Unit = { _, _ -> }
}

@Composable
fun MadarApp(store: Store, kit: Kit, shared: List<Uri>, onShared: () -> Unit) {
    val m = remember { Madar(store, kit) }; val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }; val scope = rememberCoroutineScope(); val fb = LocalFeedback.current
    m.toast = { t, kind -> when (kind) { 1 -> fb.success(); -1 -> fb.error() }; scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(t) } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = tab != 0) { tab = 0 }
    val motion = LocalMotion.current; val outlineC = MaterialTheme.colorScheme.outlineVariant
    // the PC every 6 s; the film every 2 s while it is being made
    LaunchedEffect(store.token, store.hosts) { while (true) { m.pc = withContext(Dispatchers.IO) { if (store.paired) m.host.status() else null }; m.pcChecked = true; delay(6000) } }
    LaunchedEffect(m.jobId) { var last = ""
        while (m.jobId.isNotBlank()) { m.job = withContext(Dispatchers.IO) { runCatching { m.host.job(m.jobId) }.getOrNull() } ?: m.job
            val st = m.job?.optString("stage") ?: ""; if (st != last && last.isNotEmpty()) when (st) { "done" -> m.toast("الفيديو جاهز!", 1); "failed" -> m.toast("توقف الإنتاج: افتح التفاصيل", -1); "review" -> m.toast("ورقة اللقطات جاهزة للمراجعة", 1) }
            last = st; delay(if (st == "done" || st == "failed" || st == "review") 6000 else 2000) } }
    // files shared to TaleSpark: audio = the voice, image = a ChatGPT sheet
    LaunchedEffect(shared) { for (u in shared) { val t = ctx.contentResolver.getType(u) ?: ""
        runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if (t.startsWith("image")) { store.sheet = u.toString(); m.toast("استُوردت اللوحة", 1) } else { store.voice = u.toString(); store.voiceName = nameOf(ctx, u); m.toast("استُورد الصوت: ${store.voiceName}", 1) } }
        if (shared.isNotEmpty()) { tab = 0; onShared() } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        DepthBackground()
        Row(Modifier.fillMaxSize()) {
            if (wide) NavigationRail(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), modifier = Modifier.statusBarsPadding()) {
                Spacer(Modifier.height(12.dp)); TABS.forEach { (l, fx, i) -> NavigationRailItem(tab == i, { fb.tap(); tab = i }, { NavIcon(fx, tab == i) }, label = { Text(l) }) } }
            Scaffold(modifier = Modifier.weight(1f), containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground,
                snackbarHost = { SnackbarHost(snack) { d -> Snackbar(d, shape = RoundedCornerShape(18.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface) } },
                bottomBar = { if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp,
                    modifier = Modifier.drawBehind { drawLine(outlineC, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1f) }) {
                    TABS.forEach { (l, fx, i) -> NavigationBarItem(tab == i, { fb.tap(); tab = i }, { NavIcon(fx, tab == i) }, label = { Text(l) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh, selectedTextColor = MaterialTheme.colorScheme.onSurface, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant)) } } },
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
                    Centered {
                        AnimatedContent(tab, label = "tab", transitionSpec = { val dir = if (targetState > initialState) 1 else -1
                            (fadeIn(tween((220 * motion).toInt())) + slideInVertically(tween((260 * motion).toInt())) { it / 20 * dir }).togetherWith(fadeOut(tween((100 * motion).toInt()))) }) { t ->
                            holder.SaveableStateProvider(t) { when (t) { 0 -> StudioTab(m) { tab = 2 }; 1 -> FilmsTab(m); else -> SettingsTab(m) } }
                        }
                    }
                    m.busy?.let { b -> Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) { Busy(b) } }
                    if (tab != 2) UpdatePill(Modifier.align(Alignment.TopCenter).padding(top = 6.dp)) { m.toast(it, -1) }
                }
            }
        }
    }
}

@Composable private fun NavIcon(fx: String, selected: Boolean) = FxIcon(fx, 24.dp, tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)

// ================================================================== الاستوديو
@Composable
private fun StudioTab(m: Madar, openSettings: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val s = m.store; val kit = m.kit
    var topic by remember { mutableStateOf(s.topic) }; var angle by remember { mutableStateOf(s.angle) }
    var seconds by remember { mutableIntStateOf(s.seconds) }; var reply by remember { mutableStateOf(s.reply) }
    var voice by remember { mutableStateOf(s.voiceName) }; var sheet by remember { mutableStateOf(s.sheet) }
    val parsed = remember(reply) { if (reply.isBlank()) null else kit.parse(reply) }
    val report = remember(parsed, seconds) { parsed?.let { kit.check(it, seconds) } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) { val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) {
            voice = s.voiceName; sheet = s.sheet
            val t = clip(ctx); if (t != null && t != reply && kit.looksLikeReply(t)) { reply = t; s.reply = t; m.toast("التقطتُ ردّ Gemini من الحافظة", 1) } } }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) } }
    val pickVoice = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u ?: return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; s.voice = u.toString(); s.voiceName = nameOf(ctx, u); voice = s.voiceName }
    val pickSheet = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u ?: return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; s.sheet = u.toString(); sheet = s.sheet }
    fun produce(force: Boolean) = scope.launch {
        m.busy = "أرسل السكربت والصوت إلى الحاسوب…"
        val err = withContext(Dispatchers.IO) { runCatching {
            val j = m.host.newJob(topic, reply, seconds, s.mode); val id = j.getString("id")
            m.host.upload("/jobs/$id/voice", ctx, Uri.parse(s.voice), "audio/*")
            if (s.sheet.isNotBlank() && report?.sheets?.isNotEmpty() == true) m.host.upload("/jobs/$id/sheet?names=${report.sheets.joinToString(",")}", ctx, Uri.parse(s.sheet), "image/png")
            m.host.run(id, force, s.mode); id }.fold({ m.jobId = it; s.job = it; null }, { it.message }) }
        m.busy = null; if (err != null) m.toast(err, -1) else m.toast("بدأ الإنتاج على الحاسوب", 1)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(m, openSettings)
        m.job?.let { JobCard(m, it) }
        // 1. topic
        Card { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("الموضوع", fx = "globe")
            OutlinedTextField(topic, { topic = it; s.topic = it }, Modifier.fillMaxWidth(), label = { Text("موضوع الفيديو") }, minLines = 2, shape = RoundedCornerShape(12.dp))
            OutlinedTextField(angle, { angle = it; s.angle = it }, Modifier.fillMaxWidth(), label = { Text("الزاوية (اختياري)") }, singleLine = true, shape = RoundedCornerShape(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (x in listOf(60, 90, 120)) Chip("$x ث", seconds == x) { seconds = x; s.seconds = x } }
            Text("أفكار", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { for (i in IDEAS) Chip(i, topic == i, fx = "bulb") { topic = i; s.topic = i } }
        } }
        // 2. Gemini
        Card { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("السكربت من Gemini", fx = "sparkles")
            Text("الأمر جاهز بكل القواعد (الخطّاف، والفصحى، والأرقام كلمات، والوقف، والتشكيل)، وGemini يكتب السكربت وحده. أرسل، ثم اضغط زر «نسخ» تحت ردّه وعُد إلى هنا: يُلتقط وحده.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GoldButton("افتح Gemini والأمر ملصوق", fx = "rocket") { if (topic.isBlank()) m.toast("اكتب الموضوع أولاً", -1) else sendTo(ctx, listOf(GEMINI), kit.prompt(topic, seconds, angle), "https://gemini.google.com/app") }
            GhostButton("الصق الرد من الحافظة", Modifier.fillMaxWidth(), fx = "scroll") { val t = clip(ctx); if (t.isNullOrBlank()) m.toast("الحافظة فارغة", -1) else { reply = t; s.reply = t } }
        } }
        // 3. check
        if (parsed != null && parsed.script.isEmpty()) Card(accent = Warn) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {   // not a Gemini reply at all: one clear sentence
            SectionTitle("لم أجد سكربتاً في المنسوخ", fx = "warning")
            Text("الذي في الحافظة ليس ردّ Gemini الكامل. في Gemini اضغط زر «نسخ» تحت الرد (لا تنسخ جزءاً منه)، ثم عُد إلى هنا فيُلتقط وحده، أو اضغط «الصق الرد من الحافظة».", style = MaterialTheme.typography.bodyMedium)
            Text("ما التقطتُه (${reply.length} حرفاً):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(reply.take(600), style = MaterialTheme.typography.bodySmall, maxLines = 14, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp))
            GhostButton("امسح وابدأ من جديد", Modifier.fillMaxWidth(), fx = "wastebasket") { reply = ""; s.reply = "" }
        } }
        else if (report != null && parsed != null) Card(accent = if (report.ok) Good else Bad) { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("فحص السكربت", fx = "check", trailing = { Pill(if (report.ok) "سليم" else "${report.problems.count { it.error }} خطأ", if (report.ok) Good else Bad) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatTile("${report.lines}", "سطراً", "scroll", Modifier.weight(1f)); StatTile("${report.words}", "كلمة", "books", Modifier.weight(1f)); StatTile("${report.seconds}", "ثانية", "stopwatch", Modifier.weight(1f)) }
            for (p in report.problems.take(14)) CheckLine(!p.error, p.text, warn = !p.error)
            if (report.problems.isNotEmpty()) GhostButton("اطلب من Gemini تصحيحه", Modifier.fillMaxWidth(), fx = "speech") { sendTo(ctx, listOf(GEMINI), kit.fixPrompt(report), "https://gemini.google.com/app") }
            Text(parsed.script.joinToString("\n"), style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp))
        } }
        // 4. voice
        if (parsed != null && parsed.script.isNotEmpty()) Card { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("الصوت", fx = "mic", trailing = { if (voice.isNotBlank()) Pill("جاهز", Good) })
            Text("AI Studio: يُنسخ السكربت ويُفتح «توليد الكلام». اختر الصوت Riko، والصق وصف الأسلوب (زر النسخ أدناه)، ثم Run وDownload، وشارك الملف إلى TaleSpark أو اختره من هنا.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GoldButton("افتح AI Studio والسكربت منسوخ", fx = "mic") { copyText(ctx, parsed.script.joinToString("\n")); runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/generate-speech")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; m.toast("نُسخ السكربت: الصقه في خانة النص", 1) }
            GhostButton("انسخ وصف أسلوب الراوي", Modifier.fillMaxWidth(), fx = "scroll") { copyText(ctx, STYLE); m.toast("نُسخ وصف الأسلوب: الصقه في Style", 1) }
            GhostButton("ElevenLabs بدلاً منه", Modifier.fillMaxWidth(), fx = "speech") { sendTo(ctx, ELEVEN, parsed.script.joinToString("\n"), "https://elevenlabs.io/app/speech-synthesis/text-to-speech") }
            GhostButton(if (voice.isBlank()) "اختر ملف الصوت" else "الصوت: $voice", Modifier.fillMaxWidth(), fx = "folder") { pickVoice.launch(arrayOf("audio/*")) }
        } }
        // 5. a sheet, only for a new moving object
        if (report != null && report.sheets.isNotEmpty()) Card(accent = Warn) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("لوحة مجسمات جديدة", fx = "palette")
            Text("الخطة تحتاج مجسماً متحركاً غير موجود: ${report.sheets.joinToString("، ")}. يُكتب أمر اللوحة (منظر علوي) فتصنعها في ChatGPT وتستوردها.", style = MaterialTheme.typography.bodyMedium)
            GoldButton("افتح ChatGPT والأمر ملصوق", fx = "picture") { sendTo(ctx, listOf(CHATGPT), kit.sheetPrompt(report.sheets.map { "A ${it.replace('_', ' ')} seen from directly above." }), "https://chatgpt.com/") }
            GhostButton(if (sheet.isBlank()) "استورد اللوحة" else "اللوحة مستوردة (تغيير)", Modifier.fillMaxWidth(), fx = "folder") { pickSheet.launch(arrayOf("image/*")) }
        } }
        // 6. production
        if (parsed != null) Card { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("الإنتاج", fx = "film")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Chip("تلقائي", s.mode == "auto", fx = "rocket") { s.mode = "auto" }; Chip("أراجع اللقطات أولاً", s.mode == "review", fx = "eyes") { s.mode = "review" } }
            val ready = m.pc != null && voice.isNotBlank() && m.busy == null
            GoldButton("أنتج الفيديو", enabled = ready && report?.ok == true, loading = m.busy != null, fx = "clapper") { produce(false) }
            if (report?.ok == false) GhostButton("أنتج رغم الملاحظات", Modifier.fillMaxWidth(), enabled = ready) { produce(true) }
            if (!ready && m.busy == null) CheckLine(false, when { m.pc == null -> "الحاسوب غير متاح: شغّل المضيف أو تحقق من الاقتران."; else -> "اختر ملف الصوت أولاً." }, warn = true)
        } }
        Gap(24)
    }
}

@Composable
private fun Header(m: Madar, openSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Glass, Liquid, Water))).border(1.5.dp, Glass, CircleShape), contentAlignment = Alignment.Center) {
            Text("MD", fontWeight = FontWeight.Black, color = Color(0xFF0A2A50), fontSize = 15.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text("مَدار", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text("استوديو الخرائط", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).pressable(onClick = openSettings).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (m.pc != null) 1 else if (!m.store.paired || m.pcChecked) -1 else 0); Spacer(Modifier.width(6.dp))
            Text(if (m.pc != null) "الحاسوب جاهز" else if (!m.store.paired) "غير مقترن" else "غير متاح", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun JobCard(m: Madar, j: JSONObject) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val id = j.optString("id"); val st = j.optString("stage")
    var look by remember(id) { mutableStateOf<ImageBitmap?>(null) }; var video by remember(id) { mutableStateOf<File?>(File(ctx.cacheDir, "$id.mp4").takeIf { it.exists() }) }; var dl by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(id, st) { if (st == "review" || st == "done") look = withContext(Dispatchers.IO) { runCatching { val f = File(ctx.cacheDir, "look-$id.jpg"); m.host.download("/jobs/$id/look.jpg", f); BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() } }
    Card(accent = when (st) { "done" -> Good; "failed" -> Bad; "review" -> Liquid; else -> Water }) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(j.optString("topic").ifBlank { "فيلم جديد" }, fx = "clapper", trailing = { Pill(j.optString("stage_ar", st), when (st) { "done" -> Good; "failed" -> Bad; else -> Gold }) })
        if (st != "done" && st != "failed") { LinearProgressIndicator(progress = { j.optDouble("progress", 0.0).toFloat() }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
            Text("${(j.optDouble("progress", 0.0) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        j.optString("error").takeIf { it.isNotBlank() && it != "null" }?.let { CheckLine(false, it) }
        j.optJSONObject("saycheck")?.optJSONArray("problems")?.let { a -> for (i in 0 until minOf(a.length(), 8)) { val p = a.getJSONObject(i); CheckLine(p.optString("level") != "error", p.optString("text"), warn = p.optString("level") != "error") } }
        look?.let { Image(it, "ورقة اللقطات", Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.FillWidth) }
        if (st == "review") GoldButton("موافق، ارسم الفيديو", fx = "check") { scope.launch { withContext(Dispatchers.IO) { runCatching { m.host.approve(id) } } } }
        if (st == "done") {
            if (video == null) GoldButton(if (dl >= 0) "أنزّل الفيديو… ${(dl * 100).toInt()}%" else "نزّل الفيديو وشاهده", enabled = dl < 0, fx = "film") { scope.launch {
                dl = 0f; video = withContext(Dispatchers.IO) { runCatching { m.host.download("/jobs/$id/video.mp4", File(ctx.cacheDir, "$id.mp4")) { dl = it } }.getOrNull() }; dl = -1f
                if (video == null) m.toast("تعذّر التنزيل", -1) } }
            video?.let { f -> Player(f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoldButton("احفظ في المعرض", Modifier.weight(1f), fx = "folder") { val ok = saveToGallery(ctx, f); m.toast(if (ok) "حُفظ في المعرض (Movies/Madar)" else "تعذّر الحفظ", if (ok) 1 else -1) }
                    GhostButton("شارك", Modifier.weight(1f), fx = "rocket") { val u = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "شارك الفيديو")) }
                } }
        }
        if (st == "failed") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("أعد المحاولة", Modifier.weight(1f)) { scope.launch { withContext(Dispatchers.IO) { runCatching { m.host.run(id, false, m.store.mode) } } } }
            GhostButton("تابع رغم ذلك", Modifier.weight(1f)) { scope.launch { withContext(Dispatchers.IO) { runCatching { m.host.run(id, true, m.store.mode) } } } }
        }
        if (st == "done" || st == "failed") TextButton({ m.jobId = ""; m.store.job = ""; m.job = null }) { Text("إخفاء هذه البطاقة") }
    } }
}

// ================================================================== الأفلام
@Composable
private fun FilmsTab(m: Madar) {
    var list by remember { mutableStateOf<List<JSONObject>?>(null) }
    LaunchedEffect(m.pc != null) { list = withContext(Dispatchers.IO) { runCatching { m.host.jobs() }.getOrNull() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الأفلام", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        when {
            m.pc == null -> EmptyState("phone", "الحاسوب غير متاح الآن، فلا أرى أفلامه.")
            list == null -> SkeletonCard()
            list!!.isEmpty() -> EmptyState("film", "لا أفلام بعد. ابدأ من الاستوديو.")
            else -> for (j in list!!) Card(onClick = { m.jobId = j.optString("id"); m.store.job = m.jobId; m.job = j }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FxIcon("clapper", 22.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(j.optString("topic").ifBlank { j.optString("id") }, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        Text(j.optString("created").replace('T', ' ').take(16), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Pill(j.optString("stage_ar"), when (j.optString("stage")) { "done" -> Good; "failed" -> Bad; else -> Gold })
                }
            }
        }
        if (m.jobId.isNotBlank()) m.job?.let { JobCard(m, it) }
    }
}

// ================================================================== الإعدادات
@Composable
private fun SettingsTab(m: Madar) {
    val s = m.store; var hosts by remember { mutableStateOf(s.hosts) }; var port by remember { mutableStateOf(s.port.toString()) }; var token by remember { mutableStateOf(s.token) }
    var theme by remember { mutableStateOf(s.theme) }; var haptics by remember { mutableStateOf(s.haptics) }; var sounds by remember { mutableStateOf(s.sounds) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الإعدادات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Section("الحاسوب", "phone", if (m.pc != null) "متصل: ${m.pc!!.optString("name")}" else "غير متصل", startOpen = m.pc == null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                m.pc?.let { p -> CheckLine(true, "${p.optString("name")} · ${p.optJSONObject("gpu")?.optString("name") ?: ""}"); if (p.optBoolean("busy")) CheckLine(true, "يعمل الآن على فيلم", warn = true) }
                Text("افتح رابط الاقتران الذي يطبعه المضيف (talespark://pair…)، أو اكتب العنوان والرمز. يعمل على واي فاي البيت، ومن أي مكان عبر Tailscale.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(hosts, { hosts = it }, Modifier.fillMaxWidth(), label = { Text("عناوين الحاسوب (مفصولة بفاصلة)") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(port, { port = it }, Modifier.fillMaxWidth(), label = { Text("المنفذ") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("رمز الاقتران") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                GoldButton("احفظ", fx = "key") { s.hosts = hosts.trim(); s.port = port.toIntOrNull() ?: 8765; s.token = token.trim(); m.toast("حُفظ", 1) }
            }
        }
        Section("الإنتاج", "film", if (s.mode == "auto") "تلقائي" else "أراجع اللقطات أولاً") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Chip("تلقائي", s.mode == "auto") { s.mode = "auto" }; Chip("أراجع اللقطات أولاً", s.mode == "review") { s.mode = "review" } }
        }
        Section("المظهر", "palette", when (theme) { "light" -> "فاتح"; "system" -> "مثل الهاتف"; else -> "داكن" }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for ((k, l) in listOf("dark" to "داكن", "light" to "فاتح", "system" to "مثل الهاتف")) Chip(l, theme == k) { theme = k; s.theme = k } }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("الاهتزاز عند اللمس", Modifier.weight(1f)); Switch(haptics, { haptics = it; s.haptics = it }) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("أصوات الواجهة", Modifier.weight(1f)); Switch(sounds, { sounds = it; s.sounds = it }) }
            }
        }
        Card { Row(verticalAlignment = Alignment.CenterVertically) { FxIcon("package", 22.dp); Spacer(Modifier.width(10.dp))
            Text("TaleSpark ${ma.stickman.studio.BuildConfig.VERSION_NAME} · «مَدار»", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium) } }
        UpdatePill(Modifier) { m.toast(it, -1) }
    }
}

// ================================================================== shared
/** a newer TaleSpark on GitHub: one tap downloads it and opens the installer (it installs over this version) */
@Composable
private fun UpdatePill(modifier: Modifier, onError: (String) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var rel by remember { mutableStateOf<Updater.Release?>(null) }; var dl by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(Unit) { rel = withContext(Dispatchers.IO) { runCatching { Updater.check(ma.stickman.studio.BuildConfig.VERSION_NAME) }.getOrNull() } }
    val r = rel ?: return
    Row(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).pressable(enabled = dl < 0) { scope.launch {
            dl = 0f; val f = withContext(Dispatchers.IO) { runCatching { Updater.download(r, File(ctx.cacheDir, "update/TaleSpark-${r.version}.apk")) { dl = it } }.getOrNull() }; dl = -1f
            if (f == null) { onError("تعذّر تنزيل التحديث"); return@launch }
            if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) { onError("اسمح لـ TaleSpark بتثبيت التطبيقات، ثم اضغط مرة أخرى")
                ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return@launch }
            val u = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        .padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        FxIcon("package", 20.dp); Spacer(Modifier.width(6.dp))
        Text(if (dl >= 0) "أنزّل التحديث… ${(dl * 100).toInt()}%" else "تحديث ${r.version} جاهز: اضغط للتثبيت", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Player(f: File) {
    val ctx = LocalContext.current
    val p = remember(f) { ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(Uri.fromFile(f))); prepare() } }
    DisposableEffect(p) { onDispose { p.release() } }
    AndroidView({ PlayerView(it).apply { player = p } }, Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)))
}

private fun saveToGallery(c: Context, f: File): Boolean = runCatching {
    val v = ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, f.name); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Madar") }
    val u = c.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)!!
    c.contentResolver.openOutputStream(u)!!.use { o -> f.inputStream().use { it.copyTo(o) } }; true }.getOrDefault(false)
