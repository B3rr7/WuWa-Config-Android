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
    private const val SCAN_START = 36000
    private const val SCAN_END = 45000
    private const val CONNECT_TIMEOUT = 500
    private const val READ_TIMEOUT = 1500

    private var cachedIp: String? = null

    data class ScanResult(val host: String, val port: Int)

    fun getDeviceIp(): String {
        cachedIp?.let { return it }
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
                        if (ip.startsWith("192.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            cachedIp = ip
                            return ip
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    suspend fun scanForAdb(): ScanResult? = withContext(Dispatchers.IO) {
        val targets = listOf("127.0.0.1")
        for (addr in targets) {
            val port = scanHost(addr)
            if (port > 0) return@withContext ScanResult(addr, port)
        }
        null
    }

    private suspend fun scanHost(host: String): Int = withContext(Dispatchers.IO) {
        val wellKnown = tryPort(host, WELL_KNOWN_ADB)
        if (wellKnown > 0) return@withContext wellKnown

        val range = SCAN_START..SCAN_END
        val batchSize = 20
        range.toList().chunked(batchSize).forEach { batch ->
            val results = coroutineScope {
                batch.map { port ->
                    async { tryPort(host, port) }
                }.awaitAll()
            }
            val found = results.firstOrNull { it > 0 }
            if (found != null) return@withContext found
        }
        0
    }

    private fun tryPort(host: String, port: Int): Int {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT)
                socket.soTimeout = READ_TIMEOUT
                val cnxn = AdbProtocol.createConnectionMessage()
                AdbProtocol.writeMessage(socket.getOutputStream(), cnxn)
                val response = AdbProtocol.readMessage(socket.getInputStream())
                if (response != null && (response.command.contentEquals(AdbProtocol.CNXN) || response.command.contentEquals(AdbProtocol.AUTH))) {
                    port
                } else 0
            }
        } catch (_: Exception) {
            0
        }
    }
}
