@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ma.stickman.studio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ma.stickman.studio.data.Stage
import ma.stickman.studio.ui.theme.Bad
import ma.stickman.studio.ui.theme.Gold
import ma.stickman.studio.ui.theme.Good
import ma.stickman.studio.ui.theme.LocalMotion
import ma.stickman.studio.ui.theme.Warn
import ma.stickman.studio.ui.theme.Violet
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import ma.stickman.studio.widget.StatusWidget
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StudioScreen(vm: AppViewModel, toast: (String) -> Unit) {
    val job by vm.job.collectAsState()
    val settings by vm.settings.collectAsState()
    val motion = LocalMotion.current
    // 1.2.0: the home of the app is the conversation; a running or finished video opens over it, and a banner in
    // the conversation brings it back (vm.chatFocus).
    val chat = job.stage == Stage.IDLE || vm.chatFocus
    AnimatedContent(
        targetState = if (chat) null else job.stage, label = "stage",
        transitionSpec = {
            (fadeIn(tween((220 * motion).toInt())) + scaleIn(tween((260 * motion).toInt()), initialScale = 0.97f))
                .togetherWith(fadeOut(tween((120 * motion).toInt())))
        },
    ) { stage ->
        if (stage == null) ChatHome(vm, toast) else Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(CircleShape).pressable { vm.chatFocus = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("المحادثة", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Text(job.title.ifBlank { "فيديو جديد" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Box(Modifier.weight(1f)) {
                when (stage) {
                    Stage.IDLE -> ChatHome(vm, toast)
                    Stage.TOPICS_RUNNING -> Running(vm, "يبحث في الأخبار ويقترح 10 مواضيع", "عادة 3 إلى 6 دقائق")
                    Stage.TOPICS_READY -> Topics(vm)
                    Stage.SCRIPT_RUNNING -> Running(vm, "يكتب السيناريو ويتحقق من المصادر ويجلب الصور الحقيقية",
                        when (settings.model) { "auto", "coarena" -> "عادة 8 إلى 15 دقيقة"; "gemini" -> "عادة 10 إلى 15 دقيقة"; "free" -> "عادة 20 إلى 30 دقيقة"; else -> "عادة 6 إلى 12 دقيقة" })
                    Stage.SCRIPT_READY, Stage.UPLOADING -> Prepare(vm, toast)
                    Stage.PREVIEWING -> Running(vm, "يجهّز الصوت والصور ويصنع معاينة اللقطات", "عادة 4 إلى 7 دقائق")
                    Stage.PREVIEW_READY -> PreviewView(vm)
                    Stage.PRODUCING -> Running(vm, "يجهّز ويصيّر الفيديو ويمرّره بالبوابات الخمس", "عادة 8 إلى 15 دقيقة")
                    Stage.DONE -> ResultView(vm, toast)
                    Stage.FAILED -> Failed(vm)
                    Stage.PAUSED -> PausedView(vm)
                }
            }
        }
    }
}

/** What stays useful on the home besides the conversation (1.2.0): videos to publish, files waiting, and a quiet,
 *  folded «لوحة الاستوديو» with the night production, the balances, the last video and the week. */
@Composable
fun StudioPanel(vm: AppViewModel) {
    val videos by vm.local.videos.collectAsState()
    val saved by vm.local.saved.collectAsState()
    val pub by vm.local.publish.collectAsState()
    var open by rememberSaveable { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        vm.local.draft?.let { d ->
            Card(accent = Warn) { Row(verticalAlignment = Alignment.CenterVertically) {
                FxIcon("hourglass", 22.dp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("ملفات تنتظر الإنترنت", style = MaterialTheme.typography.titleSmall)
                    Text("«${d.title}»: تُرسل وحدها عند عودة الاتصال.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
        }
        if (pub.isNotEmpty()) ReadyToPublish(vm, pub)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable { open = !open }.padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FxIcon("clapper", 20.dp); Spacer(Modifier.width(10.dp))
            Text("لوحة الاستوديو", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (saved.isNotEmpty()) Text("${saved.size} محفوظ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("تابع آخر سيناريو", fx = "scroll") { vm.chatFocus = false; vm.resumeReady() }
                    if (saved.isNotEmpty()) GhostButton("المحفوظة (${saved.size})", fx = "bookmark") { showSaved = true }
                }
                WeekPlan(vm, (videos.map { it.slug to it.date.take(10) } + vm.log.map { it.slug to it.date }).distinctBy { it.first }.map { it.second })
                NightCard(vm)
                AiCard(vm)
                CloudCard(vm)
                videos.firstOrNull()?.let { v ->
                    Card(onClick = { vm.openLocal(v.slug) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Thumb(vm.thumb(v.slug), Modifier.width(52.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("آخر فيديو", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                Text(statLine(v), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                val (n, cost, mins) = remember(videos) { vm.local.week() }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("$n", "فيديو هذا الأسبوع", "film", Modifier.weight(1f))
                    StatTile("%.2f$".format(Locale.US, cost), "التكلفة", "coin", Modifier.weight(1f))
                    StatTile(mins?.let { "${it.toInt()}" } ?: "–", "دقيقة لكل فيديو", "stopwatch", Modifier.weight(1f))
                }
            }
        }
    }
    if (showSaved) ModalBottomSheet(onDismissRequest = { showSaved = false }) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("مواضيع محفوظة", style = MaterialTheme.typography.headlineSmall) }
            saved.forEach { t -> item {
                Card { Column {
                    Text(t.title, style = MaterialTheme.typography.titleMedium)
                    Text("«${t.hook}»", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    Gap(8)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("اطلبه الآن", fx = "rocket") { showSaved = false; vm.chatFocus = false; vm.useSaved(t) }
                        GhostButton("أزله", fx = "wastebasket") { vm.toggleSaved(t) }
                    }
                } }
            } }
            item { Gap(30) }
        }
    }
}

@Composable
private fun Greeting(bestTime: String?) {
    val zone = ZoneId.of("Africa/Casablanca")
    val h = LocalTime.now(zone).hour
    val hello = when (h) { in 5..11 -> "صباح الخير"; in 12..17 -> "نهارك سعيد"; else -> "مساء الخير" }
    val d = LocalDate.now(zone)
    val day = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("ar"))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.stagger(0)) {
        Column(Modifier.weight(1f)) {
            Text(hello, style = MaterialTheme.typography.headlineSmall)
            Text("$day ${d.dayOfMonth}/${d.monthValue}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("أفضل وقت للنشر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(bestTime?.takeIf { it.isNotBlank() }?.take(28) ?: "20:00 إلى 22:00", style = MaterialTheme.typography.labelLarge, color = Gold)
        }
    }
}

/** The whole road of a video in four stations; the home screen is always at the first. */
@Composable
fun Pipeline(current: Int = 0) {
    val steps = listOf("sparkles" to "المواضيع", "scroll" to "السيناريو", "mic" to "الصوت والصور", "film" to "الإنتاج")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, (fx, l) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(if (i <= current) Gold.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center) { FxIcon(fx, 30.dp) }
                Text(l, style = MaterialTheme.typography.labelSmall, color = if (i == current) Gold else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (i < steps.lastIndex) Box(Modifier.width(14.dp).height(2.dp).background(if (i < current) Gold else MaterialTheme.colorScheme.outline))
        }
    }
}

fun statLine(v: ma.stickman.studio.data.VideoStat): String = listOfNotNull(
    v.matchRate?.let { "${(it * 100).toInt()}% تزامن" },
    v.durationS.takeIf { it > 0 }?.let { "%d:%02d".format(it.toInt() / 60, it.toInt() % 60) },
    v.costUsd?.takeIf { it > 0 }?.let { "%.2f$".format(Locale.US, it) },
    v.views.takeIf { it > 0 }?.let { "$it مشاهدة" },
).joinToString(" · ")

/** The thumbnail of a video (9:16), or a 3D film icon when the phone does not have it. */
@Composable
fun Thumb(f: java.io.File?, modifier: Modifier = Modifier) {
    val bmp = rememberFileBitmap(f?.path, 300)
    Box(modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
        if (bmp != null) androidx.compose.foundation.Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else FxIcon("film", 18.dp)
    }
}

// ---------------- failed ----------------
@Composable
private fun Failed(vm: AppViewModel) {
    val job by vm.job.collectAsState()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(140.dp).glow(Bad), contentAlignment = Alignment.Center) { FxIcon("warning", 84.dp, Modifier.tilt3d(1.4f, false)) }
        Gap(12)
        Text("لم يكتمل التشغيل", style = MaterialTheme.typography.headlineSmall)
        Gap(6)
        Text(job.error.ifBlank { "حدث خطأ على GitHub." }, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap(8)
        Text(failHint(job.error), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap(22)
        val resumable = job.failedFrom == ma.stickman.studio.data.Stage.PRODUCING || job.failedFrom == ma.stickman.studio.data.Stage.PREVIEWING
        GoldButton(if (resumable) "أكمل من حيث توقف" else "أعد المحاولة", icon = Icons.Rounded.Refresh) { vm.retry() }
        if (resumable) {
            Gap(4)
            Text("بلا إعادة رفع: الصوت والصور ما زالا على GitHub.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Gap(10)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (resumable) GhostButton("أرسل ملفات أخرى", fx = "mic") { vm.resend() }
            vm.run?.url?.let { GhostButton("السبب على GitHub", icon = Icons.AutoMirrored.Rounded.OpenInNew) { openUrl(ctx, it) } }
            GhostButton("تابع آخر سيناريو جاهز", fx = "scroll") { vm.resumeReady() }
            GhostButton("ابدأ من جديد", fx = "sparkles") { vm.reset() }
        }
    }
}

/** What usually fixes a failure at that step, in one sentence. */
private fun failHint(e: String): String = when {
    e.contains("أُلغي") -> "أنت ألغيته: «أعد المحاولة» يبدأه من جديد."
    e.contains("المفتاح") -> "راجع المفاتيح في الإعدادات ← الحساب والاتصالات، ثم أعد المحاولة."
    e.contains("الذكاء") -> "غالبًا انتهى الرصيد أو انشغل النموذج: أعد المحاولة، فالانتقال إلى المزوّد التالي تلقائي."
    e.contains("فحص") || e.contains("التحقق") -> "لم ينجح أحد الفحوص: إعادة المحاولة تعطي الذكاء الاصطناعي فرصة لإصلاحه."
    e.contains("التصيير") || e.contains("الدمج") -> "خلل في التصيير: أعد المحاولة بالملفات نفسها."
    else -> "أعد المحاولة؛ وإن تكرر الخطأ فافتح السبب على GitHub."
}

@Composable
fun VideoBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(24.dp)).background(Color.Black), contentAlignment = Alignment.Center) { content() }
}

/** A local image at screen width (large contact sheets are decoded smaller), tap to open it full screen. */
@Composable
fun ImageFile(f: java.io.File, onClick: () -> Unit) {
    val bmp = rememberFileBitmap(f.path, 1100)
    if (bmp == null) return
    androidx.compose.foundation.Image(bmp, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).pressable(onClick = onClick),
        contentScale = ContentScale.FillWidth)
}

/** Status line of the widget, reused here so the two always say the same. */
fun stageLine(stage: Stage, title: String) = StatusWidget.line(stage, title).first


/** Night production (1.1.3): on/off, the ready time, how many videos, the days, the alarm; and last night. */
@Composable
private fun NightCard(vm: AppViewModel) {
    LaunchedEffect(Unit) { if (!vm.nightLoaded) vm.loadNight() }
    val saved = vm.night
    var p by remember(saved) { mutableStateOf(saved) }
    val changed = p != saved
    Card(accent = if (p.enabled) Violet else null) { Column {
        SectionTitle("الإنتاج الليلي", fx = "rocket", trailing = {
            androidx.compose.material3.Switch(p.enabled, { p = p.copy(enabled = it) })
        })
        Text(if (p.enabled) "وأنت نائم: يختار أفضل المواضيع، ويكتب، ويولّد صوت Enzo، وينتج. تجد الفيديوهات في معرضك عند الاستيقاظ."
             else "شغّله لتجد فيديوهات جاهزة كل صباح، دون أي خطوة منك.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (p.enabled) {
            Gap(10)
            Text("جاهزة الساعة", style = MaterialTheme.typography.labelLarge)
            Gap(4)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 6, 7, 8, 9, 10).forEach { h -> item { Chip("$h:00", p.hour == h && p.minute == 0) { p = p.copy(hour = h, minute = 0) } } }
                item { Chip("%d:30".format(p.hour), p.minute == 30) { p = p.copy(minute = if (p.minute == 30) 0 else 30) } }
            }
            Gap(10)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("عدد الفيديوهات", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                (1..3).forEach { n -> Spacer(Modifier.width(6.dp)); Chip("$n", p.count == n) { p = p.copy(count = n) } }
            }
            Text(if (vm.minutesFree) "المستودع عام: دقائق GitHub مجانية، فاختر ما تشاء."
                 else when (p.count) { 1 -> "نحو 30 دقيقة من GitHub يوميًا (نحو 900 في الشهر)."
                     2 -> "نحو 60 دقيقة يوميًا: قريب من حد 2000 في الشهر."
                     else -> "نحو 90 دقيقة يوميًا: يتجاوز حد 2000 في الشهر، فاجعله لأيام قليلة." },
                style = MaterialTheme.typography.bodySmall, color = if (p.count == 3 && !vm.minutesFree) Warn else MaterialTheme.colorScheme.onSurfaceVariant)
            Gap(10)
            Text("الأيام", style = MaterialTheme.typography.labelLarge)
            Gap(4)
            val names = listOf(1 to "ن", 2 to "ث", 3 to "ر", 4 to "خ", 5 to "ج", 6 to "س", 7 to "ح")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                names.forEach { (d, l) -> Chip(l, d in p.days) { p = p.copy(days = if (d in p.days && p.days.size > 1) p.days - d else p.days + d) } }
            }
            Gap(6)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("صوت منبّه عند الجاهزية", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(p.sound, { p = p.copy(sound = it) })
            }
            Text("يبدأ في السحابة قبل الموعد بنحو ${p.leadMinutes} دقيقة. الصور: حقيقية وFluent وخرائط فقط. أي فيديو لا يجتاز بوابات الجودة لا يُسلَّم.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (changed || !vm.nightLoaded) {
            Gap(10)
            GoldButton(if (p.enabled) "احفظ الإنتاج الليلي" else "احفظ", fx = "check", enabled = vm.busy == null && vm.nightLoaded) { vm.saveNight(p) }
        }
        vm.nightLast?.let { l ->
            Gap(10)
            Text("آخر ليلة (${l.date}):", style = MaterialTheme.typography.labelLarge)
            l.videos.forEach { v -> Text((if (v.ok) "✅ " else "❌ ") + v.title, style = MaterialTheme.typography.bodySmall) }
            if (l.videos.isEmpty()) Text("لم يجد موضوعًا مناسبًا للإنتاج دون مراجعة.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Gap(8)
        GhostButton("جرّب ليلة الآن (فيديو واحد)", fx = "sparkles", enabled = vm.busy == null) { vm.nightNow(1) }
    } }
}

/** Night videos already in the gallery, waiting for TikTok: one tap posts (caption copied), one says it is done. */
@Composable
private fun ReadyToPublish(vm: AppViewModel, slugs: List<String>) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Card(accent = Good) { Column {
        SectionTitle("جاهز للنشر (${slugs.size})", fx = "party")
        slugs.forEach { slug ->
            val r = remember(slug) { vm.localResult(slug) }
            val f = vm.videoFile(slug)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Thumb(vm.thumb(slug), Modifier.width(56.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r?.title ?: slug, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    r?.seo?.bestTime?.takeIf { it.isNotBlank() }?.let {
                        Text("أفضل وقت: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (f != null) TextButton({
                            val cap = r?.seo?.description.orEmpty()
                            shareTikTok(ctx, f, cap)
                        }) { Text("انشر على تيك توك") }
                        else TextButton({ vm.openLocal(slug) }) { Text("افتح") }
                        TextButton({ vm.publishedNight(slug) }) { Text("نشرته ✓") }
                    }
                }
            }
        }
    } }
}
