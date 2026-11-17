@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ma.stickman.studio.ui

import androidx.compose.foundation.lazy.LazyColumn
import ma.stickman.studio.ui.theme.ReplyStyle
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ma.stickman.studio.data.VideoStat
import ma.stickman.studio.ui.theme.Bad
import ma.stickman.studio.ui.theme.Gold
import ma.stickman.studio.ui.theme.Good
import ma.stickman.studio.ui.theme.Sky
import ma.stickman.studio.ui.theme.Violet

@Composable
fun HistoryScreen(vm: AppViewModel, openStudio: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadHistory(); vm.loadCoach() }
    val videos by vm.local.videos.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var pickFor by remember { mutableStateOf<String?>(null) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        val slug = pickFor; pickFor = null
        if (uri != null && slug != null) vm.statsFromImage(slug, uri)
    }
    fun askStats(slug: String) {
        pickFor = slug
        picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    // asked from the chat or the reminder: open the picker for that video once
    LaunchedEffect(vm.statsRequest) { vm.statsRequest?.let { s -> vm.statsRequest = null; if (vm.local.video(s) != null) { detail = s; askStats(s) } } }

    // one list: the videos on this phone and the older ones of videos/LOG.md, never twice
    val known = videos.map { it.slug }.toSet()
    val older = vm.log.filter { it.slug !in known }.map { VideoStat(it.slug, it.title, "", it.date) }
    val everything = videos + older
    val best = videos.filter { it.views > 0 }.sortedByDescending { it.views }.take(5).map { it.slug }.toSet()
    val shown = everything.filter { v -> q.isBlank() || v.title.contains(q.trim(), true) || v.slug.contains(q.trim(), true) }
        .filter { v -> when (filter) {
            "unpublished" -> v.slug in known && v.publishedAt == 0L && !v.hasStats
            "waiting" -> v.waitingStats
            "best" -> v.slug in best
            "starred" -> v.starred
            else -> true
        } }
        .let { l -> if (filter == "best") l.sortedByDescending { it.views } else l.sortedByDescending { it.date } }
    val zone = java.time.ZoneId.of("Africa/Casablanca")
    val today = java.time.LocalDate.now(zone)
    fun group(v: VideoStat): String {
        val d = runCatching { java.time.LocalDate.parse(v.date.take(10)) }.getOrNull() ?: return "أقدم"
        return when {
            filter == "best" -> "الأفضل أداءً"
            d == today -> "اليوم"; d == today.minusDays(1) -> "أمس"
            d >= today.minusDays(7) -> "هذا الأسبوع"; d >= today.minusDays(31) -> "هذا الشهر"; else -> "أقدم"
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Text("المشاريع", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Text("${everything.size} فيديو", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Card(onClick = { vm.showCoach = true }, modifier = Modifier.padding(bottom = 10.dp)) { ProgressLine(vm.coach) } }
        vm.busy?.let { b -> item { Box(Modifier.padding(bottom = 8.dp)) { Busy(b, vm.progress) } } }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicTextField(q, { q = it }, Modifier.weight(1f), singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner -> Box { if (q.isEmpty()) Text("ابحث في فيديوهاتك", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                val waiting = videos.count { it.waitingStats }
                item { Chip("الكل", filter == "all") { filter = "all" } }
                item { Chip("ينتظر النشر", filter == "unpublished") { filter = "unpublished" } }
                item { Chip(if (waiting > 0) "ينتظر أرقامه ($waiting)" else "ينتظر أرقامه", filter == "waiting") { filter = "waiting" } }
                item { Chip("الأفضل أداءً", filter == "best") { filter = "best" } }
                item { Chip("المفضّلة", filter == "starred") { filter = "starred" } }
            }
        }
        if (shown.isEmpty()) item {
            EmptyState("folder", when {
                everything.isEmpty() -> "لا فيديوهات بعد. أول فيديو تنتجه يظهر هنا بغلافه وأرقامه."
                filter == "waiting" -> "لا فيديو ينتظر أرقامه. أحسنت!"
                filter == "starred" -> "اضغط النجمة في صفحة أي فيديو ليظهر هنا."
                else -> "لا نتيجة."
            }, if (everything.isEmpty()) "ابدأ فيديو جديدًا" else null) { openStudio() }
        }
        var last = ""
        shown.forEach { v ->
            val g = group(v)
            if (g != last) {
                last = g
                item(key = "g$g") {
                    Text(g, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp))
                }
            }
            item(key = "v" + v.slug) { ProjectRow(vm, v, onPhone = v.slug in known) { if (v.slug in known) detail = v.slug else vm.likeThis(v.title) } }
        }
    }
    detail?.let { slug -> vm.local.video(slug)?.let { v -> ProjectSheet(vm, v, { detail = null }) { askStats(slug) } } }
}

/** One quiet line per video: small cover, title, one grey status line, a star. */
@Composable
private fun ProjectRow(vm: AppViewModel, v: VideoStat, onPhone: Boolean, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).pressable(onClick = open).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (onPhone) Thumb(vm.thumb(v.slug), Modifier.width(40.dp))
        else Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            FxIcon("film", 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(status(v, onPhone), style = MaterialTheme.typography.labelSmall,
                color = if (v.waitingStats) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        v.score?.let { Text("${it.toInt()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp)) }
        if (onPhone) IconButton({ vm.toggleStar(v.slug) }, Modifier.size(36.dp)) {
            Icon(if (v.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (v.starred) "أزل من المفضّلة" else "أضف إلى المفضّلة",
                Modifier.size(20.dp), tint = if (v.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun status(v: VideoStat, onPhone: Boolean): String = when {
    !onPhone -> "${v.date} · ليس على هذا الهاتف · المس لـ«اصنع مثله»"
    v.hasStats -> listOfNotNull("منشور", "${compact(v.views)} مشاهدة", v.completionPct?.let { "إكمال ${it.toInt()}%" },
        v.avgWatchS?.let { "متوسط ${it.toInt()} ث" }).joinToString(" · ")
    v.waitingStats -> "منشور · ينتظر أرقامه"
    else -> listOfNotNull("غير منشور", v.durationS.takeIf { it > 0 }?.let { "%d:%02d".format(it.toInt() / 60, it.toInt() % 60) },
        v.matchRate?.let { "تزامن ${(it * 100).toInt()}%" }).joinToString(" · ")
}

private fun compact(n: Long) = when { n >= 1_000_000 -> "%.1fM".format(n / 1e6); n >= 1000 -> "%.1fK".format(n / 1e3); else -> "$n" }

/** The page of one video, like a project: the video, its numbers, the coach's note, and what to do next. */
@Composable
private fun ProjectSheet(vm: AppViewModel, v: VideoStat, dismiss: () -> Unit, askStats: () -> Unit) {
    var typing by remember { mutableStateOf(false) }
    val c = vm.coach?.scores?.firstOrNull { it.slug == v.slug }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.background,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Thumb(vm.thumb(v.slug), Modifier.width(72.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.title, style = MaterialTheme.typography.headlineSmall)
                        Text(listOfNotNull(v.date, v.durationS.takeIf { it > 0 }?.let { "%d:%02d".format(it.toInt() / 60, it.toInt() % 60) },
                            v.costUsd?.let { "%.2f$".format(java.util.Locale.US, it) }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton({ vm.toggleStar(v.slug) }) {
                        Icon(if (v.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, null,
                            tint = if (v.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("شاهد", fx = "film") { dismiss(); vm.openLocal(v.slug) }
                    GhostButton("اصنع مثله", fx = "sparkles") { dismiss(); vm.chatFocus = false; vm.likeThis(v.title) }
                }
            }
            item {
                Card { Column {
                    SectionTitle("أرقام تيك توك", fx = "chart")
                    if (v.hasStats) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(compact(v.views), "مشاهدة", "eyes", Modifier.weight(1f))
                            StatTile(v.completionPct?.let { "${it.toInt()}%" } ?: "–", "إكمال", "check", Modifier.weight(1f))
                            StatTile(v.avgWatchS?.let { "${it.toInt()}" } ?: "–", "ث متوسط", "stopwatch", Modifier.weight(1f))
                        }
                        Gap(6)
                        Text("${v.likes} إعجاب · ${v.comments} تعليق · ${v.shares} مشاركة · ${v.saves} حفظ", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Gap(10)
                    } else {
                        Text(if (v.publishedAt > 0) "نُشر؟ أرسل لقطة شاشة لصفحة «الإحصاءات» في تيك توك بعد يومين، فيقرأ Claude الأرقام وحده."
                            else "بعد النشر بيومين أرسل لقطة شاشة لإحصاءاته: منها يتعلّم المدرّب.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Gap(10)
                    }
                    GoldButton(if (v.hasStats) "حدّث الأرقام بلقطة شاشة" else "أضف الأرقام بلقطة شاشة", fx = "picture", loading = vm.busy != null) { askStats() }
                    Gap(6)
                    GhostButton("اكتبها بيدي", fx = "scroll") { typing = true }
                } }
            }
            item {
                Card { Column {
                    SectionTitle("ملاحظة المدرّب", fx = "coach", trailing = { (c?.score ?: v.score)?.let { Pill("${it.toInt()}/100") } })
                    Text(when {
                        c != null && c.weakest.isNotEmpty() -> "أضعف ما فيه: " + c.weakest.joinToString("، ") + ". سيُعالَج في الفيديو القادم."
                        c != null -> "قوي في كل المعايير."
                        else -> "يُقيَّم هذا الفيديو بعد مراجعة المدرّب القادمة."
                    }, style = ReplyStyle.copy(color = MaterialTheme.colorScheme.onSurface))
                } }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("أزله من القائمة", fx = "wastebasket") { dismiss(); vm.forget(v.slug) }
                }
            }
        }
    }
    if (typing) PerformanceDialog(v, { typing = false }) { j -> vm.saveStats(v.slug, j); typing = false }
}

/** Typing the numbers by hand (when there is no screenshot). */
@Composable
private fun PerformanceDialog(v: VideoStat, dismiss: () -> Unit, save: (org.json.JSONObject) -> Unit) {
    val fields = listOf("views" to "المشاهدات", "completion_pct" to "نسبة الإكمال %", "avg_watch_s" to "متوسط المشاهدة (ث)",
        "likes" to "الإعجابات", "comments" to "التعليقات", "shares" to "المشاركات", "saves" to "الحفظ")
    val values = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
        put("views", v.views.takeIf { it > 0 }?.toString() ?: ""); put("likes", v.likes.takeIf { it > 0 }?.toString() ?: "")
        put("comments", v.comments.takeIf { it > 0 }?.toString() ?: ""); put("shares", v.shares.takeIf { it > 0 }?.toString() ?: "")
        put("saves", v.saves.takeIf { it > 0 }?.toString() ?: ""); put("avg_watch_s", v.avgWatchS?.toString() ?: ""); put("completion_pct", v.completionPct?.toString() ?: "")
    } }
    AlertDialog(onDismissRequest = dismiss, title = { Text("أرقام «${v.title}»") }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.forEach { (k, l) -> item {
                OutlinedTextField(values[k] ?: "", { values[k] = it.filter { ch -> ch.isDigit() || ch == '.' }.take(12) }, Modifier.fillMaxWidth(),
                    label = { Text(l) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(12.dp))
            } }
        }
    }, confirmButton = { TextButton({
        val j = org.json.JSONObject()
        fields.forEach { (k, _) -> values[k]?.toDoubleOrNull()?.let { j.put(k, if (k == "avg_watch_s" || k == "completion_pct") it else it.toLong()) } }
        save(j)
    }) { Text("احفظ") } }, dismissButton = { TextButton(dismiss) { Text("إلغاء") } })
}

/** Which axis works best: average views per axis (or the number of videos before any views are typed). */
@Composable
fun AxisChart(videos: List<VideoStat>) {

    if (videos.isEmpty()) return
    val axes = listOf(Triple("history", "تاريخ وسياسة", Gold), Triple("event", "حدث قريب", Sky), Triple("other", "محاور أخرى", Violet))
    val byViews = videos.any { it.views > 0 }
    val values = axes.map { (k, _, _) ->
        val l = videos.filter { (it.axis.ifBlank { "history" }) == k }
        if (byViews) l.filter { it.views > 0 }.map { it.views.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0 else l.size.toDouble()
    }
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Card { Column {
        SectionTitle(if (byViews) "متوسط المشاهدات لكل محور" else "عدد الفيديوهات لكل محور", fx = "chart")
        axes.forEachIndexed { i, (_, label, c) ->
            val f by animateFloatAsState((values[i] / max).toFloat(), spring(dampingRatio = 0.7f, stiffness = 120f), label = "bar$i")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(96.dp))
                Canvas(Modifier.weight(1f).height(18.dp)) {
                    drawRoundRect(c.copy(alpha = 0.15f), Offset.Zero, size, CornerRadius(9f))
                    // bars grow from the right, like the Arabic text
                    drawRoundRect(c, Offset(size.width * (1 - f), 0f), Size(size.width * f, size.height), CornerRadius(9f))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (byViews) compact(values[i].toLong()) else values[i].toInt().toString(), style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(44.dp))
            }
        }
        if (!byViews) Text("أدخل أرقام المشاهدات من قائمة كل فيديو (⋮) ليقارن الرسم بين المحاور.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}

/** 1.1.2: this week at a glance (Monday to Sunday, Moroccan time): a dot for every video made, today circled,
 *  the weekly goal with − / +, and a daily reminder on days without a video. */
@Composable
fun WeekPlan(vm: AppViewModel, allDates: List<String>) {
    val s by vm.settings.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val today = remember { java.time.LocalDate.now(java.time.ZoneId.of("Africa/Casablanca")) }
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val days = (0 until 7).map { monday.plusDays(it.toLong()) }
    val byDay = allDates.groupingBy { it }.eachCount()
    val done = days.sumOf { d -> byDay[d.toString()] ?: 0 }
    val names = listOf("ن", "ث", "ر", "خ", "ج", "س", "ح")
    Card { Column {
        SectionTitle("خطة النشر هذا الأسبوع", fx = "calendar", trailing = {
            Pill("$done من ${s.weeklyGoal}", if (done >= s.weeklyGoal) Good else Gold)
        })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { i, d ->
                val n = byDay[d.toString()] ?: 0
                val isToday = d == today
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(names[i], style = MaterialTheme.typography.labelSmall, color = if (isToday) Gold else MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.size(34.dp).clip(CircleShape)
                        .background(if (n > 0) Gold else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .then(if (isToday) Modifier.border(2.dp, Gold, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                        Text(if (n > 0) "$n" else "${d.dayOfMonth}", style = MaterialTheme.typography.labelMedium,
                            color = if (n > 0) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Gap(8)
        val left = (s.weeklyGoal - done).coerceAtLeast(0)
        val daysLeft = 7 - today.dayOfWeek.value + 1
        Text(when {
            left == 0 -> "بلغت هدف الأسبوع. الانتظام يرفع ثقة الخوارزمية في القناة."
            left > daysLeft -> "بقي $left فيديو و$daysLeft أيام: فيديوهان في بعض الأيام، أو خفّض الهدف."
            else -> "بقي $left فيديو في $daysLeft أيام: نحو فيديو كل ${(daysLeft / left).coerceAtLeast(1)} يوم."
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap(6)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("الهدف الأسبوعي", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton({ vm.update { it.copy(weeklyGoal = (it.weeklyGoal - 1).coerceAtLeast(1)) } }) { Text("−") }
            Text("${s.weeklyGoal}", style = MaterialTheme.typography.titleMedium)
            TextButton({ vm.update { it.copy(weeklyGoal = (it.weeklyGoal + 1).coerceAtMost(14)) } }) { Text("+") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ذكّرني الساعة ${s.nudgeHour}:00 في يوم بلا فيديو", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(s.dailyNudge, { v ->
                vm.update { it.copy(dailyNudge = v) }
                ma.stickman.studio.work.DailyNudge.schedule(ctx, v, s.nudgeHour)
            })
        }
        if (s.dailyNudge) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(13, 18, 20, 22).forEach { h ->
                Chip("$h:00", s.nudgeHour == h) {
                    vm.update { it.copy(nudgeHour = h) }
                    ma.stickman.studio.work.DailyNudge.schedule(ctx, true, h)
                }
            }
        }
    } }
}
