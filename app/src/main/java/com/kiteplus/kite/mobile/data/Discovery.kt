package com.kiteplus.kite.mobile.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/**
 * Finds the copy of Kite running on the same network.
 *
 * A laptop's address is a DHCP lease: it changes when the router feels like it,
 * and every change used to mean typing a new IP on the phone. Kite answers one
 * unauthenticated question (`GET /api/v1/setup`), so the app looks for it.
 *
 * Every subnet this phone is on is swept at once, not just the most likely one:
 * a phone can hold a hotspot address and a home address at the same time, and
 * guessing wrong used to mean finding nothing while the server sat one network
 * over, reachable. The sweep stops at the first answer.
 */
object Discovery {

    /** Short enough to sweep a subnet, long enough for a wifi round trip. */
    private val client = OkHttpClient.Builder()
        .connectTimeout(300, TimeUnit.MILLISECONDS)
        .readTimeout(700, TimeUnit.MILLISECONDS)
        .callTimeout(1, TimeUnit.SECONDS)
        .build()

    private const val PORT = 1717
    private const val CONCURRENCY = 64

    /** Long enough for 254 hosts at this concurrency, short enough to wait for. */
    private const val BUDGET_MS = 8_000L

    /**
     * @param preferredPort the port in the address the author already typed, so
     *   a server on a non-default port can be found too.
     * @return `http://<ip>:<port>` for a host that answers like Kite, or null
     *   when nothing on these networks does.
     */
    suspend fun findServer(preferredPort: Int? = null): DiscoveryResult = withContext(Dispatchers.IO) {
        val addresses = lanAddresses()
        val prefixes = prefixesOf(addresses)
        if (prefixes.isEmpty()) {
            return@withContext DiscoveryResult(found = null, localAddresses = addresses, scanned = emptyList())
        }

        val ports = listOfNotNull(preferredPort?.takeIf { it in 1..65535 }, PORT).distinct()
        val hosts = prefixes.flatMap { prefix -> (1..254).map { "$prefix.$it" } }

        val winner = CompletableDeferred<String?>()
        withTimeoutOrNull(BUDGET_MS) {
            coroutineScope {
                val gate = Semaphore(CONCURRENCY)
                val jobs = hosts.flatMap { host ->
                    ports.map { port ->
                        async {
                            // Nothing new starts once something has answered.
                            if (winner.isCompleted) return@async
                            gate.withPermit {
                                if (!winner.isCompleted && probe(host, port)) {
                                    winner.complete("http://$host:$port")
                                }
                            }
                        }
                    }
                }
                jobs.awaitAll()
                if (!winner.isCompleted) winner.complete(null)
            }
        }
        // A sweep that ran out of time leaves nothing completed, which is a
        // failure to find rather than a reason to wait forever.
        val found = if (winner.isCompleted) winner.getCompleted() else null
        DiscoveryResult(found = found, localAddresses = addresses, scanned = prefixes)
    }


    /** True when the host answers the setup question with Kite's own shape. */
    private fun probe(host: String, port: Int): Boolean = runCatching {
        val request = Request.Builder()
            .url("http://$host:$port/api/v1/setup")
            .build()
        client.newCall(request).execute().use { response ->
            // Matching the body keeps a random web server on the port from
            // passing for the article store.
            response.isSuccessful && response.body?.string()?.contains("\"required\"") == true
        }
    }.getOrDefault(false)

    /**
     * Every /24 this phone holds an address on, most likely first.
     *
     * Addresses are filtered and ordered by what they can possibly be: a
     * 169.254 address means the network never handed out a lease, a loopback
     * address leads back to the phone, and the RFC 1918 ranges are ranked in
     * the order home routers usually hand them out.
     */
    /** Every IPv4 address this phone holds, most likely to reach a laptop first. */
    private suspend fun lanAddresses(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
                // Wi-Fi and wired interfaces first: a VPN or a cellular address
                // is rarely where the laptop sits.
                .sortedBy { iface ->
                    val name = iface.name.lowercase()
                    when {
                        name.startsWith("wlan") || name.startsWith("wi-fi") || name.startsWith("wifi") -> 0
                        name.startsWith("eth") -> 1
                        else -> 2
                    }
                }
                .flatMap { iface -> iface.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList())
    }
}

/**
 * The /24 prefixes worth sweeping, given the IPv4 addresses this device holds.
 *
 * Pure and internal so the ordering rules can be checked without a network:
 * 192.168 first, then 10/8, then 172.16/12, duplicates collapsed.
 */
internal fun prefixesOf(addresses: List<String>): List<String> {
    data class Ranked(val prefix: String, val rank: Int)

    val ranked = addresses.mapNotNull { raw ->
        val parts = raw.split('.')
        if (parts.size != 4) return@mapNotNull null
        val octets = parts.map { it.toIntOrNull() ?: return@mapNotNull null }
        if (octets.any { it !in 0..255 }) return@mapNotNull null
        val (a, b) = octets[0] to octets[1]
        val siteLocal = when {
            a == 10 -> true
            a == 172 && b in 16..31 -> true
            a == 192 && b == 168 -> true
            else -> false
        }
        if (!siteLocal) return@mapNotNull null
        val rank = when {
            a == 192 && b == 168 -> 0
            a == 10 -> 1
            else -> 2
        }
        Ranked("${octets[0]}.${octets[1]}.${octets[2]}", rank)
    }

    return ranked.sortedBy { it.rank }.map { it.prefix }.distinct()
}

/**
 * What a sweep did, not just whether it found anything.
 *
 * "Could not find the laptop" is the same sentence for three different
 * problems: the phone has no network address, the sweep looked at a network the
 * laptop is not on, or the network dropped the packets. Handing back what was
 * scanned turns the next report into something that can be acted on.
 */
data class DiscoveryResult(
    val found: String?,
    val localAddresses: List<String>,
    val scanned: List<String>,
)
