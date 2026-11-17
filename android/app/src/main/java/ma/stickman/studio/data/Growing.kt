@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package ma.stickman.studio.data

import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.RandomAccessFile

/**
 * Watch while it downloads (1.1.1): the mp4 is written to its final file while the artifact streams in, and
 * the player reads it through [GrowingFileDataSource], which waits for the bytes that are not there yet.
 * The cloud writes the mp4 with "+faststart" (index first), so playback starts after the first megabyte.
 */
object Growing {
    /** path -> true when the file is complete. A path missing here is a normal, finished file. */
    val state = mutableStateMapOf<String, Boolean>()
    fun downloading(f: File) = state[f.path] == false
}

@UnstableApi
class GrowingFileDataSource : BaseDataSource(false) {
    private var raf: RandomAccessFile? = null
    private var uri: Uri? = null
    private var path = ""
    private var pos = 0L

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        path = dataSpec.uri.path ?: ""
        transferInitializing(dataSpec)
        val f = File(path)
        var waited = 0
        while (!f.exists() && waited < 200) { Thread.sleep(50); waited++ }
        raf = RandomAccessFile(f, "r")
        pos = dataSpec.position
        transferStarted(dataSpec)
        return if (Growing.downloading(f)) C.LENGTH_UNSET.toLong() else (f.length() - pos).coerceAtLeast(0)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val r = raf ?: return C.RESULT_END_OF_INPUT
        while (true) {
            val avail = r.length() - pos
            if (avail > 0) {
                r.seek(pos)
                val n = r.read(buffer, offset, minOf(length.toLong(), avail).toInt())
                if (n > 0) { pos += n; bytesTransferred(n); return n }
            }
            if (Growing.state[path] != false) return C.RESULT_END_OF_INPUT   // complete (or never streamed)
            Thread.sleep(40)                                                  // wait for the next bytes
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        runCatching { raf?.close() }
        raf = null
        if (uri != null) { uri = null; transferEnded() }
    }
}
