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
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import ma.stickman.studio.ui.theme.*
import org.json.JSONObject
import java.io.File

// «مَدار» brand colours: water blue, liquid gold, glass silver (the channel logo)
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

private fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_LONG).show()
private fun clip(c: Context): String? = (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(c)?.toString()
private fun copy(c: Context, s: String) = (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("مدار", s))
private fun installed(c: Context, pkg: String) = runCatching { c.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
/** the text goes to the app's own input box (share), and to the clipboard too, so a paste always works */
private fun sendTo(c: Context, pkgs: List<String>, text: String, web: String) {
    copy(c, text); val pkg = pkgs.firstOrNull { installed(c, it) }
    val ok = pkg != null && runCatching { c.startActivity(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    if (!ok) runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
private fun nameOf(c: Context, u: Uri): String = runCatching { c.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull() ?: u.lastPathSegment ?: "ملف"

@Composable
fun MadarApp(store: Store, kit: Kit, shared: List<Uri>, onShared: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val host = remember { Host(store) }
    var topic by remember { mutableStateOf(store.topic) }; var angle by remember { mutableStateOf(store.angle) }
    var seconds by remember { mutableIntStateOf(store.seconds) }; var mode by remember { mutableStateOf(store.mode) }
    var reply by remember { mutableStateOf(store.reply) }; var voice by remember { mutableStateOf(store.voiceName) }
    var sheetName by remember { mutableStateOf(if (store.sheet.isNotBlank()) "لوحة مستوردة" else "") }
    var pc by remember { mutableStateOf<JSONObject?>(null) }; var pcChecked by remember { mutableStateOf(false) }
    var jobId by remember { mutableStateOf(store.job) }; var job by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }; var showSettings by remember { mutableStateOf(!store.paired) }
    val parsed = remember(reply) { if (reply.isBlank()) null else kit.parse(reply) }
    val report = remember(parsed, seconds) { parsed?.let { kit.check(it, seconds) } }

    // the PC, every 6 s; the job, every 2 s while it runs
    LaunchedEffect(store.token, store.hosts) { while (true) { pc = withContext(Dispatchers.IO) { if (store.paired) host.status() else null }; pcChecked = true; delay(6000) } }
    LaunchedEffect(jobId) { while (jobId.isNotBlank()) { job = withContext(Dispatchers.IO) { runCatching { host.job(jobId) }.getOrNull() } ?: job
        val st = job?.optString("stage"); delay(if (st == "done" || st == "failed" || st == "review") 6000 else 2000) } }
    // Gemini's reply: caught from the clipboard each time the app comes back
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) { val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { val t = clip(ctx); if (t != null && t != reply && kit.looksLikeReply(t)) { reply = t; store.reply = t; toast(ctx, "التقطتُ ردّ Gemini من الحافظة") } } }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) } }
    // files shared to TaleSpark: audio = the voice, image = a ChatGPT sheet
    LaunchedEffect(shared) { for (u in shared) { val t = ctx.contentResolver.getType(u) ?: ""
        runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if (t.startsWith("image")) { store.sheet = u.toString(); sheetName = nameOf(ctx, u) } else { store.voice = u.toString(); store.voiceName = nameOf(ctx, u); voice = store.voiceName } }
        if (shared.isNotEmpty()) onShared() }
    val pickVoice = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u ?: return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; store.voice = u.toString(); store.voiceName = nameOf(ctx, u); voice = store.voiceName }
    val pickSheet = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u ?: return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; store.sheet = u.toString(); sheetName = nameOf(ctx, u) }

    fun produce(force: Boolean) = scope.launch {
        busy = "أرسل السكربت والصوت إلى الحاسوب…"
        val err = withContext(Dispatchers.IO) { runCatching {
            val j = host.newJob(topic, reply, seconds, mode); val id = j.getString("id")
            host.upload("/jobs/$id/voice", ctx, Uri.parse(store.voice), "audio/*")
            if (store.sheet.isNotBlank() && report?.sheets?.isNotEmpty() == true) host.upload("/jobs/$id/sheet?names=${report.sheets.joinToString(",")}", ctx, Uri.parse(store.sheet), "image/png")
            host.run(id, force, mode); id }.fold({ jobId = it; store.job = it; null }, { it.message }) }
        busy = null; if (err != null) toast(ctx, err)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // ---- header: the channel and the PC
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Glass, Liquid, Water))).border(2.dp, Glass, CircleShape), contentAlignment = Alignment.Center) {
                Text("MD", fontWeight = FontWeight.Black, color = Color(0xFF0A2A50), fontSize = 16.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("مَدار", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Liquid)
                Text("TaleSpark 2.0 · استوديو الخرائط", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconText(if (pc != null) "الحاسوب جاهز" else if (!store.paired) "غير مقترن" else if (pcChecked) "الحاسوب غير متاح" else "…", if (pc != null) Good else Bad) { showSettings = !showSettings }
        }
        pc?.let { Text("${it.optString("name")} · ${it.optJSONObject("gpu")?.optString("name") ?: ""}${if (it.optBoolean("busy")) " · يعمل الآن" else ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        AnimatedVisibility(showSettings) { Settings(store) { showSettings = false } }

        // ---- 1. the topic
        Step(1, "الموضوع") {
            OutlinedTextField(topic, { topic = it; store.topic = it }, Modifier.fillMaxWidth(), label = { Text("موضوع الفيديو") }, minLines = 2)
            OutlinedTextField(angle, { angle = it; store.angle = it }, Modifier.fillMaxWidth(), label = { Text("الزاوية (اختياري)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { for (s in listOf(60, 90, 120)) FilterChip(seconds == s, { seconds = s; store.seconds = s }, label = { Text("$s ث") }) }
            Text("أفكار:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRowCompat { for (i in IDEAS) AssistChip({ topic = i; store.topic = i }, label = { Text(i, fontSize = 12.sp) }) }
        }
        // ---- 2. Gemini writes it
        Step(2, "السكربت من Gemini") {
            Text("يُكتب الأمر كاملاً (الخطّاف، والفصحى، والطول، والأرقام كلمات، والوقف، والتشكيل، وخطة اللقطات). اختر النموذج في Gemini وأرسل، ثم انسخ الرد كاملاً وعُد إلى هنا.", fontSize = 14.sp)
            Button({ if (topic.isBlank()) toast(ctx, "اكتب الموضوع أولاً") else sendTo(ctx, listOf(GEMINI), kit.prompt(topic, seconds, angle), "https://gemini.google.com/app") }, Modifier.fillMaxWidth()) { Text("افتح Gemini والأمر ملصوق") }
            OutlinedButton({ val t = clip(ctx); if (t.isNullOrBlank()) toast(ctx, "الحافظة فارغة") else { reply = t; store.reply = t } }, Modifier.fillMaxWidth()) { Text("الصق الرد من الحافظة") }
        }
        // ---- 3. the check
        if (report != null && parsed != null) Step(3, "فحص السكربت") {
            Text(if (report.ok) "سليم: ${report.lines} سطراً، ${report.words} كلمة، نحو ${report.seconds} ثانية." else "يحتاج إصلاحاً: ${report.problems.count { it.error }} خطأ.",
                color = if (report.ok) Good else Bad, fontWeight = FontWeight.Bold)
            for (p in report.problems.take(14)) Text("• ${p.text}", fontSize = 13.sp, color = if (p.error) Bad else Warn)
            if (report.problems.isNotEmpty()) OutlinedButton({ sendTo(ctx, listOf(GEMINI), kit.fixPrompt(report), "https://gemini.google.com/app") }, Modifier.fillMaxWidth()) { Text("اطلب من Gemini تصحيحه") }
            Text(parsed.script.joinToString("\n"), fontSize = 15.sp, lineHeight = 26.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp))
        }
        // ---- 4. the voice
        if (parsed != null) Step(4, "الصوت") {
            Text("افتح ElevenLabs والسكربت ملصوق، اختر الصوت وولّده واسمعه، ثم نزّله وشاركه إلى TaleSpark أو اختره من هنا.", fontSize = 14.sp)
            Button({ sendTo(ctx, ELEVEN, parsed.script.joinToString("\n"), "https://elevenlabs.io/app/speech-synthesis/text-to-speech") }, Modifier.fillMaxWidth()) { Text("افتح ElevenLabs والنص ملصوق") }
            OutlinedButton({ pickVoice.launch(arrayOf("audio/*")) }, Modifier.fillMaxWidth()) { Text(if (voice.isBlank()) "اختر ملف الصوت" else "الصوت: $voice (تغيير)") }
        }
        // ---- 5. sheets, only for a new moving object
        if (report != null && report.sheets.isNotEmpty()) Step(5, "لوحة مجسمات جديدة") {
            Text("الخطة تحتاج مجسماً متحركاً غير موجود: ${report.sheets.joinToString("، ")}. يُكتب أمر اللوحة (منظر علوي) فتصنعها في ChatGPT وتستوردها.", fontSize = 14.sp)
            Button({ sendTo(ctx, listOf(CHATGPT), kit.sheetPrompt(report.sheets.map { "A ${it.replace('_', ' ')} seen from directly above." }), "https://chatgpt.com/") }, Modifier.fillMaxWidth()) { Text("افتح ChatGPT والأمر ملصوق") }
            OutlinedButton({ pickSheet.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth()) { Text(if (sheetName.isBlank()) "استورد اللوحة" else "اللوحة: $sheetName (تغيير)") }
        }
        // ---- 6. production
        if (parsed != null) Step(6, "الإنتاج") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(mode == "auto", { mode = "auto"; store.mode = "auto" }, label = { Text("تلقائي") })
                FilterChip(mode == "review", { mode = "review"; store.mode = "review" }, label = { Text("أراجع اللقطات أولاً") })
            }
            val ready = pc != null && voice.isNotBlank() && busy == null
            Button({ produce(false) }, Modifier.fillMaxWidth().height(54.dp), enabled = ready && report?.ok == true, colors = ButtonDefaults.buttonColors(containerColor = Liquid, contentColor = Color(0xFF2A1A00))) {
                Text("أنتج الفيديو", fontSize = 18.sp, fontWeight = FontWeight.Black) }
            if (report?.ok == false) TextButton({ produce(true) }, enabled = ready) { Text("أنتج رغم الملاحظات") }
            if (!ready && busy == null) Text(when { pc == null -> "الحاسوب غير متاح: شغّل المضيف أو تحقق من الاقتران."; voice.isBlank() -> "اختر ملف الصوت أولاً."; else -> "" }, fontSize = 13.sp, color = Warn)
            busy?.let { Text(it, color = Water) }
        }
        job?.let { JobCard(it, host, store) { jobId = ""; store.job = ""; job = null } }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun JobCard(j: JSONObject, host: Host, store: Store, onClose: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val id = j.optString("id"); val st = j.optString("stage")
    var look by remember(id) { mutableStateOf<ImageBitmap?>(null) }; var video by remember(id) { mutableStateOf<File?>(null) }; var dl by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(id, st) { if (st == "review" || st == "done") look = withContext(Dispatchers.IO) { runCatching { val f = File(ctx.cacheDir, "look-$id.jpg"); host.download("/jobs/$id/look.jpg", f); BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() } }
    Step(7, "على الحاسوب") {
        Text(j.optString("stage_ar", st), fontWeight = FontWeight.Bold, color = when (st) { "done" -> Good; "failed" -> Bad; "review" -> Liquid; else -> Water })
        if (st != "done" && st != "failed") LinearProgressIndicator(progress = { j.optDouble("progress", 0.0).toFloat() }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Liquid)
        j.optString("error").takeIf { it.isNotBlank() && it != "null" }?.let { Text(it, color = Bad) }
        j.optJSONObject("saycheck")?.optJSONArray("problems")?.let { a -> for (i in 0 until minOf(a.length(), 8)) { val p = a.getJSONObject(i); Text("• ${p.optString("text")}", fontSize = 13.sp, color = if (p.optString("level") == "error") Bad else Warn) } }
        look?.let { Image(it, "ورقة اللقطات", Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.FillWidth) }
        if (st == "review") Button({ scope.launch { withContext(Dispatchers.IO) { runCatching { host.approve(id) } } } }, Modifier.fillMaxWidth()) { Text("موافق، ارسم الفيديو") }
        if (st == "done") {
            if (video == null) Button({ scope.launch { dl = 0f; video = withContext(Dispatchers.IO) { runCatching { host.download("/jobs/$id/video.mp4", File(ctx.cacheDir, "$id.mp4")) { dl = it } }.getOrNull() }; dl = -1f } }, Modifier.fillMaxWidth(), enabled = dl < 0) {
                Text(if (dl >= 0) "أنزّل الفيديو… ${(dl * 100).toInt()}%" else "نزّل الفيديو وشاهده") }
            video?.let { f ->
                Player(f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ toast(ctx, if (saveToGallery(ctx, f)) "حُفظ في المعرض (Movies/Madar)" else "تعذّر الحفظ") }, Modifier.weight(1f)) { Text("احفظ في المعرض") }
                    OutlinedButton({ val u = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "شارك الفيديو")) }, Modifier.weight(1f)) { Text("شارك") }
                }
            }
            j.optJSONObject("timing")?.let { t -> Text("زمن الآلة: الرسم ${t.optDouble("render+encode", 0.0).toInt()} ث", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (st == "failed") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ scope.launch { withContext(Dispatchers.IO) { runCatching { host.run(id, false, store.mode) } } } }) { Text("أعد المحاولة") }
            OutlinedButton({ scope.launch { withContext(Dispatchers.IO) { runCatching { host.run(id, true, store.mode) } } } }) { Text("تابع رغم ذلك") }
        }
        TextButton(onClose) { Text("إغلاق هذه المهمة") }
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

@Composable
private fun Settings(store: Store, onDone: () -> Unit) {
    var hosts by remember { mutableStateOf(store.hosts) }; var port by remember { mutableStateOf(store.port.toString()) }; var token by remember { mutableStateOf(store.token) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("الاقتران بالحاسوب", fontWeight = FontWeight.Bold)
        Text("افتح رابط الاقتران الذي يطبعه المضيف على الحاسوب (talespark://pair…)، أو اكتب العنوان والرمز هنا. يعمل على واي فاي البيت، ومن أي مكان عبر Tailscale.", fontSize = 13.sp)
        OutlinedTextField(hosts, { hosts = it }, Modifier.fillMaxWidth(), label = { Text("عناوين الحاسوب (مفصولة بفاصلة)") }, singleLine = true)
        OutlinedTextField(port, { port = it }, Modifier.fillMaxWidth(), label = { Text("المنفذ") }, singleLine = true)
        OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("رمز الاقتران") }, singleLine = true)
        Button({ store.hosts = hosts.trim(); store.port = port.toIntOrNull() ?: 8765; store.token = token.trim(); onDone() }) { Text("احفظ") }
    }
}

@Composable
private fun Step(n: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(Liquid, Water))), contentAlignment = Alignment.Center) { Text("$n", fontWeight = FontWeight.Black, color = Color.White) }
            Spacer(Modifier.width(10.dp)); Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
private fun IconText(text: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.clip(CircleShape).background(color.copy(alpha = .15f)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(6.dp)); Text(text, fontSize = 12.sp, color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCompat(content: @Composable () -> Unit) = FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
