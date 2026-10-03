package com.parem.launcher.helper.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.ssl.SSLSockets
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * A minimal adb host that talks to this phone's own adbd over Wireless
 * debugging (Android 11+): pair with the 6-digit code, connect, run one shell
 * command. Blocking; call it off the main thread.
 *
 * Both services are found by mDNS and reached over loopback; a service that
 * resolves to another device's address is skipped.
 */
@RequiresApi(30)
object WirelessAdb {

    enum class Failure { PAIRING_NOT_FOUND, WRONG_CODE, CONNECT_NOT_FOUND, CONNECTION }

    class AdbException(val failure: Failure, cause: Throwable? = null) : IOException(failure.name, cause)

    private const val PAIRING_SERVICE = "_adb-tls-pairing._tcp"
    private const val CONNECT_SERVICE = "_adb-tls-connect._tcp"
    private const val DISCOVERY_TIMEOUT_MS = 8_000L
    private const val IO_TIMEOUT_MS = 10_000
    private const val LOOPBACK = "127.0.0.1"
    private const val KEY_NAME = "Parem"

    /** Pairs with [code], then runs [command] through `shell:` and returns its output. */
    fun pairAndRun(context: Context, code: String, command: String): String {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val ssl = sslContext(keyPair, AdbWire.selfSignedCertificate(keyPair, KEY_NAME))
        val pairPort = discoverOwnPort(context, PAIRING_SERVICE) ?: throw AdbException(Failure.PAIRING_NOT_FOUND)
        pair(ssl, pairPort, code, keyPair)
        val connectPort = discoverOwnPort(context, CONNECT_SERVICE) ?: throw AdbException(Failure.CONNECT_NOT_FOUND)
        return try {
            shell(ssl, connectPort, command)
        } catch (e: IOException) {
            throw AdbException(Failure.CONNECTION, e)
        }
    }

    private fun pair(ssl: SSLContext, port: Int, code: String, keyPair: KeyPair) {
        var sentPeerInfo = false
        try {
            (ssl.socketFactory.createSocket(LOOPBACK, port) as SSLSocket).use { socket ->
                socket.soTimeout = IO_TIMEOUT_MS
                socket.enabledProtocols = arrayOf("TLSv1.3")
                socket.startHandshake()
                // Binds the code to this TLS session, so a relay in the middle fails SPAKE2
                val exported = SSLSockets.exportKeyingMaterial(
                    socket, AdbWire.EXPORTED_KEY_LABEL, null, AdbWire.EXPORTED_KEY_SIZE
                ) ?: throw IOException("no TLS keying material")
                val random = ByteArray(64).also { SecureRandom().nextBytes(it) }
                val spake = Spake2(true, AdbWire.CLIENT_NAME, AdbWire.SERVER_NAME, code.toByteArray(Charsets.UTF_8) + exported, random)
                val out = socket.outputStream
                val input = DataInputStream(socket.inputStream)

                out.write(AdbWire.pairingHeader(AdbWire.PAIR_SPAKE2_MSG, spake.message.size) + spake.message)
                out.flush()
                val theirMsg = AdbWire.readPairingPacket(input, AdbWire.PAIR_SPAKE2_MSG)
                val key = AdbWire.pairingKey(spake.processMessage(theirMsg) ?: throw AdbException(Failure.WRONG_CODE))

                val publicKey = keyPair.public as java.security.interfaces.RSAPublicKey
                val sealed = AdbWire.gcm(key, 0, AdbWire.peerInfo(AdbWire.adbPublicKey(publicKey, KEY_NAME)), encrypt = true)!!
                out.write(AdbWire.pairingHeader(AdbWire.PAIR_PEER_INFO, sealed.size) + sealed)
                out.flush()
                sentPeerInfo = true
                // adbd hangs up here when the code was wrong: it could not open our PeerInfo
                val theirInfo = AdbWire.readPairingPacket(input, AdbWire.PAIR_PEER_INFO)
                AdbWire.gcm(key, 0, theirInfo, encrypt = false) ?: throw AdbException(Failure.WRONG_CODE)
            }
        } catch (e: AdbException) {
            throw e
        } catch (e: IOException) {
            throw AdbException(if (sentPeerInfo) Failure.WRONG_CODE else Failure.CONNECTION, e)
        }
    }

