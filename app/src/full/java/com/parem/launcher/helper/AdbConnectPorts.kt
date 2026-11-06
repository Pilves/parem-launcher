package com.parem.launcher.helper

import java.io.IOException

/** Retry only opening a transport. Authentication and protocol run after this returns. */
object AdbConnectPorts {
    fun <T, R> withFirstConnected(ports: List<Int>, connect: (Int) -> T, use: (T) -> R): R {
        var lastFailure: IOException? = null
        for (port in ports) {
            val connection = try {
                connect(port)
            } catch (e: IOException) {
                lastFailure = e
                continue
            }
            // Do not retry TLS, authentication or protocol failures from use().
            return use(connection)
        }
        throw lastFailure ?: IOException("No connect ports")
    }
}
