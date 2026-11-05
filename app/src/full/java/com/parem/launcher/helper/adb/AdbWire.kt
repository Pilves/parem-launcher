package com.parem.launcher.helper.adb

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The byte formats of adb's wireless pairing and transport, JDK-only so they
 * are unit-tested. Sources: AOSP packages/modules/adb (pairing_connection,
 * pairing_auth, adb.h, protocol.txt, libcrypto_utils android_pubkey).
 */
object AdbWire {

    // ---- Transport messages (protocol.txt) ----

    const val CNXN = 0x4e584e43
    const val OPEN = 0x4e45504f
    const val OKAY = 0x59414b4f
    const val CLSE = 0x45534c43
    const val WRTE = 0x45545257
    const val STLS = 0x534c5453
    const val VERSION = 0x01000001
    const val STLS_VERSION = 0x01000000
    const val MAX_DATA = 256 * 1024
    private const val HEADER_SIZE = 24

    class Message(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)

    fun message(command: Int, arg0: Int, arg1: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(command).putInt(arg0).putInt(arg1).putInt(payload.size)
        buf.putInt(payload.sumOf { it.toInt() and 0xff })
        buf.putInt(command.inv())
        buf.put(payload)
        return buf.array()
    }

    fun readMessage(input: DataInputStream): Message {
        val header = ByteArray(HEADER_SIZE)
        input.readFully(header)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val length = buf.int
        buf.int // checksum: ignored from VERSION 0x01000001 on
        if (buf.int != command.inv()) throw IOException("bad adb message magic")
        if (length < 0 || length > MAX_DATA) throw IOException("bad adb payload length $length")
        val payload = ByteArray(length)
        input.readFully(payload)
        return Message(command, arg0, arg1, payload)
    }

    // ---- Pairing (pairing_connection.cpp, pairing_auth.cpp) ----

    const val PAIR_SPAKE2_MSG = 0
    const val PAIR_PEER_INFO = 1
    const val PEER_INFO_SIZE = 8192
    private const val PAIR_HEADER_VERSION = 1
    val CLIENT_NAME = "adb pair client\u0000".toByteArray(Charsets.UTF_8)
    val SERVER_NAME = "adb pair server\u0000".toByteArray(Charsets.UTF_8)
    // Passed to the platform's TLS keying-material exporter, which works with
    // this exact string (as Kadb and LADB do against the same Conscrypt).
    const val EXPORTED_KEY_LABEL = "adb-label\u0000"
    const val EXPORTED_KEY_SIZE = 64
    private val HKDF_INFO = "adb pairing_auth aes-128-gcm key".toByteArray(Charsets.UTF_8)

    fun pairingHeader(type: Int, payloadSize: Int): ByteArray =
        ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
            .put(PAIR_HEADER_VERSION.toByte()).put(type.toByte()).putInt(payloadSize).array()

    /** Reads one pairing packet of [expectedType]; throws on anything else. */
    fun readPairingPacket(input: DataInputStream, expectedType: Int): ByteArray {
        val header = ByteArray(6)
        input.readFully(header)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val version = buf.get().toInt()
        val type = buf.get().toInt()
        val size = buf.int
        if (version != PAIR_HEADER_VERSION || type != expectedType || size <= 0 || size > 2 * PEER_INFO_SIZE)
            throw IOException("unexpected pairing packet v$version t$type s$size")
        return ByteArray(size).also { input.readFully(it) }
    }

    /** PeerInfo { u8 type = ADB_RSA_PUB_KEY (0); u8 data[8191] }, zero-padded. */
    fun peerInfo(adbPublicKey: ByteArray): ByteArray {
        val out = ByteArray(PEER_INFO_SIZE)
        adbPublicKey.copyInto(out, 1, 0, minOf(adbPublicKey.size, PEER_INFO_SIZE - 1))
        return out
    }

