package com.wuwaconfig.app.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

object PortScanner {
    private const val WELL_KNOWN_ADB = 5555
    private const val SCAN_START = 37000
    private const val SCAN_END = 44000
    private const val CONNECT_TIMEOUT = 300
    private const val READ_TIMEOUT = 500

    private var cachedIp: String? = null
    private var cacheTimestamp: Long = 0
    private const val CACHE_TTL_MS = 30_000L

    @JvmStatic var lastAdbPort: Int? = null
        private set

    fun clearCache() {
        cachedIp = null
        cacheTimestamp = 0
    }

    data class ScanResult(val host: String, val port: Int)

    fun getDeviceIp(): String {
        if (cachedIp != null && System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) return cachedIp!!
        cachedIp = null
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
                            cachedIp = ip
                            cacheTimestamp = System.currentTimeMillis()
                            return ip
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return "127.0.0.1"
    }

    suspend fun scanForAdb(): ScanResult? =
        withContext(Dispatchers.IO) {
            val addrs = listOf("127.0.0.1", getDeviceIp()).distinct()
            for (addr in addrs) {
                lastAdbPort?.let { port ->
                    if (tryPort(addr, port) > 0) return@withContext ScanResult(addr, port)
                }
                if (tryPort(addr, WELL_KNOWN_ADB) > 0) return@withContext ScanResult(addr, WELL_KNOWN_ADB)
                val port = scanHost(addr)
                if (port > 0) {
                    lastAdbPort = port
                    return@withContext ScanResult(addr, port)
                }
            }
            null
        }

    private suspend fun scanHost(host: String): Int =
        withContext(Dispatchers.IO) {
            val range = SCAN_START..SCAN_END
            val batchSize = 50
            val totalPorts = range.toList()
            val startTime = System.currentTimeMillis()
            val MAX_SCAN_MS = 20_000L
            for (batch in totalPorts.chunked(batchSize)) {
                if (System.currentTimeMillis() - startTime > MAX_SCAN_MS) break
                val results =
                    coroutineScope {
                        batch.map { port ->
                            async { tryPort(host, port) }
                        }.awaitAll()
                    }
                val found = results.firstOrNull { it > 0 }
                if (found != null) return@withContext found
            }
            0
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
                if (response != null && (response.command.contentEquals(AdbProtocol.CNXN) || response.command.contentEquals(AdbProtocol.AUTH))) {
                    port
                } else {
                    0
                }
            }
        } catch (_: Exception) {
            0
        }
    }
}
