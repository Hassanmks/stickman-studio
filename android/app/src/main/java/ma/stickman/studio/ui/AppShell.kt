package ma.stickman.studio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ma.stickman.studio.BuildConfigInfo
import ma.stickman.studio.data.hasAiKey
import ma.stickman.studio.ui.theme.Gold
import ma.stickman.studio.ui.theme.Good
import ma.stickman.studio.ui.theme.LocalMotion

private val TABS = listOf(Triple("المحادثة", "speech", 0), Triple("المشاريع", "folder", 1), Triple("الإعدادات", "gear", 2))

@Composable
fun AppShell(vm: AppViewModel) {
    val s by vm.settings.collectAsState()
    val snack = remember { SnackbarHostState() }
    val fb = LocalFeedback.current
    val snackHost: @Composable () -> Unit = {
        SnackbarHost(snack) { d ->
            Snackbar(d, shape = RoundedCornerShape(18.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface, actionColor = MaterialTheme.colorScheme.primary)
        }
    }
    val scope = rememberCoroutineScope()
    val toast: (String) -> Unit = { m -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m) } }
    LaunchedEffect(Unit) {
        vm.message.collect { m ->
            when (m.kind) { 1 -> fb.success(); -1 -> fb.error() }
            scope.launch {
                snack.currentSnackbarData?.dismiss()
                val r = snack.showSnackbar(m.text, m.action, duration = if (m.action != null) SnackbarDuration.Long else SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) m.onAction?.invoke()
            }
        }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(vm.tabRequest) { vm.tabRequest?.let { tab = it; vm.tabRequest = null } }
    BackHandler(enabled = tab != 0) { tab = 0 }
    val motion = LocalMotion.current
    val outlineC = MaterialTheme.colorScheme.outlineVariant

    if (!(s.hasGithubToken && hasAiKey(s)) && !vm.demo) {
        Box(Modifier.fillMaxSize()) {
            DepthBackground()
            Scaffold(snackbarHost = snackHost, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) { pad -> Box(Modifier.padding(pad)) { Onboarding(vm) } }
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        DepthBackground(scroll = { ScrollBus.y.floatValue })
        Row(Modifier.fillMaxSize()) {
            if (wide) NavigationRail(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), modifier = Modifier.statusBarsPadding()) {
                Spacer(Modifier.height(12.dp))
                TABS.forEach { (l, fx, i) ->
                    NavigationRailItem(tab == i, { fb.tap(); tab = i }, { NavIcon(fx, tab == i) }, label = { Text(l) })
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground,
                snackbarHost = snackHost,
                bottomBar = {
                    if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp,
                        modifier = Modifier.drawBehind { drawLine(outlineC, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1f) }) {
                        TABS.forEach { (l, fx, i) ->
                            NavigationBarItem(tab == i, { fb.tap(); tab = i }, { NavIcon(fx, tab == i) }, label = { Text(l) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }
                },

            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    // 1.3.5 (speed): each tab keeps its scroll position and remembered state when you come back
                    val tabState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
                    Centered {
                        AnimatedContent(tab, label = "tab", transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (fadeIn(tween((220 * motion).toInt())) + slideInVertically(tween((260 * motion).toInt())) { it / 20 * dir })
                                .togetherWith(fadeOut(tween((100 * motion).toInt())))
                        }) { t ->
                            tabState.SaveableStateProvider(t) {
                                when (t) {
                                    0 -> StudioScreen(vm, toast)
                                    1 -> HistoryScreen(vm) { tab = 0; vm.newChat() }
                                    else -> SettingsScreen(vm)
                                }
                            }
                        }
                    }
                    vm.busy?.let { b -> if (tab == 0) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) { Busy(b, vm.progress) } }
                    // a new version on GitHub: one tap to install it
                    vm.release?.let { r ->
                        if (tab != 2) Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp)) {
                            Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).pressable { vm.installUpdate() }
                                .padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                FxIcon("package", 20.dp); Spacer(Modifier.width(6.dp))
                                Text("تحديث ${r.version} جاهز: اضغط للتثبيت", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (vm.showCoach) CoachSheet(vm) { vm.showCoach = false }
    vm.spendAsk?.let { a ->
        AlertDialog(onDismissRequest = { vm.spendAsk = null },
            confirmButton = { TextButton({ vm.spendAsk = null; a.go() }) { Text("ابدأ") } },
            dismissButton = { TextButton({ vm.spendAsk = null }) { Text("ليس الآن") } },
            title = { Text("الرصيد قد لا يكفي") },
            text = { Text(a.text, style = MaterialTheme.typography.bodyMedium) })
    }
    if (!s.toured && !vm.demo) Tour { vm.update { it.copy(toured = true, lastSeenVersion = BuildConfigInfo.VERSION) } }
    else if (s.lastSeenVersion != BuildConfigInfo.VERSION && !vm.demo) WhatsNewSheet(vm, { t -> tab = t }) { vm.update { it.copy(lastSeenVersion = BuildConfigInfo.VERSION) } }
}

/** A thin line icon; the chosen tab is drawn in the text colour. */
@Composable
private fun NavIcon(fx: String, selected: Boolean) {
    FxIcon(fx, 24.dp, tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Onboarding(vm: AppViewModel) {
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Box(Modifier.size(150.dp).glow(), contentAlignment = Alignment.Center) { FxIcon("clapper", 96.dp, Modifier.tilt3d(1.6f, false)) }
            Text("TaleSpark", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Gap(6)
            Text("خطوتان فقط، مرة واحدة. بعدها كل شيء من هاتفك.", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        item {
            Card(tilt = true) { Column {
                SectionTitle("1. رمز GitHub", fx = "key", trailing = { if (s.hasGithubToken) Pill("✓ ${s.githubLogin}", Good) })
                Text("اضغط «أنشئ رمزًا»، سجّل الدخول، انزل إلى آخر الصفحة واضغط Generate token، ثم انسخ الرمز والصقه هنا.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(8)
                GhostButton("أنشئ رمزًا", icon = Icons.AutoMirrored.Rounded.OpenInNew) { openUrl(ctx, TOKEN_URL) }
                Gap(8)
                KeyField("ghp_…", "تحقق واحفظ") { t, done -> vm.saveGithubToken(t, done) }
            } }
        }
        item {
            Card(tilt = true) { Column {
                SectionTitle("2. مفتاح ذكاء اصطناعي واحد", fx = "lock", trailing = { if (hasAiKey(s)) Pill("✓ محفوظ", Good) })
                Text("يكفي مفتاح واحد. الأفضل مفتاح Coarena (5 دولارات يوميًا لأقوى النماذج). يمكنك إضافة Gemini وOpenRouter لاحقًا من الإعدادات احتياطًا.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(8)
                Text("Claude (Coarena)", style = MaterialTheme.typography.titleSmall)
                KeyField("sk-…", "تحقق واحفظ") { k, done -> vm.saveCoarenaKey(k, done) }
                Gap(10)
                Text("أو Gemini", style = MaterialTheme.typography.titleSmall)
                KeyField("AIza…", "تحقق واحفظ") { k, done -> vm.saveGeminiKey(k, done) }
                Gap(10)
                Text("أو OpenRouter", style = MaterialTheme.typography.titleSmall)
                KeyField("sk-or-v1-…", "تحقق واحفظ") { k, done -> vm.saveOpenRouterKey(k, done) }
            } }
        }
        vm.busy?.let { b -> item { Busy(b) } }
    }
}

/** First use: four short pages that show how the app works. */
@Composable
private fun Tour(done: () -> Unit) {
    val pages = listOf(
        Triple("sparkles", "اقترح المواضيع", "اضغط «اقترح 10 مواضيع». يبحث الذكاء الاصطناعي في أخبار الأسبوع، ويرتّب لك المواضيع بشارات: قوة الخطّاف، الصور الحقيقية، الحساسية."),
        Triple("scroll", "السيناريو جاهز", "اختر موضوعًا، فيكتب السيناريو ويتحقق من المصادر. تراه مشهدًا مشهدًا، بخط زمني ومدة تقريبية."),
        Triple("mic", "الصوت والصور منك", "انسخ نص الصوت إلى AI Studio وأمر الصور إلى ChatGPT، ثم شارك الملفين مع التطبيق. يفحص طول الصوت واللوحة قبل الإرسال."),
        Triple("rocket", "السحابة تنتج", "تتابع كل مرحلة حيًّا مع التكلفة. يمكنك إغلاق التطبيق: إشعار بشريط تقدّم وزر إلغاء، ثم الفيديو وحزمة النشر."),
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = {}, confirmButton = {
        TextButton({ if (pager.currentPage == pages.lastIndex) done() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
            Text(if (pager.currentPage == pages.lastIndex) "ابدأ" else "التالي")
        }
    }, dismissButton = { TextButton(done) { Text("تخطَّ") } }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(pager, Modifier.fillMaxWidth().height(300.dp)) { i ->
                val (fx, t, d) = pages[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(130.dp).glow(), contentAlignment = Alignment.Center) { FxIcon(fx, 84.dp, Modifier.tilt3d(1.5f, false)) }
                    Gap(6)
                    Text(t, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Gap(6)
                    Text(d, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(pages.size) { i ->
                    val w by animateFloatAsState(if (pager.currentPage == i) 22f else 8f, label = "dot")
                    Box(Modifier.height(8.dp).width(w.dp).clip(CircleShape).background(if (pager.currentPage == i) Gold else MaterialTheme.colorScheme.outline))
                }
            }
        }
    })
}

/** Shown while the fingerprint prompt is open (lock on). */
@Composable
fun LockScreen(retry: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        DepthBackground()
        Column(Modifier.fillMaxSize().navigationBarsPadding(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(150.dp).glow(), contentAlignment = Alignment.Center) { FxIcon("lock", 90.dp, Modifier.tilt3d(1.5f, false)) }
            Gap(10)
            Text("TaleSpark مقفل", style = MaterialTheme.typography.headlineSmall)
            Gap(16)
            GoldButton("افتح بالبصمة", fx = "key", modifier = Modifier.width(240.dp)) { retry() }
        }
    }
}
