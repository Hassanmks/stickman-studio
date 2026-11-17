package ma.stickman.studio.data

import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.engines.XSalsa20Engine
import org.bouncycastle.crypto.macs.Poly1305
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.math.ec.rfc7748.X25519
import java.security.SecureRandom

/**
 * libsodium crypto_box_seal, which GitHub requires to store a repository secret.
 * output = ephemeral_pk(32) || poly1305_tag(16) || xsalsa20(message)
 */
object SealedBox {
    fun seal(message: ByteArray, recipientPk: ByteArray): ByteArray {
        require(recipientPk.size == 32) { "bad public key" }
        val rnd = SecureRandom()
        val esk = ByteArray(32).also { X25519.generatePrivateKey(rnd, it) }
        val epk = ByteArray(32).also { X25519.generatePublicKey(esk, 0, it, 0) }
        val shared = ByteArray(32)
        check(X25519.calculateAgreement(esk, 0, recipientPk, 0, shared, 0)) { "bad key agreement" }

        val nonce = ByteArray(24)
        Blake2bDigest(192).apply {
            update(epk, 0, 32); update(recipientPk, 0, 32); doFinal(nonce, 0)
        }
        val key = hsalsa20(shared, ByteArray(16))

        // crypto_secretbox: the first 32 keystream bytes are the Poly1305 key, the rest encrypt the message.
        val stream = XSalsa20Engine().apply { init(true, ParametersWithIV(KeyParameter(key), nonce)) }
        val polyKey = ByteArray(32)
        stream.processBytes(ByteArray(32), 0, 32, polyKey, 0)
        val cipher = ByteArray(message.size)
        stream.processBytes(message, 0, message.size, cipher, 0)
        val tag = ByteArray(16)
        Poly1305().apply { init(KeyParameter(polyKey)); update(cipher, 0, cipher.size); doFinal(tag, 0) }
        return epk + tag + cipher
    }

    private fun hsalsa20(k: ByteArray, n: ByteArray): ByteArray {
        fun le(b: ByteArray, o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or
            ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)
        val x = IntArray(16)
        x[0] = 0x61707865; x[5] = 0x3320646e; x[10] = 0x79622d32; x[15] = 0x6b206574
        for (i in 0 until 4) { x[1 + i] = le(k, i * 4); x[11 + i] = le(k, 16 + i * 4); x[6 + i] = le(n, i * 4) }
        fun r(a: Int, b: Int) = (a shl b) or (a ushr (32 - b))
        repeat(10) {
            x[4] = x[4] xor r(x[0] + x[12], 7); x[8] = x[8] xor r(x[4] + x[0], 9)
            x[12] = x[12] xor r(x[8] + x[4], 13); x[0] = x[0] xor r(x[12] + x[8], 18)
            x[9] = x[9] xor r(x[5] + x[1], 7); x[13] = x[13] xor r(x[9] + x[5], 9)
            x[1] = x[1] xor r(x[13] + x[9], 13); x[5] = x[5] xor r(x[1] + x[13], 18)
            x[14] = x[14] xor r(x[10] + x[6], 7); x[2] = x[2] xor r(x[14] + x[10], 9)
            x[6] = x[6] xor r(x[2] + x[14], 13); x[10] = x[10] xor r(x[6] + x[2], 18)
            x[3] = x[3] xor r(x[15] + x[11], 7); x[7] = x[7] xor r(x[3] + x[15], 9)
            x[11] = x[11] xor r(x[7] + x[3], 13); x[15] = x[15] xor r(x[11] + x[7], 18)
            x[1] = x[1] xor r(x[0] + x[3], 7); x[2] = x[2] xor r(x[1] + x[0], 9)
            x[3] = x[3] xor r(x[2] + x[1], 13); x[0] = x[0] xor r(x[3] + x[2], 18)
            x[6] = x[6] xor r(x[5] + x[4], 7); x[7] = x[7] xor r(x[6] + x[5], 9)
            x[4] = x[4] xor r(x[7] + x[6], 13); x[5] = x[5] xor r(x[4] + x[7], 18)
            x[11] = x[11] xor r(x[10] + x[9], 7); x[8] = x[8] xor r(x[11] + x[10], 9)
            x[9] = x[9] xor r(x[8] + x[11], 13); x[10] = x[10] xor r(x[9] + x[8], 18)
            x[12] = x[12] xor r(x[15] + x[14], 7); x[13] = x[13] xor r(x[12] + x[15], 9)
            x[14] = x[14] xor r(x[13] + x[12], 13); x[15] = x[15] xor r(x[14] + x[13], 18)
        }
        val out = ByteArray(32)
        intArrayOf(x[0], x[5], x[10], x[15], x[6], x[7], x[8], x[9]).forEachIndexed { i, w ->
            for (j in 0 until 4) out[i * 4 + j] = (w ushr (8 * j)).toByte()
        }
        return out
    }
}
