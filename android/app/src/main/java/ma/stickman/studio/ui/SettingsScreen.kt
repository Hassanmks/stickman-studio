@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ma.stickman.studio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ma.stickman.studio.BuildConfigInfo
import ma.stickman.studio.data.ModelInfo
import java.util.Locale

/** Recommended models, best first. Prices are per million tokens (in / out) as listed on OpenRouter. */
val RECOMMENDED = listOf(
    ModelInfo("auto", "تلقائي — Coarena لكل الخطوات ثم Gemini عند نفاد الرصيد (موصى به)", 0.0, 0.0, true),
    ModelInfo("coarena", "Coarena دائمًا (Claude أو أقوى نموذج متاح فيها)", 0.0, 0.0, true),
    ModelInfo("gemini", "Gemini Flash دائمًا (مفتاحك من AI Studio)", 0.0, 0.0, true),
    ModelInfo("free", "نماذج OpenRouter المجانية (أبطأ)", 0.0, 0.0, true),
    ModelInfo("anthropic/claude-sonnet-5.5", "Claude Sonnet 5.5 عبر OpenRouter (مدفوع)", 2.0, 10.0, false),
    ModelInfo("google/gemini-3.8-flash", "Gemini 3.8 Flash عبر OpenRouter", 0.83, 4.12, false),
    ModelInfo("deepseek/deepseek-v4.1-flash", "DeepSeek V4.1 Flash — رخيص جدًا", 0.30, 1.20, false),
)

const val TOKEN_URL = "https://github.com/settings/tokens/new?scopes=repo,workflow&description=Stickman%20Studio%20app"

