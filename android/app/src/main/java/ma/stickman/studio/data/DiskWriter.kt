package ma.stickman.studio.data

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 1.3.5 (speed): every save of the app's own files used to run on the screen's thread (local.json, chats.json),
 * so a tap that saved something froze the frame being drawn. Now the save is handed to one background thread,
 * and several saves of the same file within 120 ms become one write of the newest content (atomic: temp + rename).
 */
object DiskWriter {
    private val pool = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "disk-writer").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private val pending = ConcurrentHashMap<String, () -> String>()

    fun later(file: File, content: () -> String) {
        val key = file.absolutePath
        if (pending.put(key, content) != null) return            // a write of this file is already queued: it takes the newest
        pool.schedule({ flush(file) }, 120, TimeUnit.MILLISECONDS)
    }

    private fun flush(file: File, tries: Int = 0) {
        val make = pending.remove(file.absolutePath) ?: return
        val text = runCatching { make() }.getOrElse {
            // the data changed while it was being read: try again with the newest
            if (tries < 3) { pending.putIfAbsent(file.absolutePath, make); pool.schedule({ flush(file, tries + 1) }, 60, TimeUnit.MILLISECONDS) }
            return
        }
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) { file.writeText(text); tmp.delete() }
        }
    }
}