    /** The AES-128-GCM key both sides derive from the SPAKE2 key. */
    fun pairingKey(spake2Key: ByteArray): ByteArray = hkdfSha256(spake2Key, HKDF_INFO, 16)

    /** RFC 5869 with no salt (a hash-length run of zeros). */
    fun hkdfSha256(ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArrayOutputStream()
        var previous = ByteArray(0)
        var counter = 1
        while (out.size() < length) {
            mac.update(previous)
            mac.update(info)
            mac.update(counter++.toByte())
            previous = mac.doFinal()
            out.write(previous)
        }
        return out.toByteArray().copyOf(length)
    }

    /**
     * AES-128-GCM with adb's nonce: the per-direction message counter,
     * little-endian, in the first 8 of 12 bytes. Null when decryption fails.
     */
    fun gcm(key: ByteArray, counter: Long, input: ByteArray, encrypt: Boolean): ByteArray? {
        val nonce = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putLong(counter).array()
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                doFinal(input)
            }
        } catch (e: java.security.GeneralSecurityException) {
            null
        }
    }

    // ---- Keys ----

    /**
     * adb's public-key string: base64 of android_pubkey's RSAPublicKey struct
     * (2048-bit only), a space, the name, and a NUL.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun adbPublicKey(key: RSAPublicKey, name: String): ByteArray {
        val modulusBytes = 256
        val n = key.modulus
        require(n.bitLength() == 2048) { "adb keys are 2048-bit RSA" }
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = r32 - n.mod(r32).modInverse(r32)
        val rr = BigInteger.ONE.shiftLeft(2048).modPow(BigInteger.valueOf(2), n)
        val buf = ByteBuffer.allocate(4 + 4 + modulusBytes * 2 + 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(modulusBytes / 4)
        buf.putInt(n0inv.toInt())
        buf.put(littleEndian(n, modulusBytes))
        buf.put(littleEndian(rr, modulusBytes))
        buf.putInt(key.publicExponent.toInt())
        return (Base64.encode(buf.array()) + " " + name + "\u0000").toByteArray(Charsets.UTF_8)
    }

    private fun littleEndian(value: BigInteger, size: Int): ByteArray {
        val be = value.toByteArray()
        val out = ByteArray(size)
        for (i in be.indices) {
            val j = be.size - 1 - i
            if (j < size) out[j] = be[i]
        }
        return out
    }

    /**
     * A minimal self-signed X.509 v3 certificate (no extensions). adbd ignores
     * everything in it but the public key, which it matches against the key
     * stored at pairing time.
     */
    fun selfSignedCertificate(keyPair: KeyPair, commonName: String): X509Certificate {
        val sha256WithRsa = der(0x30, der(0x06, bytes(0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x01, 0x01, 0x0b)) + der(0x05))
        val name = der(0x30, der(0x31, der(0x30,
            der(0x06, bytes(0x55, 0x04, 0x03)) + der(0x0c, commonName.toByteArray(Charsets.UTF_8))
        )))
        val validity = der(0x30,
            der(0x17, "250101000000Z".toByteArray(Charsets.US_ASCII)) +
                der(0x17, "491231235959Z".toByteArray(Charsets.US_ASCII))
        )
        val tbs = der(0x30,
            der(0xa0, der(0x02, bytes(0x02))) +
                der(0x02, bytes(0x01)) +
                sha256WithRsa + name + validity + name +
                keyPair.public.encoded
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(tbs)
            sign()
        }
        val cert = der(0x30, tbs + sha256WithRsa + der(0x03, byteArrayOf(0) + signature))
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(cert)) as X509Certificate
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun der(tag: Int, content: ByteArray = ByteArray(0)): ByteArray {
        val len = content.size
        val lengthBytes = when {
            len < 0x80 -> bytes(len)
            len < 0x100 -> bytes(0x81, len)
            else -> bytes(0x82, len shr 8, len and 0xff)
        }
        return byteArrayOf(tag.toByte()) + lengthBytes + content
    }
}