private val COLORS = listOf("#FFD400" to "ذهبي (الافتراضي)", "#FFFFFF" to "أبيض", "#FF3B30" to "أحمر", "#3DDC84" to "أخضر")

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.checkHealth() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(vm.backupJson().toByteArray()) } }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()?.let { vm.restore(it) }
    }
    val list = rememberParallaxList()
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("الإعدادات", style = MaterialTheme.typography.headlineSmall) }
        vm.busy?.let { b -> item { Busy(b, vm.progress) } }

        item {
            val h = vm.health
            val bad = listOf("github", "openrouter").count { h[it] == -1 }
            Section("الحساب والاتصالات", "key", if (bad > 0) "$bad اتصال يحتاج انتباهك" else "GitHub وCoarena وGemini وOpenRouter", startOpen = bad > 0) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("github" to "GitHub", "coarena" to "Coarena", "gemini" to "Gemini", "openrouter" to "OpenRouter").forEach { (k, l) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { StatusDot(h[k] ?: 0); Gap(4); Text(l, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Gap(8)
                GhostButton("افحص الاتصالات الآن", fx = "check") { vm.checkHealth(force = true) }
                Gap(14)
                Sub("حساب GitHub", "لا شيء يعمل بدونه: يشغّل السحابة ويحفظ الملفات.", h["github"] ?: 0)
                Text(if (s.hasGithubToken) "متصل: ${s.githubLogin} · ${s.owner}/${s.repo}" else "غير متصل",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(6)
                KeyField("ghp_…", "تحقق واحفظ") { t, done -> vm.saveGithubToken(t, done) }
                Gap(6)
                GhostButton("أنشئ رمزًا جديدًا", icon = Icons.AutoMirrored.Rounded.OpenInNew) { openUrl(ctx, TOKEN_URL) }
                Gap(6)
                var owner by rememberSaveable { mutableStateOf(s.owner) }
                var repo by rememberSaveable { mutableStateOf(s.repo) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(owner, { owner = it.trim() }, Modifier.weight(1f), label = { Text("الحساب") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(repo, { repo = it.trim() }, Modifier.weight(1f), label = { Text("المستودع") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                }
                if (owner != s.owner || repo != s.repo) { Gap(6); GhostButton("حفظ المستودع") { vm.update { it.copy(owner = owner, repo = repo) } } }
                Gap(16)
                Sub("Coarena", "يكتب السيناريو ويراجع الصور بعينه، بـ Claude حين يكون متاحًا، وإلا Gemini 4 Argon للسيناريو (الأجود) وGPT-6.1 Terra لبقية الخطوات (الأرخص والأسرع). 5 دولارات مجانًا كل يوم على مفتاحك.", h["coarena"] ?: 0)
                vm.coarenaLeft?.let { left -> ProviderBar("المتبقي اليوم", "sparkles", left, (vm.coarenaUsed ?: 0.0) + left, "%.2f$".format(Locale.US, left), if (left > 1.5) 1 else 0) }
                Text(when { s.hasCoarenaKey -> "محفوظ في أسرار GitHub، ومشفّر على هاتفك لعرض الرصيد"
                    s.coarenaSynced -> "محفوظ في أسرار GitHub. الصقه مرة أخرى ليظهر رصيده هنا"
                    else -> "اختياري. الصق مفتاحًا لإضافته أو استبداله." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(6)
                KeyField("coa_…", "تحقق واحفظ") { k, done -> vm.saveCoarenaKey(k, done) }
                Gap(6)
                GhostButton("مفاتيحي في Coarena", icon = Icons.AutoMirrored.Rounded.OpenInNew) { openUrl(ctx, "https://coarena.ai/account/api") }
                Gap(16)
                Sub("Gemini (مشروع مفعّل الدفع)", "للخطوات البسيطة، ويتولى كل شيء حين يقارب رصيد Coarena النفاد. تكلفته تُحسب بالسنتات في كل فيديو.", h["gemini"] ?: 0)
                Text(if (s.geminiSynced) "محفوظ في أسرار GitHub (لا يُحفظ على الهاتف)" else "اختياري. الصق مفتاح المشروع المدفوع لتفادي الحصة الصغيرة.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(6)
                KeyField("AIza…", "تحقق واحفظ") { k, done -> vm.saveGeminiKey(k, done) }
                Gap(6)
                GhostButton("مفاتيحي في AI Studio", icon = Icons.AutoMirrored.Rounded.OpenInNew) { openUrl(ctx, "https://aistudio.google.com/apikey") }
                Gap(16)
                Sub("OpenRouter (اختياري)", "احتياط أخير فقط إن تعذّر Coarena وGemini، بالنماذج المجانية. يمكن العمل بدونه.", h["openrouter"] ?: 0)
                vm.credit?.let { c ->
                    Text(if (c.remaining != null) "الرصيد: %.2f$ من %.2f$".format(Locale.US, c.remaining, c.limit ?: 0.0) else "المستعمل: %.2f$".format(Locale.US, c.usage),
                        style = MaterialTheme.typography.titleSmall)
                }
                Gap(6)
                KeyField("sk-or-v1-…", "تحقق واحفظ") { k, done -> vm.saveOpenRouterKey(k, done) }
                Gap(6)
                GhostButton("مفاتيحي في OpenRouter", icon = Icons.AutoMirrored.Rounded.OpenInNew) { openUrl(ctx, "https://openrouter.ai/settings/keys") }
            }
        }

        item {
            Section("النموذج والإنفاق", "robot", modelChoice(s.model)?.title ?: s.model.substringAfter('/')) {
                // 1.2.9: a summary card; the four-group sheet (ui/ModelSheet.kt) opens on tap
                var sheet by remember { mutableStateOf(false) }
                ModelSummary(vm, s.model) { sheet = true }
                if (sheet) ModelSheet(vm, s.model, { m ->
                    vm.update { it.copy(model = m) }
                    val c = modelChoice(m)
                    vm.notify("من الآن: " + (c?.title ?: m.substringAfter('/')) + (c?.perVideo?.takeIf { it > 0 }?.let { " · نحو %.2f$ للفيديو".format(Locale.US, it) } ?: ""))
                }) { sheet = false }
                Gap(10)
                var budget by remember(s.budget) { mutableFloatStateOf(s.budget) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("حد الإنفاق لكل خطوة: %.2f$".format(Locale.US, budget), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Help("حد الإنفاق", "أقصى ما تصرفه خطوة واحدة (السيناريو مثلًا) على نموذج مدفوع. حين يبلغه، تكمل الخطوة بالمزوّد التالي الأرخص بدل أن تتوقف.")
                }
                Slider(budget, { budget = it }, valueRange = 0.3f..10f, steps = 96, onValueChangeFinished = { vm.update { it.copy(budget = budget) } })
                // 1.2.5: from the real full cost of the last videos and the balance read from Coarena itself, as a range
                Text(ma.stickman.studio.data.Timing.videosLeft(vm.local, vm.coarenaLeft,
                    vm.result?.let { r -> r.totalCost?.let { (r.slug.ifBlank { r.title }) to it } }),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(10)
                TimeHonesty(vm)
                // 1.3.0: what a running step does when today's Coarena money runs out
                Gap(12)
                Text("عند نفاد رصيد Coarena أثناء العمل", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("توقّف وأكمل بعد تجدد الرصيد", selected = s.onEmpty == "pause") { vm.setOnEmpty("pause") }
                    Chip("أكمل بالنماذج المجانية", selected = s.onEmpty == "free") { vm.setOnEmpty("free") }
                }
                Text(if (s.onEmpty == "pause") "الموصى به: يحفظ العمل ويتوقف، ثم يكمل بالجودة نفسها بعد منتصف الليل (بتوقيت غرينتش)."
                     else "يكمل في الحين، لكن النماذج المجانية أبطأ بكثير وأضعف جودة.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.onEmpty == "pause") Toggle("استأنف وحده بعد تجدد الرصيد", "يكمل الخطوة المتوقفة تلقائيًا بعد منتصف الليل، ويرسل إشعارًا.", s.autoResume) { v -> vm.update { it.copy(autoResume = v) } }
            }
        }

        item {
            Section("الصوت والإنتاج", "mic", if (s.voice == "female") "صوت أنثوي" else "Enzo · " + (if (s.previewFirst) "مع معاينة" else "دون معاينة")) {
                Text("الصوت الافتراضي", style = MaterialTheme.typography.labelLarge)
                Gap(4)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("male" to "Enzo (رجالي)", "female" to "أنثوي").forEachIndexed { i, (k, l) ->
                        SegmentedButton(s.voice == k, { vm.update { it.copy(voice = k) } }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                    }
                }
                Toggle("معاينة اللقطات قبل التصيير", "تراجع صورة من كل لقطة وتوافق قبل التصيير. خطوة إضافية منك، توفّر تصييرًا حين يلزم تعديل.", s.previewFirst) { v -> vm.update { it.copy(previewFirst = v) } }
                Toggle("حفظ الفيديو في المعرض تلقائيًا", "في Movies/ستوديو المحتوى فور تنزيله.", s.autoSaveGallery) { v -> vm.update { it.copy(autoSaveGallery = v) } }
                Toggle("الإشعارات", "شريط التقدّم مع زر الإلغاء، ورسالة حين تجهز كل خطوة.", s.notifications) { v -> vm.update { it.copy(notifications = v) } }
                Toggle("تذكير بأفضل وقت للنشر", "إشعار في الساعة التي تقترحها حزمة السيو.", s.remindPost) { v -> vm.update { it.copy(remindPost = v) } }
                // 1.3.4: the TikTok numbers from one screenshot, read by themselves
                val shots = rememberShotsPermission(vm)
                val ok = s.autoStats && shotsGranted(ctx)
                Toggle("قراءة أرقام تيك توك وحدها", "خذ لقطة شاشة لتحليلات فيديوك في تيك توك فقط: يقرؤها التطبيق ويعرف الفيديو ويعلّم المدرّب. " +
                    "لا يفتح إلا لقطات تيك توك، ولا يفتحها إلا حين ينتظر فيديو منشور أرقامه." +
                    (if (s.autoStats && !ok) " يحتاج إذن الصور: اضغط المفتاح واختر «السماح بالكل»." else ""), ok) { v ->
                    vm.update { it.copy(autoStats = v) }
                    if (v && !shotsGranted(ctx)) shots() else vm.armShots()
                }
            }
        }

        item {
            Section("إعدادات الفيديو", "film", "${s.fps} إطارًا · " + (if (s.music) "بموسيقى" else "بلا موسيقى")) {
                Text("الإطارات في الثانية", style = MaterialTheme.typography.labelLarge)
                Gap(4)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(30 to "30 (أسرع تصييرًا)", 60 to "60 (أنعم)").forEachIndexed { i, (k, l) ->
                        SegmentedButton(s.fps == k, { vm.update { it.copy(fps = k) } }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                    }
                }
                Toggle("موسيقى الخلفية", "تُولَّد محليًا وتنقطع قبل كل كشف كبير.", s.music) { v -> vm.update { it.copy(music = v) } }
                Gap(10)
                Text("لون الكلمات المفتاحية", style = MaterialTheme.typography.labelLarge)
                Gap(6)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    COLORS.forEach { (hex, name) ->
                        val c = Color(android.graphics.Color.parseColor(hex))
                        val on = s.keywordColor.equals(hex, true)
                        Row(Modifier.clip(CircleShape).border(2.dp, if (on) c else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .pressable { vm.update { it.copy(keywordColor = hex) } }.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(6.dp))
                            Text(name, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Gap(10)
                var ts by remember(s.textSize) { mutableFloatStateOf(s.textSize) }
                Text("حجم النص في الفيديو: ${(ts * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(ts, { ts = it }, valueRange = 0.9f..1.3f, steps = 7, onValueChangeFinished = { vm.update { it.copy(textSize = ts) } })
                Text("تُطبَّق على كل إنتاج جديد. القيم الافتراضية مطابقة لقواعد القناة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            Section("المظهر والحركة", "palette", when (s.theme) { "light" -> "فاتح"; "system" -> "حسب الهاتف"; else -> "داكن" }) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("dark" to "داكن", "light" to "فاتح", "system" to "الهاتف").forEachIndexed { i, (k, l) ->
                        SegmentedButton(s.theme == k, { vm.update { it.copy(theme = k) } }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) }
                    }
                }
                Toggle("اهتزاز عند اللمس", "نقرة خفيفة للأزرار، واهتزاز مختلف للنجاح والخطأ.", s.haptics) { v -> vm.update { it.copy(haptics = v) } }
                Toggle("أصوات خفيفة", "صوت نقر ناعم، ونغمة للنجاح وأخرى للخطأ.", s.sounds) { v -> vm.update { it.copy(sounds = v) } }
                Toggle("ميل البطاقات مع الهاتف", "عمق ثلاثي الأبعاد ولمعة تتحرك حين تميل الهاتف.", s.tilt) { v -> vm.update { it.copy(tilt = v) } }
                Toggle("تقليل الحركة", "يوقف الحركات والانتقالات (يتبع أيضًا إعداد الهاتف).", s.reduceMotion) { v -> vm.update { it.copy(reduceMotion = v) } }
                Toggle("تباين عالٍ", "نصوص أوضح وحدود أقوى.", s.highContrast) { v -> vm.update { it.copy(highContrast = v) } }
                Gap(8)
                var fsc by remember(s.fontScale) { mutableFloatStateOf(s.fontScale) }
                Text("حجم الخط في التطبيق: ${(fsc * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(fsc, { fsc = it }, valueRange = 0.85f..1.4f, steps = 10, onValueChangeFinished = { vm.update { it.copy(fontScale = fsc) } })
            }
        }

        item {
            Section("الأمان والنسخ الاحتياطي", "shield", if (s.lock) "مقفل بالبصمة" else "") {
                Toggle("قفل التطبيق بالبصمة", "بالبصمة أو الوجه أو رمز الهاتف، عند الفتح وبعد دقيقتين في الخلفية.", s.lock) { v -> vm.update { it.copy(lock = v) } }
                Gap(10)
                Text("النسخة الاحتياطية تحفظ الإعدادات والسجل والمواضيع المحفوظة في ملف. المفاتيح لا تُحفظ فيها أبدًا.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(8)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("صدّر نسخة", fx = "package") { export.launch("stickman-studio-backup.json") }
                    GhostButton("استعد نسخة", fx = "check") { import.launch(arrayOf("application/json", "*/*")) }
                }
            }
        }

        item {
            Section("التطبيق", "package", "TaleSpark ${BuildConfigInfo.VERSION}" + (vm.release?.let { " · تحديث ${it.version} جاهز" } ?: "")) {
                vm.release?.let { r ->
                    Text("الجديد في ${r.version}:", style = MaterialTheme.typography.titleSmall)
                    Text(r.notes.take(600), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(8)
                    GoldButton("نزّل وثبّت ${r.version} (%.1f ميغابايت)".format(Locale.US, r.size / 1e6), fx = "package") { vm.installUpdate() }
                    Gap(8)
                } ?: GhostButton("ابحث عن تحديث", fx = "magnifier") { vm.checkUpdate() }
                Gap(8)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("ما الجديد", fx = "sparkles") { vm.update { it.copy(lastSeenVersion = "") } }
                    GhostButton("الجولة التعريفية", fx = "bulb") { vm.update { it.copy(toured = false) } }
                }
                Gap(8)
                GhostButton("حذف الفيديوهات من ذاكرة التطبيق", fx = "wastebasket") { vm.clearVideos() }
                Gap(8)
                GhostButton("عمليات GitHub", icon = Icons.AutoMirrored.Rounded.OpenInNew) { openUrl(ctx, "https://github.com/${s.owner}/${s.repo}/actions") }
            }
        }
        item {
            Section("عمليات GitHub الأخيرة", "package", "الفيديو يبقى هناك 3 أيام") {
                LaunchedEffect(Unit) { vm.loadHistory() }
                if (vm.pastRuns.isEmpty()) Text("…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                vm.pastRuns.take(12).forEach { r ->
                    val slug = r.title.removePrefix("produce").trim()
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).pressable(enabled = r.ok && slug.isNotBlank()) { vm.openPast(r) }
                        .padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        FxIcon(when { !r.done -> "hourglass"; r.ok -> "film"; else -> "warning" }, 20.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(vm.localResult(slug)?.title?.ifBlank { null } ?: slug.ifBlank { "إنتاج" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(r.createdAt.take(16).replace('T', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(when { !r.done -> "يعمل"; r.ok -> "افتح"; r.conclusion == "cancelled" -> "أُلغي"; else -> "فشل" },
                            style = MaterialTheme.typography.labelMedium, color = if (r.done && !r.ok) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item { Gap(30) }
    }
}

/** A sub-title inside a section, with its status dot and one line of explanation. */
@Composable
private fun Sub(title: String, text: String, state: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); StatusDot(state)
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Gap(4)
}

@Composable
fun KeyField(hint: String, action: String, save: (String, (Boolean) -> Unit) -> Unit) {
    var v by rememberSaveable { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // Keys are latin: always left-to-right, so "sk-or-v1-…" reads correctly.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        OutlinedTextField(v, { v = it.trim() }, Modifier.fillMaxWidth(), placeholder = { Text(hint) }, singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(14.dp),
            trailingIcon = { IconButton({ show = !show }) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "أخفِ" else "أظهر") } })
    }
    Spacer(Modifier.padding(4.dp))
    GoldButton(action, enabled = v.length > 10, loading = busy) { busy = true; save(v) { ok -> busy = false; if (ok) v = "" } }
}

@Composable
private fun Toggle(label: String, help: String, value: Boolean, set: (Boolean) -> Unit) {
    val fb = LocalFeedback.current
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(value, { fb.tick(); set(it) })
    }
}

@Composable
private fun ModelPicker(vm: AppViewModel, current: String, pick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open) { if (open && vm.models.isEmpty()) vm.loadModels() }
    val all = RECOMMENDED + vm.models.filter { m -> RECOMMENDED.none { it.id == m.id } }
    val label = all.firstOrNull { it.id == current }?.name ?: current
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(label, {}, Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable), readOnly = true,
            label = { Text("النموذج") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }, shape = RoundedCornerShape(14.dp))
        ExposedDropdownMenu(open, { open = false }) {
            all.take(80).forEach { m ->
                DropdownMenuItem(text = {
                    Column {
                        Text(m.name.ifBlank { m.id }, style = MaterialTheme.typography.bodyMedium)
                        Text(if (m.free) "بلا تكلفة على OpenRouter" else "%.2f$ / %.2f$ لكل مليون".format(Locale.US, m.inPrice, m.outPrice), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }, onClick = { pick(m.id); open = false })
            }
        }
    }
}


/** 1.2.5: this week's real production times and how well the app's own time estimates matched GitHub's clock. */
@Composable
private fun TimeHonesty(vm: AppViewModel) {
    val w = remember(vm.timingsVersion) { ma.stickman.studio.data.Timing.week(vm.local) }
    val acc = remember(vm.timingsVersion) { ma.stickman.studio.data.Timing.accuracy(vm.local) }
    val dur = ma.stickman.studio.data.Eta.Companion::dur
    Text("الوقت هذا الأسبوع", style = MaterialTheme.typography.titleSmall)
    val lines = buildList {
        if (w.videos == 0) add("لا فيديو مكتمل مقاس هذا الأسبوع بعد.")
        else if (w.avgTotalS == null) add("${w.videos} فيديو، لكن خطواته الأولى جرت قبل 1.2.5 فلم تُقس كاملة. تظهر المتوسطات من الفيديو القادم.")
        else {
            add("${w.videos} فيديو. متوسط الفيديو من الفكرة إلى النهاية: ${w.avgTotalS?.let(dur) ?: "—"}")
            add("منه وقت السحابة ${w.avgMachineS?.let(dur) ?: "—"} ووقتك ${w.avgUserS?.let(dur) ?: "—"}")
            w.slowest?.let { add("أبطأ خطوة: $it") }
        }
        add(acc?.let { (e, n) -> "دقة تقدير الوقت: الفرق المتوسط ${e}% (آخر $n تشغيلات)" } ?: "دقة تقدير الوقت: تظهر بعد تشغيلين.")
    }
    lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
