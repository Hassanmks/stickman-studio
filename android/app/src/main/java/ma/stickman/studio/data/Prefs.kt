package ma.stickman.studio.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    val owner: String = "Hassanmks",
    val repo: String = "stickman-studio",
    val model: String = DEFAULT_MODEL,
    val budget: Float = 1.5f,
    val voice: String = "male",
    val notifications: Boolean = true,
    val autoSaveGallery: Boolean = true,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    val theme: String = "dark",          // dark · light · system
    val githubLogin: String = "",
    val hasGithubToken: Boolean = false,
    val hasOpenRouterKey: Boolean = false,
    /** 1.1.2: one AI key is enough (Claude via Coarena, Gemini or OpenRouter); OpenRouter is only the last fallback. */
    val openRouterSynced: Boolean = false,
    val geminiSynced: Boolean = false,
    val coarenaSynced: Boolean = false,
    val hasCoarenaKey: Boolean = false,
    val previewFirst: Boolean = false,  // see the stills and approve before the 3-minute render
    // 1.1.0
    val sounds: Boolean = false,          // soft tap / success sounds
    val tilt: Boolean = true,             // cards lean with the phone
    val highContrast: Boolean = false,
    val fontScale: Float = 1f,
    val lock: Boolean = false,            // fingerprint / face to open the app
    val fps: Int = 30,                    // video defaults sent with every production
    val music: Boolean = true,
    val keywordColor: String = "#FFD400",
    val textSize: Float = 1f,
    val lastSeenVersion: String = "",
    val toured: Boolean = false,
    val remindPost: Boolean = true,       // reminder at the best time to post
    val weeklyGoal: Int = 4,              // 1.1.2: videos a week (publishing plan)
    val dailyNudge: Boolean = false,      // a reminder at nudgeHour on days without a new video
    val nudgeHour: Int = 20,
    // 1.3.0: when today's Coarena money runs out mid-step: "pause" (continue after midnight) or "free" models
    val onEmpty: String = "pause",
    val autoResume: Boolean = true,       // continue a paused step by itself when the balance comes back
    // 1.3.4: read the TikTok analytics screenshots by itself (only while a published video waits for its numbers)
    val autoStats: Boolean = true,
) {
    companion object { const val DEFAULT_MODEL = "auto" }

    /** The video settings for produce.yml (input `opts`): only what differs from the studio defaults. */
    val videoOpts: String get() = listOfNotNull(
        if (fps == 60) "fps=60" else null, if (!music) "music=off" else null,
        if (!keywordColor.equals("#FFD400", true)) "color=$keywordColor" else null,
        if (textSize != 1f) "text_size=" + "%.2f".format(java.util.Locale.US, textSize) else null,
    ).joinToString(";")
}

enum class Stage { IDLE, TOPICS_RUNNING, TOPICS_READY, SCRIPT_RUNNING, SCRIPT_READY, UPLOADING, PREVIEWING, PREVIEW_READY, PRODUCING, DONE, FAILED,
    /** 1.3.0: stopped with a checkpoint (failedFrom = the step, error = why); a re-run continues from it. */
    PAUSED }

data class Job(
    val stage: Stage = Stage.IDLE,
    val runId: Long = 0,
    val workflow: String = "",
    val slug: String = "",
    val title: String = "",
    val startedAt: Long = 0,
    val error: String = "",
    val failedFrom: Stage = Stage.IDLE,
)

