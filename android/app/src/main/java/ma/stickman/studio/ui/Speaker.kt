package ma.stickman.studio.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** 1.3.6: reads a reply aloud with the phone's own voice (Arabic); a second tap stops it. Nothing leaves the phone. */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun toggle(ctx: Context, text: String) {
        val t = tts
        if (t != null && t.isSpeaking) { t.stop(); return }
        val clean = text.replace(Regex("[*#`>]"), " ").replace(Regex("""\s+"""), " ").trim().take(3900)
        if (clean.isBlank()) return
        if (t == null) {
            pending = clean
            tts = TextToSpeech(ctx.applicationContext) { st ->
                ready = st == TextToSpeech.SUCCESS
                if (ready) { tts?.language = Locale("ar"); pending?.let { say(it) }; pending = null }
            }
        } else if (ready) say(clean) else pending = clean
    }

    private fun say(s: String) { tts?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "reply") }
}
