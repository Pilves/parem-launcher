package com.parem.launcher.helper.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64

class AdbWireTest {

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private val keyPair by lazy { KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() }

    @Test
    fun hkdfMatchesRfc5869EmptySaltCase() {
        // RFC 5869 test case 3: zero-length salt and info
        val okm = AdbWire.hkdfSha256(ByteArray(22) { 0x0b }, ByteArray(0), 42)
        assertArrayEquals(
            hex("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"),
            okm
        )
    }

    @Test
    fun gcmUsesTheCounterAsNonce() {
        val key = ByteArray(16) { it.toByte() }
        val sealed = AdbWire.gcm(key, 0, "hello".toByteArray(), encrypt = true)!!
        assertEquals(5 + 16, sealed.size)
        assertArrayEquals("hello".toByteArray(), AdbWire.gcm(key, 0, sealed, encrypt = false))
        assertNull(AdbWire.gcm(key, 1, sealed, encrypt = false))
        sealed[0] = (sealed[0].toInt() xor 1).toByte()
        assertNull(AdbWire.gcm(key, 0, sealed, encrypt = false))
    }

    @Test
    fun pairingRoundTripBetweenBothRoles() {
        val password = "482910".toByteArray() + ByteArray(64) { 7 }
        val client = Spake2(true, AdbWire.CLIENT_NAME, AdbWire.SERVER_NAME, password, ByteArray(64) { 3 })
        val server = Spake2(false, AdbWire.SERVER_NAME, AdbWire.CLIENT_NAME, password, ByteArray(64) { 9 })
        val clientKey = AdbWire.pairingKey(client.processMessage(server.message)!!)
        val serverKey = AdbWire.pairingKey(server.processMessage(client.message)!!)
        val info = AdbWire.peerInfo(AdbWire.adbPublicKey(keyPair.public as RSAPublicKey, "Parem"))
        val opened = AdbWire.gcm(serverKey, 0, AdbWire.gcm(clientKey, 0, info, encrypt = true)!!, encrypt = false)
        assertArrayEquals(info, opened)
    }

    @Test
    fun peerInfoIsTypeByteThenZeroPaddedKey() {
        val info = AdbWire.peerInfo(byteArrayOf(1, 2, 3))
        assertEquals(8192, info.size)
        assertEquals(0, info[0].toInt())
        assertArrayEquals(byteArrayOf(1, 2, 3), info.copyOfRange(1, 4))
        assertTrue(info.drop(4).all { it == 0.toByte() })
    }

    @Test
    fun pairingHeaderIsBigEndian() {
        assertArrayEquals(byteArrayOf(1, 1, 0, 0, 0x20, 0x10), AdbWire.pairingHeader(AdbWire.PAIR_PEER_INFO, 8208))
        val packet = AdbWire.pairingHeader(AdbWire.PAIR_SPAKE2_MSG, 2) + byteArrayOf(5, 6)
        assertArrayEquals(
            byteArrayOf(5, 6),
            AdbWire.readPairingPacket(DataInputStream(ByteArrayInputStream(packet)), AdbWire.PAIR_SPAKE2_MSG)
        )
    }

    @Test(expected = java.io.IOException::class)
    fun pairingPacketOfTheWrongTypeIsRejected() {
        val packet = AdbWire.pairingHeader(AdbWire.PAIR_PEER_INFO, 1) + byteArrayOf(0)
        AdbWire.readPairingPacket(DataInputStream(ByteArrayInputStream(packet)), AdbWire.PAIR_SPAKE2_MSG)
    }

    @Test
    fun messageRoundTrip() {
        val bytes = AdbWire.message(AdbWire.OPEN, 1, 0, "shell:id\u0000".toByteArray())
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(AdbWire.OPEN, header.getInt(0))
        assertEquals(AdbWire.OPEN.inv(), header.getInt(20))
        assertEquals("shell:id\u0000".toByteArray().sumOf { it.toInt() and 0xff }, header.getInt(16))
        val msg = AdbWire.readMessage(DataInputStream(ByteArrayInputStream(bytes)))
        assertEquals(AdbWire.OPEN, msg.command)
        assertEquals(1, msg.arg0)
        assertEquals("shell:id\u0000", String(msg.payload))
    }

    @Test(expected = java.io.IOException::class)
    fun messageWithBadMagicIsRejected() {
        val bytes = AdbWire.message(AdbWire.OKAY, 1, 2)
        bytes[20] = 0
        AdbWire.readMessage(DataInputStream(ByteArrayInputStream(bytes)))
    }

    @Test
    fun adbPublicKeyIsAndroidPubkeyStruct() {
        val pub = keyPair.public as RSAPublicKey
        val text = String(AdbWire.adbPublicKey(pub, "Parem"), Charsets.UTF_8)
        assertTrue(text.endsWith(" Parem\u0000"))
        val raw = Base64.getDecoder().decode(text.substringBefore(' '))
        assertEquals(4 + 4 + 256 + 256 + 4, raw.size)
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(64, buf.getInt(0))
        val n0inv = BigInteger.valueOf(buf.getInt(4).toLong() and 0xffffffffL)
        val r32 = BigInteger.ONE.shiftLeft(32)
        // n0inv = -1 / n mod 2^32
        assertEquals(r32 - BigInteger.ONE, (n0inv * pub.modulus).mod(r32))
        val modulus = BigInteger(1, raw.copyOfRange(8, 8 + 256).reversedArray())
        assertEquals(pub.modulus, modulus)
        val rr = BigInteger(1, raw.copyOfRange(264, 264 + 256).reversedArray())
        assertEquals(BigInteger.ONE.shiftLeft(4096).mod(pub.modulus), rr)
        assertEquals(pub.publicExponent.toInt(), buf.getInt(520))
    }

    @Test
    fun selfSignedCertificateParsesAndVerifies() {
        val cert = AdbWire.selfSignedCertificate(keyPair, "Parem")
        cert.verify(keyPair.public)
        assertEquals(keyPair.public, cert.publicKey)
        assertEquals(3, cert.version)
        assertTrue(cert.subjectX500Principal.name.contains("CN=Parem"))
        assertEquals("SHA256withRSA", cert.sigAlgName)
    }
}