    private fun shell(ssl: SSLContext, port: Int, command: String): String {
        Socket(LOOPBACK, port).use { plain ->
            plain.soTimeout = IO_TIMEOUT_MS
            plain.outputStream.write(AdbWire.message(AdbWire.CNXN, AdbWire.VERSION, AdbWire.MAX_DATA, "host::".toByteArray()))
            val first = AdbWire.readMessage(DataInputStream(plain.inputStream))
            if (first.command != AdbWire.STLS) throw IOException("expected STLS, got ${first.command}")
            plain.outputStream.write(AdbWire.message(AdbWire.STLS, AdbWire.STLS_VERSION, 0))

            (ssl.socketFactory.createSocket(plain, LOOPBACK, port, true) as SSLSocket).use { tls ->
                tls.soTimeout = IO_TIMEOUT_MS
                tls.enabledProtocols = arrayOf("TLSv1.3")
                tls.startHandshake()
                val out = tls.outputStream
                val input = DataInputStream(tls.inputStream)
                if (AdbWire.readMessage(input).command != AdbWire.CNXN) throw IOException("no CNXN after TLS")

                val localId = 1
                out.write(AdbWire.message(AdbWire.OPEN, localId, 0, "shell:$command\u0000".toByteArray()))
                val output = StringBuilder()
                while (true) {
                    val msg = AdbWire.readMessage(input)
                    when (msg.command) {
                        AdbWire.WRTE -> {
                            output.append(String(msg.payload, Charsets.UTF_8))
                            out.write(AdbWire.message(AdbWire.OKAY, localId, msg.arg0))
                        }
                        AdbWire.CLSE -> return output.toString()
                    }
                }
            }
        }
    }

    // ---- mDNS ----

    @Suppress("DEPRECATION") // resolveService/host: the replacements are API 34+
    private fun discoverOwnPort(context: Context, type: String): Int? {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return null
        val found = LinkedBlockingQueue<NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) { found.add(info) }
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        try {
            val deadline = SystemClock.elapsedRealtime() + DISCOVERY_TIMEOUT_MS
            val own = ownAddresses()
            while (true) {
                val left = deadline - SystemClock.elapsedRealtime()
                if (left <= 0) return null
                val info = found.poll(left, TimeUnit.MILLISECONDS) ?: return null
                // One resolve at a time: older NsdManager rejects concurrent ones
                val latch = CountDownLatch(1)
                var resolved: NsdServiceInfo? = null
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) { resolved = serviceInfo; latch.countDown() }
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { latch.countDown() }
                })
                latch.await(left, TimeUnit.MILLISECONDS)
                val r = resolved ?: continue
                val host = r.host ?: continue
                if (own.any { it.address.contentEquals(host.address) }) return r.port
            }
        } finally {
            try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) {}
        }
    }

    private fun ownAddresses(): List<InetAddress> = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().flatMap { it.inetAddresses.toList() }
    } catch (_: Exception) {
        emptyList()
    }

    // ---- TLS ----

    private fun sslContext(keyPair: KeyPair, cert: X509Certificate): SSLContext {
        val alias = "parem"
        // Always offer our key: adbd lists its paired keys as acceptable issuers,
        // which a stock key manager would match against and then send nothing
        val keyManager = object : X509ExtendedKeyManager() {
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = alias
            override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = alias
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(alias)
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
            override fun getCertificateChain(a: String?) = arrayOf(cert)
            override fun getPrivateKey(a: String?): PrivateKey = keyPair.private
        }
        // adbd's certificate is self-signed and unknowable in advance, so there is
        // nothing to validate it against. Pairing authenticates the peer instead
        // (the code and this session's exported key both feed SPAKE2), and the
        // connection is loopback-only and carries a single `pm grant`.
        val trustManager = object : X509TrustManager {
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain.isNullOrEmpty()) throw CertificateException("adbd sent no certificate")
            }
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException("client mode only")
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply { init(arrayOf(keyManager), arrayOf(trustManager), SecureRandom()) }
    }
}
