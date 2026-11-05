package com.parem.launcher.helper.adb

import java.math.BigInteger
import java.security.MessageDigest

/**
 * SPAKE2 over edwards25519, byte-compatible with BoringSSL's `SPAKE2_*`, which
 * is what adbd's wireless-debugging pairing runs. BoringSSL's quirks are kept
 * on purpose: the ephemeral scalar is multiplied by the cofactor, the password
 * scalar gets the "add multiples of the order" hack, and the transcript hashes
 * the full SHA-512 of the password rather than the scalar.
 *
 * BigInteger arithmetic, so not constant time. It runs once, against the
 * phone's own adbd over loopback, while the user holds the pairing dialog open.
 *
 * [privateInput] is 64 random bytes; tests pass fixed ones.
 */
class Spake2(
    private val isAlice: Boolean,
    private val myName: ByteArray,
    private val theirName: ByteArray,
    password: ByteArray,
    privateInput: ByteArray,
) {
    private val privateScalar: BigInteger
    private val passwordHash: ByteArray = MessageDigest.getInstance("SHA-512").digest(password)
    private val passwordScalar: BigInteger

    /** The 32-byte message to send to the peer. */
    val message: ByteArray

    init {
        require(privateInput.size == 64) { "privateInput must be 64 bytes" }
        privateScalar = leToInt(privateInput).mod(L).shiftLeft(3)
        var w = leToInt(passwordHash).mod(L)
        // BoringSSL makes the password scalar a multiple of 8 by adding l, 2l, 4l
        // as needed. M and N are not in the prime-order subgroup, so the exact
        // multiple changes the point and must match.
        if (w.testBit(0)) w += L
        if (w.testBit(1)) w += L.shiftLeft(1)
        if (w.testBit(2)) w += L.shiftLeft(2)
        passwordScalar = w
        val mask = mul(passwordScalar, if (isAlice) M else N)
        message = encode(add(mul(privateScalar, B), mask))
    }

    /** The 64-byte shared key, or null when [theirMessage] is not a curve point. */
    fun processMessage(theirMessage: ByteArray): ByteArray? {
        if (theirMessage.size != 32) return null
        val q = decode(theirMessage) ?: return null
        val peersMask = mul(passwordScalar, if (isAlice) N else M)
        val shared = encode(mul(privateScalar, add(q, negate(peersMask))))
        val sha = MessageDigest.getInstance("SHA-512")
        fun put(data: ByteArray) {
            val len = ByteArray(8)
            var l = data.size.toLong()
            for (i in 0 until 8) { len[i] = (l and 0xff).toByte(); l = l shr 8 }
            sha.update(len)
            sha.update(data)
        }
        if (isAlice) {
            put(myName); put(theirName); put(message); put(theirMessage)
        } else {
            put(theirName); put(myName); put(theirMessage); put(message)
        }
        put(shared)
        put(passwordHash)
        return sha.digest()
    }

    /** Extended homogeneous coordinates: x = X/Z, y = Y/Z, xy = T/Z. */
    private class Point(val x: BigInteger, val y: BigInteger, val z: BigInteger, val t: BigInteger)

    companion object {
        private val P: BigInteger = BigInteger.ONE.shiftLeft(255) - BigInteger.valueOf(19)
        private val L: BigInteger = BigInteger.ONE.shiftLeft(252) + BigInteger("27742317777372353535851937790883648493")
        private val D: BigInteger = (BigInteger.valueOf(-121665) * BigInteger.valueOf(121666).modInverse(P)).mod(P)
        private val D2: BigInteger = D.shiftLeft(1).mod(P)
        private val SQRT_M1: BigInteger = BigInteger.valueOf(2).modPow((P - BigInteger.ONE).shiftRight(2), P)
        private val IDENTITY = Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)

        private val B = decode(hex("5866666666666666666666666666666666666666666666666666666666666666"))!!
        // From BoringSSL spake25519.cc (generated from the seeds
        // "edwards25519 point generation seed (M)" / "(N)").
        private val M = decode(hex("5ada7e4bf6ddd9adb6626d32131c6b5c51a1e347a3478f53cfcf441b88eed12e"))!!
        private val N = decode(hex("10e3df0ae37d8e7a99b5fe74b44672103dbddcbd06af680d71329a11693bc778"))!!

        private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

        private fun leToInt(bytes: ByteArray) = BigInteger(1, bytes.reversedArray())

        // Unified twisted-Edwards addition (a = -1), valid for doubling too.
        private fun add(p: Point, q: Point): Point {
            val a = (p.y - p.x) * (q.y - q.x) % P
            val b = (p.y + p.x) * (q.y + q.x) % P
            val c = p.t * D2 % P * q.t % P
            val d = p.z * q.z * BigInteger.valueOf(2) % P
            val e = b - a
            val f = d - c
            val g = d + c
            val h = b + a
            return Point((e * f).mod(P), (g * h).mod(P), (f * g).mod(P), (e * h).mod(P))
        }

        private fun negate(p: Point) = Point((P - p.x).mod(P), p.y, p.z, (P - p.t).mod(P))

        private fun mul(k: BigInteger, p: Point): Point {
            var r = IDENTITY
            for (i in k.bitLength() - 1 downTo 0) {
                r = add(r, r)
                if (k.testBit(i)) r = add(r, p)
            }
            return r
        }

        private fun encode(p: Point): ByteArray {
            val zInv = p.z.modInverse(P)
            val x = p.x * zInv % P
            val y = p.y * zInv % P
            val be = y.toByteArray()
            val out = ByteArray(32)
            for (i in be.indices) {
                val j = be.size - 1 - i
                if (j < 32) out[j] = be[i]
            }
            if (x.testBit(0)) out[31] = (out[31].toInt() or 0x80).toByte()
            return out
        }

        /** Like BoringSSL's ge_frombytes_vartime: a non-canonical y is reduced, not rejected. */
        private fun decode(bytes: ByteArray): Point? {
            val sign = (bytes[31].toInt() shr 7) and 1
            val yBytes = bytes.copyOf()
            yBytes[31] = (yBytes[31].toInt() and 0x7f).toByte()
            val y = leToInt(yBytes).mod(P)
            val yy = y * y % P
            val u = (yy - BigInteger.ONE).mod(P)
            val v = (D * yy + BigInteger.ONE).mod(P)
            val v3 = v * v % P * v % P
            val v7 = v3 * v3 % P * v % P
            var x = u * v3 % P * (u * v7 % P).modPow((P - BigInteger.valueOf(5)).shiftRight(3), P) % P
            val vxx = v * x % P * x % P
            if (vxx != u) {
                if (vxx != (P - u).mod(P)) return null
                x = x * SQRT_M1 % P
            }
            if ((if (x.testBit(0)) 1 else 0) != sign) x = (P - x).mod(P)
            return Point(x, y, BigInteger.ONE, x * y % P)
        }
    }
}
