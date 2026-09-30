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

    /** How every genuine adbd introduces its banner; see [describeBanner]. */
    private const val DEVICE_BANNER_PREFIX = "device::"

    /**
     * The one address where an unauthenticated adbd banner is safe to trust.
     *
     * Only this device can bind it, so an answer cannot have come from the network.
     */
    const val LOOPBACK = "127.0.0.1"

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

    /**
     * Set when a port answered as adbd but could not be *proved* to be adbd,
     * because it declined to issue an authentication challenge.
     *
     * Surfaced so a miss can say why. Without it, a rooted or custom-ROM device
     * whose adbd runs with `ro.adb.secure=0` reports the same bare "ADB port not
     * found" as a phone with wireless debugging switched off, and the only way to
     * tell them apart is a logcat line the user cannot see.
     */
    @Volatile
    var unauthenticatedAdbHint: String? = null
        private set

    /**
     * Outcome of probing one port.
     *
     * [Unauthenticated] is a distinct case rather than a failure because the
     * difference decides what the user is told. adbd with `ro.adb.secure=0`
     * (rooted and custom ROMs, including this project's own test device) replies
     * straight to a CNXN with its own CNXN banner instead of an AUTH challenge.
     * That banner is unauthenticated and therefore forgeable by anything that can
     * bind the port, which is why [isAuthChallenge] rejects it — see the note
     * there. So the port is reported, not acted on, and the user is pointed at
     * the manual route.
     */
    private enum class Probe {
        /** Nothing listening, or the connection failed. */
        Dead,

        /** A genuine AUTH/AUTH_TOKEN challenge. Safe to hand the public key to. */
        Verified,

        /** Answered with a CNXN banner and no challenge. Cannot be trusted. */
        Unauthenticated,
    }

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

    /**
     * Loopback only. Backs [com.wuwaconfig.app.backend.LocalAdbBackend].
     *
     * Exposed separately from [scanForAdb] so "no LAN address" is a structural
     * property of the caller rather than a runtime argument. Sharing one entry
     * point meant every caller had to remember to pass the right flag.
     */
    suspend fun scanLoopback(): ScanResult? =
        withContext(Dispatchers.IO) {
            unauthenticatedAdbHint = null
            probeAddress(LOOPBACK, loopback = true, deadlineMs = System.currentTimeMillis() + OVERALL_SCAN_MS)
        }

    suspend fun scanForAdb(): ScanResult? =
        withContext(Dispatchers.IO) {
            unauthenticatedAdbHint = null
            val deadline = System.currentTimeMillis() + OVERALL_SCAN_MS
            // Loopback first: it is both the cheapest probe and, on a rooted device
            // running its own adbd, the only address that can succeed at all.
            val addrs = listOf(LOOPBACK, getDeviceIp()).distinct()
            for (addr in addrs) {
                val found = probeAddress(addr, addr == LOOPBACK, deadline)
                if (found != null) return@withContext found
            }
            null
        }

    /** Probes one address: remembered port, then the well-known port, then the sweep. */
    private suspend fun probeAddress(
        addr: String,
        loopback: Boolean,
        deadlineMs: Long,
    ): ScanResult? {
        lastAdbPort?.let { port ->
            if (probe(addr, port, loopback) == Probe.Verified) {
                lastAdbPort = port
                return ScanResult(addr, port)
            }
        }
        if (probe(addr, WELL_KNOWN_ADB, loopback) == Probe.Verified) {
            // Remembered so the next scan probes this port first.
            lastAdbPort = WELL_KNOWN_ADB
            return ScanResult(addr, WELL_KNOWN_ADB)
        }
        val port = scanHost(addr, deadlineMs, loopback)
        if (port > 0) {
            lastAdbPort = port
            return ScanResult(addr, port)
        }
        return null
    }

    private suspend fun scanHost(
        host: String,
        deadlineMs: Long,
        loopback: Boolean,
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
                            semaphore.withPermit { probe(host, port, loopback) }
                        }
                    }.awaitAll()
                }
            val found = results.firstOrNull { it == Probe.Verified }
            if (found != null) {
                return batch[results.indexOf(found)]
            }
        }
        return 0
    }

    /**
     * Probes one port and classifies the answer.
     *
     * A peer that answers with `CNXN` instead of an `AUTH` challenge is running
     * adbd with `ro.adb.secure=0` — the normal state on rooted and custom ROMs,
     * and the state this app's own test phone is in.
     *
     * Whether that is enough to connect depends entirely on *where* it came from:
     *
     *  - **Loopback** — nothing but this app and adbd can hold `127.0.0.1:port`;
     *    binding one needs the same root that already gives you the device. There
     *    is no third party to impersonate, so the banner is accepted. This is what
     *    restores auto-connect on a rooted phone, and it is strictly safer than the
     *    Wi-Fi address it replaces, since no traffic leaves the device at all.
     *
     *  - **LAN** — anything on the network can bind the port and answer, so a
     *    banner proves nothing. [isAuthChallenge] rejects it, as it must: accepting
     *    one would hand the app's RSA public key to whatever answered. Recovered
     *    from the 7bf945b hardening, which broke auto-connect on every rooted
     *    device and was reported as "it worked in v1.1.4".
     */
    private fun probe(
        host: String,
        port: Int,
        loopback: Boolean,
    ): Probe {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT)
                socket.soTimeout = READ_TIMEOUT
                val cnxn = AdbProtocol.createConnectionMessage("host::")
                AdbProtocol.writeMessage(socket.getOutputStream(), cnxn)
                val response = AdbProtocol.readMessage(socket.getInputStream())
                when {
                    isAuthChallenge(response) -> Probe.Verified
                    response != null &&
                        response.command.contentEquals(AdbProtocol.CNXN) &&
                        response.payload.isNotEmpty() -> {
                        val who = describeBanner(response.payload)
                        if (who == null) {
                            Probe.Dead
                        } else if (loopback) {
                            Log.d(TAG, "$host:$port: accepting unauthenticated $who (loopback)")
                            Probe.Verified
                        } else {
                            Log.d(TAG, "$host:$port answered CNXN without an auth challenge: $who")
                            // First credible find wins: the sweep runs to 44000 and will
                            // happily walk past the real device onto a transient listener,
                            // and a later worse guess must not overwrite the answer the
                            // user is about to be told to type in.
                            if (unauthenticatedAdbHint == null) {
                                unauthenticatedAdbHint =
                                    "An unauthenticated ADB-like service answered on port $port" +
                                    " ($who). Rooted and custom ROMs often run ADB without" +
                                    " authentication, which this app will not use" +
                                    " over Wi-Fi automatically. Use Local ADB, or tap" +
                                    " Manual and enter $host:$port to connect."
                            }
                            Probe.Unauthenticated
                        }
                    }
                    else -> {
                        Log.d(
                            TAG,
                            "rejecting $host:$port: ${response?.let { AdbProtocol.hex(it.command) } ?: "no response"}",
                        )
                        Probe.Dead
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "probe $host:$port failed: $e")
            Probe.Dead
        }
    }

/**
     * Names the peer, or null if the reply is not credible enough to tell a user about.
     *
     * Two filters, both learned on-device:
     *
     *  - The banner must start with `device::`. A genuine adbd identifies itself that
     *    way. Anything else is not evidence of a phone — a scan of 37000..44000 will
     *    cross transient listeners, and one of them answered with a `host::` banner
     *    and nearly sent the user to a port that was already closed by the time they
     *    looked. Silence beats a confident wrong answer here.
     *
     *  - Within a real banner, `ro.product.model` is what tells the user "yes, that is
     *    my phone". The full banner runs to several hundred characters and truncates
     *    mid-word in a toast.
     */
    internal fun describeBanner(payload: ByteArray): String? {
        val banner = String(payload, Charsets.UTF_8).trim()
        if (!banner.startsWith(DEVICE_BANNER_PREFIX)) return null
        val model =
            banner.removePrefix(DEVICE_BANNER_PREFIX)
                .split(';')
                .firstOrNull { it.startsWith("ro.product.model=") }
                ?.substringAfter('=')
                ?.trim()
        return model?.takeIf { it.isNotEmpty() }?.let { "device $it" } ?: "an unnamed device"
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
