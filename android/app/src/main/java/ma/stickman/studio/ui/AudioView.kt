package ma.stickman.studio.ui

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ma.stickman.studio.ui.theme.Gold
import ma.stickman.studio.ui.theme.Ink
import java.nio.ByteOrder
import kotlin.math.abs

private val waveCache = mutableMapOf<String, FloatArray>()

/**
 * Loudness of the voice in [bars] slices (0..1), decoded on the phone (wav, mp3, m4a, ogg…).
 * Every 8th sample is enough for a picture, so a 2-minute voice takes about a second.
 */
suspend fun waveform(ctx: Context, uri: Uri, bars: Int = 90): FloatArray = withContext(Dispatchers.IO) {
    waveCache[uri.toString()]?.let { return@withContext it }
    val ex = MediaExtractor()
    try {
        ex.setDataSource(ctx, uri, null)
        val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio") == true }
            ?: return@withContext FloatArray(0)
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0); codec.start()
        val peaks = FloatArray(bars)
        val info = MediaCodec.BufferInfo()
        var inDone = false
        var outDone = false
        while (!outDone) {
            if (!inDone) {
                val i = codec.dequeueInputBuffer(5000)
                if (i >= 0) {
                    val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                    if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                    else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 5000)
            if (o >= 0) {
                val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val bar = if (durUs > 0) ((info.presentationTimeUs * bars) / durUs).toInt().coerceIn(0, bars - 1) else 0
                var k = 0
                while (k < buf.limit()) { val v = abs(buf.get(k).toInt()) / 32768f; if (v > peaks[bar]) peaks[bar] = v; k += 8 }
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
            }
        }
        codec.stop(); codec.release()
        val max = peaks.maxOrNull()?.takeIf { it > 0 } ?: 1f
        FloatArray(bars) { (peaks[it] / max).coerceIn(0.04f, 1f) }.also { waveCache[uri.toString()] = it }
    } catch (e: Exception) { FloatArray(0) } finally { ex.release() }
}

/** Listen to the voice before sending it: play / pause, its wave, tap the wave to jump. */
@Composable
fun AudioPlayer(uri: Uri, seconds: Int?) {
    val ctx = LocalContext.current
    val wave by produceState(FloatArray(0), uri) { value = waveform(ctx, uri) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableFloatStateOf(0f) }
    val player = remember(uri) { runCatching { MediaPlayer().apply { setDataSource(ctx, uri); prepare() } }.getOrNull() }
    DisposableEffect(player) { onDispose { player?.release() } }
    LaunchedEffect(playing) {
        while (playing && player != null) {
            pos = player.currentPosition.toFloat() / player.duration.coerceAtLeast(1)
            if (!player.isPlaying) { playing = false; pos = 0f }
            delay(80)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        FilledIconButton(onClick = {
            player ?: return@FilledIconButton
            if (playing) player.pause() else player.start()
            playing = !playing
        }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Gold, contentColor = Ink), modifier = Modifier.size(42.dp)) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "أوقف" else "استمع")
        }
        Spacer(Modifier.width(10.dp))
        val base = MaterialTheme.colorScheme.outline
        Canvas(Modifier.weight(1f).height(40.dp).pointerInput(player) {
            detectTapGestures { o ->
                player ?: return@detectTapGestures
                // the wave is drawn left to right (time), whatever the page direction
                val f = (o.x / size.width).coerceIn(0f, 1f)
                player.seekTo((f * player.duration).toInt()); pos = f
            }
        }) {
            if (wave.isEmpty()) {
                drawRoundRect(base.copy(alpha = 0.3f), Offset(0f, size.height / 2 - 2), Size(size.width, 4f), CornerRadius(2f))
                return@Canvas
            }
            val w = size.width / wave.size
            wave.forEachIndexed { i, v ->
                val h = v * size.height
                drawRoundRect(if (i.toFloat() / wave.size <= pos) Gold else base, Offset(i * w + w * 0.18f, (size.height - h) / 2),
                    Size(w * 0.64f, h), CornerRadius(w))
            }
        }
        Spacer(Modifier.width(8.dp))
        seconds?.let { Text("%d:%02d".format(it / 60, it % 60), style = MaterialTheme.typography.labelMedium) }
    }
}
