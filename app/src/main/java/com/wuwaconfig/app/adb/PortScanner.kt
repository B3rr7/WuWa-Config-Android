package com.wuwaconfig.app.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

object PortScanner {
    private const val MIN_PORT = 35000
    private const val MAX_PORT = 46000
    private const val CONNECT_TIMEOUT = 200
    private const val MAX_CONCURRENT = 50

    suspend fun scanForAdb(): Int = withContext(Dispatchers.IO) {
        val foundPort = AtomicInteger(0)
        val semaphore = Semaphore(MAX_CONCURRENT)

        coroutineScope {
            for (port in MIN_PORT..MAX_PORT) {
                if (foundPort.get() > 0) break
                launch {
                    semaphore.withPermit {
                        if (foundPort.get() > 0) return@withPermit
                        if (isAdbPort(port)) {
                            foundPort.compareAndSet(0, port)
                        }
                    }
                }
            }
        }

        foundPort.get()
    }

    private fun isAdbPort(port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT)
                val output = socket.getOutputStream()
                val input = socket.getInputStream()
                val cnxn = AdbProtocol.createConnectionMessage()
                AdbProtocol.writeMessage(output, cnxn)
                val response = AdbProtocol.readMessage(input)
                response != null && (response.command.contentEquals(AdbProtocol.CNXN) || response.command.contentEquals(AdbProtocol.AUTH))
            }
        } catch (_: Exception) {
            false
        }
    }
}
