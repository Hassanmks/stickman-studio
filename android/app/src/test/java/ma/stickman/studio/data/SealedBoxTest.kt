package ma.stickman.studio.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * Seals a message for the public key in SEALEDBOX_PK (hex) and writes base64 to SEALEDBOX_OUT,
 * so libsodium (PyNaCl) can open it and prove the format matches what GitHub expects.
 */
class SealedBoxTest {
    @Test
    fun sealsForLibsodium() {
        val pkHex = System.getenv("SEALEDBOX_PK") ?: return
        val pk = pkHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val msg = "مرحبا sk-or-v1-test 123".toByteArray()
        val sealed = SealedBox.seal(msg, pk)
        assertEquals(32 + 16 + msg.size, sealed.size)
        File(System.getenv("SEALEDBOX_OUT")!!).writeText(Base64.getEncoder().encodeToString(sealed))
    }
}
