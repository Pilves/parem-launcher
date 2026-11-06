package com.parem.launcher.helper

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class AdbConnectPortsTest {
    @Test fun staleFirstThenLive() {
        val attempts = mutableListOf<Int>()
        val result = AdbConnectPorts.withFirstConnected(listOf(38149, 42287), connect = { port ->
            attempts += port
            if (port == 38149) throw IOException("refused")
            "connected"
        }) { it }
        assertEquals("connected", result)
        assertEquals(listOf(38149, 42287), attempts)
    }

    @Test fun allStalePropagatesLastConnectionFailure() {
        val attempts = mutableListOf<Int>()
        val last = IOException("last refused")
        try {
            AdbConnectPorts.withFirstConnected<Int, Int>(listOf(1, 2), connect = { port ->
                attempts += port
                throw if (port == 2) last else IOException("first refused")
            }) { it }
            fail("Expected a connection failure")
        } catch (e: IOException) {
            assertSame(last, e)
        }
        assertEquals(listOf(1, 2), attempts)
    }

    @Test fun oneLivePortIsUsedOnce() {
        val attempts = mutableListOf<Int>()
        assertEquals(1234, AdbConnectPorts.withFirstConnected(listOf(1234), connect = { port ->
            attempts += port
            port
        }) { it })
        assertEquals(listOf(1234), attempts)
    }

    @Test fun nonConnectionFailureIsNotRetried() {
        val attempts = mutableListOf<Int>()
        try {
            AdbConnectPorts.withFirstConnected<Int, Int>(listOf(1, 2), connect = { port ->
                attempts += port
                throw SecurityException("authentication")
            }) { it }
            fail("Expected authentication failure")
        } catch (_: SecurityException) { }
        assertEquals(listOf(1), attempts)
    }

    @Test fun protocolIoFailureAfterConnectionIsNotRetried() {
        val attempts = mutableListOf<Int>()
        val protocolFailure = IOException("TLS or protocol")
        try {
            AdbConnectPorts.withFirstConnected<Int, Int>(listOf(1, 2), connect = { port ->
                attempts += port
                port
            }) { throw protocolFailure }
            fail("Expected protocol failure")
        } catch (e: IOException) {
            assertSame(protocolFailure, e)
        }
        assertEquals(listOf(1), attempts)
    }
}