/** Plain settings + job state in normal prefs; the two keys in encrypted prefs (Android Keystore). */
class Prefs(ctx: Context) {
    private val plain: SharedPreferences = ctx.getSharedPreferences("studio", Context.MODE_PRIVATE)
    private val secret: SharedPreferences = runCatching {
        val mk = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(ctx, "studio_secret", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }.getOrElse { ctx.getSharedPreferences("studio_secret_fallback", Context.MODE_PRIVATE) }

    init {
        // 1.0.6: Gemini (the user's own AI Studio key) replaces the slower OpenRouter free models, once.
        if (!plain.getBoolean("gmMigrated", false)) {
            if ((plain.getString("model", null) ?: "free") == "free") plain.edit().putString("model", "gemini").apply()
            plain.edit().putBoolean("gmMigrated", true).apply()
        }
        // 1.0.7: Coarena (Claude Sonnet 5.5, $5 free a day on the user's key) becomes the default, once.
        if (!plain.getBoolean("coaMigrated", false)) {
            if ((plain.getString("model", null) ?: "free") in setOf("free", "gemini")) plain.edit().putString("model", "coarena").apply()
            plain.edit().putBoolean("coaMigrated", true).apply()
        }
        // 1.1.0: "auto" (Claude for every step while Coarena has money, then Gemini, then free) becomes the default, once.
        if (!plain.getBoolean("autoMigrated", false)) {
            if ((plain.getString("model", null) ?: "coarena") in setOf("coarena", "gemini", "free")) plain.edit().putString("model", "auto").apply()
            plain.edit().putBoolean("autoMigrated", true).apply()
        }
    }

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings
    private val _job = MutableStateFlow(readJob())
    val job: StateFlow<Job> = _job

    val githubToken: String get() = secret.getString("gh", "") ?: ""
    val openRouterKey: String get() = secret.getString("or", "") ?: ""
    /** 1.0.9: kept (encrypted) only to show today's Coarena balance on the start screen. */
    val coarenaKey: String get() = secret.getString("coa", "") ?: ""

    /** 1.2.0: kept (encrypted) on the phone too, so the conversation can fall back to Gemini. */
    val geminiKey: String get() = secret.getString("gem", "") ?: ""
    fun setGeminiKey(k: String) { secret.edit().putString("gem", k).apply() }

    fun setCoarenaKey(k: String) {
        secret.edit().putString("coa", k).apply()
        plain.edit().putBoolean("coaSynced", true).apply()
        _settings.value = readSettings()
    }

    fun setGithubToken(t: String, login: String) {
        secret.edit().putString("gh", t).apply()
        plain.edit().putString("login", login).apply()
        _settings.value = readSettings()
    }

    fun setOpenRouterKey(k: String, synced: Boolean) {
        secret.edit().putString("or", k).apply()
        plain.edit().putBoolean("orSynced", synced).apply()
        _settings.value = readSettings()
    }

    /** The Gemini key lives only in the GitHub secrets (the cloud agent uses it); the phone keeps a flag. */
    fun setCoarenaSynced(v: Boolean) {
        plain.edit().putBoolean("coaSynced", v).apply()
        _settings.value = readSettings()
    }

    fun setGeminiSynced(v: Boolean) {
        plain.edit().putBoolean("gmSynced", v).apply()
        _settings.value = readSettings()
    }

    fun update(f: (Settings) -> Settings) {
        val s = f(_settings.value)
        plain.edit()
            .putString("owner", s.owner).putString("repo", s.repo).putString("model", s.model)
            .putFloat("budget", s.budget).putString("voice", s.voice).putBoolean("notif", s.notifications)
            .putBoolean("gallery", s.autoSaveGallery).putBoolean("haptics", s.haptics)
            .putBoolean("reduceMotion", s.reduceMotion).putString("theme", s.theme).putBoolean("previewFirst", s.previewFirst)
            .putBoolean("sounds", s.sounds).putBoolean("tilt", s.tilt).putBoolean("highContrast", s.highContrast)
            .putFloat("fontScale", s.fontScale).putBoolean("lock", s.lock).putInt("fps", s.fps).putBoolean("music", s.music)
            .putString("kwColor", s.keywordColor).putFloat("textSize", s.textSize).putString("seenVersion", s.lastSeenVersion)
            .putBoolean("toured", s.toured).putBoolean("remindPost", s.remindPost)
            .putInt("weeklyGoal", s.weeklyGoal).putBoolean("dailyNudge", s.dailyNudge).putInt("nudgeHour", s.nudgeHour)
            .putString("onEmpty", s.onEmpty).putBoolean("autoResume", s.autoResume)
            .putBoolean("autoStats", s.autoStats)
            .apply()
        _settings.value = readSettings()
    }

    /** 1.3.5: the whole video's clock and bill (data/Journey.kt). */
    val journeys by lazy { JourneyStore(plain) }

    fun setJob(j: Job) {
        runCatching { journeys.onJob(_job.value, j) }
        plain.edit().putString("stage", j.stage.name).putLong("runId", j.runId).putString("wf", j.workflow)
            .putString("slug", j.slug).putString("title", j.title).putLong("started", j.startedAt)
            .putString("error", j.error).putString("failedFrom", j.failedFrom.name).apply()
        _job.value = j
    }

    fun reloadJob() { _job.value = readJob() }

    /** 1.3.0: when a paused step will continue by itself (0 = not scheduled). */
    val resumeAt: Long get() = plain.getLong("resumeAt", 0)
    fun setResumeAt(t: Long) { plain.edit().putLong("resumeAt", t).apply() }

    /** 1.3.4: the newest screenshot already looked at (MediaStore DATE_ADDED, seconds); 0 = never armed. */
    val shotSeen: Long get() = plain.getLong("shotSeen", 0)
    fun setShotSeen(t: Long) { plain.edit().putLong("shotSeen", t).apply() }

    private fun readSettings() = Settings(
        owner = plain.getString("owner", null) ?: "Hassanmks",
        repo = plain.getString("repo", null) ?: "stickman-studio",
        model = plain.getString("model", null) ?: Settings.DEFAULT_MODEL,
        budget = plain.getFloat("budget", 1.5f),
        voice = plain.getString("voice", null) ?: "male",
        notifications = plain.getBoolean("notif", true),
        autoSaveGallery = plain.getBoolean("gallery", true),
        haptics = plain.getBoolean("haptics", true),
        reduceMotion = plain.getBoolean("reduceMotion", false),
        theme = plain.getString("theme", null) ?: "dark",
        githubLogin = plain.getString("login", "") ?: "",
        hasGithubToken = githubToken.isNotBlank(),
        hasOpenRouterKey = openRouterKey.isNotBlank(),
        openRouterSynced = plain.getBoolean("orSynced", false),
        geminiSynced = plain.getBoolean("gmSynced", false),
        coarenaSynced = plain.getBoolean("coaSynced", false),
        hasCoarenaKey = coarenaKey.isNotBlank(),
        previewFirst = plain.getBoolean("previewFirst", false),
        sounds = plain.getBoolean("sounds", false),
        tilt = plain.getBoolean("tilt", true),
        highContrast = plain.getBoolean("highContrast", false),
        fontScale = plain.getFloat("fontScale", 1f),
        lock = plain.getBoolean("lock", false),
        fps = plain.getInt("fps", 30),
        music = plain.getBoolean("music", true),
        keywordColor = plain.getString("kwColor", null) ?: "#FFD400",
        textSize = plain.getFloat("textSize", 1f),
        lastSeenVersion = plain.getString("seenVersion", null) ?: "",
        toured = plain.getBoolean("toured", false),
        remindPost = plain.getBoolean("remindPost", true),
        weeklyGoal = plain.getInt("weeklyGoal", 4).coerceIn(1, 14),
        dailyNudge = plain.getBoolean("dailyNudge", false),
        nudgeHour = plain.getInt("nudgeHour", 20).coerceIn(0, 23),
        onEmpty = plain.getString("onEmpty", null) ?: "pause",
        autoResume = plain.getBoolean("autoResume", true),
        autoStats = plain.getBoolean("autoStats", true),
    )

    /** Backup of the settings (never the keys: those stay encrypted on this phone and in GitHub). */
    fun exportSettings(): org.json.JSONObject = org.json.JSONObject().apply {
        plain.all.forEach { (k, v) -> if (k !in setOf("stage", "runId", "wf", "slug", "title", "started", "error", "failedFrom")) put(k, v) }
    }

    fun importSettings(o: org.json.JSONObject) {
        val e = plain.edit()
        val floats = setOf("budget", "fontScale", "textSize")
        o.keys().forEach { k ->
            val v = o.get(k)
            // JSON turns 2.0 into 2: keep the type the app stores for this key
            runCatching {
                when {
                    k in floats || plain.all[k] is Float -> e.putFloat(k, (v as Number).toFloat())
                    plain.all[k] is Long -> e.putLong(k, (v as Number).toLong())
                    v is Boolean -> e.putBoolean(k, v)
                    v is Number -> e.putInt(k, v.toInt())
                    v is String -> e.putString(k, v)
                }
            }
        }
        e.apply(); _settings.value = readSettings()
    }

    private fun readJob() = Job(
        stage = runCatching { Stage.valueOf(plain.getString("stage", "IDLE")!!) }.getOrDefault(Stage.IDLE),
        runId = plain.getLong("runId", 0), workflow = plain.getString("wf", "") ?: "",
        slug = plain.getString("slug", "") ?: "", title = plain.getString("title", "") ?: "",
        startedAt = plain.getLong("started", 0), error = plain.getString("error", "") ?: "",
        failedFrom = runCatching { Stage.valueOf(plain.getString("failedFrom", "IDLE")!!) }.getOrDefault(Stage.IDLE),
    )
}

/** At least one AI key is known (on the phone or already in the GitHub secrets). */
fun hasAiKey(s: Settings): Boolean = s.hasOpenRouterKey || s.hasCoarenaKey || s.coarenaSynced || s.geminiSynced
