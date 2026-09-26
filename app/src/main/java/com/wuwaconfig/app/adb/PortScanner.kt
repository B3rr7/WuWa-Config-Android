package com.wuwaconfig.app.adb

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

object PortScanner {
    private const val TAG = "PortScanner"
    private const val WELL_KNOWN_ADB = 5555
    private const val SCAN_START = 37000
    private const val SCAN_END = 44000
    private const val CONNECT_TIMEOUT = 300
    private const val READ_TIMEOUT = 500

    /** A real adbd's AUTH challenge is always a 20-byte token. */
    private const val AUTH_TOKEN_BYTES = 20

    /**
     * IP + expiry travel together: as two independent @Volatile fields a reader could see a
     * new IP with a stale (zero) timestamp and miss the TTL, re-enumerating interfaces.
     */
    private class IpCacheEntry(
        val ip: String,
        val expiresAt: Long,
    )

    private val ipCache = AtomicReference<IpCacheEntry?>(null)
    private const val CACHE_TTL_MS = 30_000L

    /** Shorter TTL for the "no interface found" fallback so a real IP is picked up quickly. */
    private const val FALLBACK_CACHE_TTL_MS = 5_000L

    /** Single budget for the whole sweep, both hosts included. */
    private const val OVERALL_SCAN_MS = 20_000L

    @JvmStatic
    @Volatile
    var lastAdbPort: Int? = null
        private set

    data class ScanResult(val host: String, val port: Int)

    fun getDeviceIp(): String {
        val now = System.currentTimeMillis()
        val cached = ipCache.get()
        if (cached != null && now < cached.expiresAt) return cached.ip
        var found: String? = null
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        val parts = ip.split(".")
                        val isPrivate172 = parts.size == 4 && parts[0] == "172" && parts[1].toIntOrNull()?.let { it in 16..31 } == true
                        if (ip.startsWith("192.") || ip.startsWith("10.") || isPrivate172) {
                            found = ip
                            break
                        }
                    }
                }
                if (found != null) break
            }
        } catch (e: Exception) {
            Log.d(TAG, "getDeviceIp: interface enumeration failed: $e")
        }
        // The fallback is cached too (with a shorter TTL): re-enumerating on every miss was
        // pure waste while the device was offline.
        val ip = found ?: "127.0.0.1"
        val ttl = if (found == null) FALLBACK_CACHE_TTL_MS else CACHE_TTL_MS
        ipCache.set(IpCacheEntry(ip, System.currentTimeMillis() + ttl))
        return ip
    }

    suspend fun scanForAdb(): ScanResult? =
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + OVERALL_SCAN_MS
            val addrs = listOf("127.0.0.1", getDeviceIp()).distinct()
            for (addr in addrs) {
                lastAdbPort?.let { port ->
                    if (tryPort(addr, port) > 0) {
                        lastAdbPort = port
                        return@withContext ScanResult(addr, port)
                    }
                }
                if (tryPort(addr, WELL_KNOWN_ADB) > 0) {
                    // Remembered so the next scan probes this port first.
                    lastAdbPort = WELL_KNOWN_ADB
                    return@withContext ScanResult(addr, WELL_KNOWN_ADB)
                }
                val port = scanHost(addr, deadline)
                if (port > 0) {
                    lastAdbPort = port
                    return@withContext ScanResult(addr, port)
                }
            }
            null
        }

    private suspend fun scanHost(
        host: String,
        deadlineMs: Long,
    ): Int {
        val batchSize = 50
        val concurrency = 20
        val semaphore = Semaphore(concurrency)
        for (batch in (SCAN_START..SCAN_END).chunked(batchSize)) {
            // One deadline for the entire scan: previously each host got its own 20s, so a
            // two-host miss could take ~45s.
            if (System.currentTimeMillis() > deadlineMs) break
            val results =
                coroutineScope {
                    batch.map { port ->
                        async {
                            semaphore.withPermit { tryPort(host, port) }
                        }
                    }.awaitAll()
                }
            val found = results.firstOrNull { it > 0 }
            if (found != null) return found
        }
        return 0
    }

    private fun tryPort(
        host: String,
        port: Int,
    ): Int {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT)
                socket.soTimeout = READ_TIMEOUT
                val cnxn = AdbProtocol.createConnectionMessage("host::")
                AdbProtocol.writeMessage(socket.getOutputStream(), cnxn)
                val response = AdbProtocol.readMessage(socket.getInputStream())
                if (isAuthChallenge(response)) {
                    port
                } else {
                    Log.d(TAG, "rejecting $host:$port: ${response?.let { AdbProtocol.hex(it.command) } ?: "no response"}")
                    0
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "tryPort $host:$port failed: $e")
            0
        }
    }

    /**
     * True only for a genuine adbd authentication challenge.
     *
     * Any TCP listener on the LAN can answer, so a bare `CNXN` is worthless as an
     * identity check — accepting one meant the app handed its RSA PUBLIC KEY to an
     * unknown peer during the scan. A real adbd always replies to our connect with
     * `AUTH`, `arg0 == AUTH_TOKEN`, and a 20-byte random challenge.
     *
     * Extracted as an internal top-level function (rather than left inline in
     * [tryPort]) precisely so it is reachable from a pure-JVM unit test.
     */
    internal fun isAuthChallenge(message: AdbProtocol.AdbMessage?): Boolean =
        message != null &&
            message.command.contentEquals(AdbProtocol.AUTH) &&
            message.arg0 == AdbProtocol.AUTH_TOKEN &&
            message.payload.size == AUTH_TOKEN_BYTES
}
