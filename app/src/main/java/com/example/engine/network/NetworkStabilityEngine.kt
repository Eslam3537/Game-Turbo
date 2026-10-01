package com.example.engine.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Random
import kotlin.math.abs

data class PingSample(
    val timestamp: Long = System.currentTimeMillis(),
    val rttMs: Int?, // null represents timeout / packet loss
    val endpoint: String
)

data class NetworkQualityProfile(
    val currentRttMs: Int? = null,
    val minRttMs: Int? = null,
    val maxRttMs: Int? = null,
    val avgRttMs: Int? = null,
    val jitterMs: Int? = null,
    val packetLossPercent: Double? = null,
    val samplesCount: Int = 0,
    val lostSamplesCount: Int = 0,
    val networkChangesCount: Int = 0,
    val activeTransport: String = "Detecting...",
    val wifiLinkSpeedMbps: Int? = null,
    val wifiRssi: Int? = null,
    val integrityVerdict: String = "PENDING", // OPTIMAL, MODERATE, ELEVATED, UNSTABLE, NO_CONNECTION
    val fastestDnsHost: String? = null,
    val fastestDnsLatencyMs: Long? = null
)

data class DnsBenchmarkResult(
    val providerName: String,
    val ip: String,
    val dotHostname: String,
    val medianMs: Long?,
    val minMs: Long?,
    val lostCount: Int,
    val samplesCount: Int = 4
) {
    val host: String get() = dotHostname
    val latencyMs: Long? get() = medianMs
}

class NetworkStabilityEngine(private val context: Context) {
    private val _networkProfile = MutableStateFlow(NetworkQualityProfile())
    val networkProfile: StateFlow<NetworkQualityProfile> = _networkProfile.asStateFlow()

    private val samples = ArrayDeque<PingSample>(30)
    private var networkChangeCounter = 0
    private var isMonitoring = false

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            networkChangeCounter++
            updateTransportState()
        }
        override fun onLost(network: Network) {
            networkChangeCounter++
            updateTransportState()
        }
    }

    fun start() {
        if (isMonitoring) return
        isMonitoring = true
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
            updateTransportState()
        } catch (_: Throwable) {}
    }

    fun stop() {
        isMonitoring = false
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Throwable) {}
    }

    /**
     * Measures genuine TCP connect RTT against a target host on port 443 (Fix A18: resolve first, time only connect).
     * Records a packet loss when connection fails. NEVER returns a fake number.
     */
    suspend fun sampleRtt(host: String = "1.1.1.1", port: Int = 443, timeoutMs: Int = 1500): Int? = withContext(Dispatchers.IO) {
        // Resolve address first so DNS time is not counted in TCP connect RTT
        val address = try {
            InetAddress.getByName(host)
        } catch (_: Throwable) {
            null
        } ?: run {
            withContext(Dispatchers.Main) { recordSample(null, host) }
            return@withContext null
        }

        val socketAddress = InetSocketAddress(address, port)
        var socket: Socket? = null
        val start = System.currentTimeMillis()
        val measuredRtt: Int? = try {
            socket = Socket()
            socket.connect(socketAddress, timeoutMs)
            (System.currentTimeMillis() - start).toInt()
        } catch (_: Throwable) {
            null // Real connection failure / timeout
        } finally {
            try { socket?.close() } catch (_: Throwable) {}
        }

        withContext(Dispatchers.Main) {
            recordSample(measuredRtt, host)
        }
        measuredRtt
    }

    private fun updateTransportState() {
        var transport = "No Connection"
        var linkSpeed: Int? = null
        var rssi: Int? = null

        try {
            val activeNet = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(activeNet)
            if (caps != null) {
                transport = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    else -> "Connected"
                }

                if (transport == "Wi-Fi") {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    val wifiInfo = wifiManager?.connectionInfo
                    if (wifiInfo != null && wifiInfo.networkId != -1) {
                        linkSpeed = if (wifiInfo.linkSpeed > 0) wifiInfo.linkSpeed else null
                        rssi = wifiInfo.rssi
                    }
                }
            }
        } catch (_: Throwable) {}

        _networkProfile.value = _networkProfile.value.copy(
            activeTransport = transport,
            wifiLinkSpeedMbps = linkSpeed,
            wifiRssi = rssi
        )
    }

    private fun recordSample(rtt: Int?, endpoint: String) {
        if (samples.size >= 30) {
            samples.removeFirst()
        }
        samples.addLast(PingSample(rttMs = rtt, endpoint = endpoint))

        val totalTaken = samples.size
        val validSamples = samples.mapNotNull { it.rttMs }
        val lostCount = totalTaken - validSamples.size

        val packetLossPercent = if (totalTaken > 0) {
            (lostCount.toDouble() / totalTaken.toDouble()) * 100.0
        } else null

        val avg = if (validSamples.isNotEmpty()) validSamples.average().toInt() else null
        val min = validSamples.minOrNull()
        val max = validSamples.maxOrNull()

        // Authentic Jitter calculation: mean absolute difference of consecutive real samples
        val jitter = if (validSamples.size >= 2) {
            val diffs = validSamples.zipWithNext { a, b -> abs(b - a) }
            diffs.average().toInt()
        } else null

        // Genuine network integrity verdict
        val verdict = when {
            validSamples.isEmpty() -> "NO_CONNECTION"
            (packetLossPercent ?: 0.0) > 5.0 -> "UNSTABLE"
            avg != null && avg <= 50 -> "OPTIMAL"
            avg != null && avg <= 90 -> "MODERATE"
            else -> "ELEVATED"
        }

        updateTransportState()

        _networkProfile.value = _networkProfile.value.copy(
            currentRttMs = rtt,
            minRttMs = min,
            maxRttMs = max,
            avgRttMs = avg,
            jitterMs = jitter,
            packetLossPercent = packetLossPercent,
            samplesCount = totalTaken,
            lostSamplesCount = lostCount,
            networkChangesCount = networkChangeCounter,
            integrityVerdict = verdict
        )
    }

    /**
     * Genuinely benchmarks DNS providers via direct UDP port 53 query packets (Fix A5).
     * Discards 1st warm-up sample, measures 4 samples, returns median and lost count.
     */
    suspend fun benchmarkDnsCandidates(): List<DnsBenchmarkResult> = withContext(Dispatchers.IO) {
        val candidates = listOf(
            Triple("Cloudflare", "1.1.1.1", "one.one.one.one"),
            Triple("Google", "8.8.8.8", "dns.google"),
            Triple("Quad9", "9.9.9.9", "dns.quad9.net"),
            Triple("AdGuard", "94.140.14.14", "dns.adguard.com")
        )

        val results = mutableListOf<DnsBenchmarkResult>()

        for ((name, ip, dotHost) in candidates) {
            val measuredLatencies = mutableListOf<Long>()
            var lostCount = 0

            // 5 samples total: 1 warm-up + 4 measured
            for (i in 0 until 5) {
                val latency = sendUdpDnsQuery(ip, "cloudflare.com", timeoutMs = 1000)
                if (i > 0) { // discard warm-up sample 0
                    if (latency != null) {
                        measuredLatencies.add(latency)
                    } else {
                        lostCount++
                    }
                }
            }

            val median = if (measuredLatencies.isNotEmpty()) {
                val sorted = measuredLatencies.sorted()
                sorted[sorted.size / 2]
            } else null

            val min = measuredLatencies.minOrNull()

            results.add(
                DnsBenchmarkResult(
                    providerName = name,
                    ip = ip,
                    dotHostname = dotHost,
                    medianMs = median,
                    minMs = min,
                    lostCount = lostCount,
                    samplesCount = 4
                )
            )
        }

        // Find fastest winner among valid responses
        val validResults = results.filter { it.medianMs != null }
        val fastest = validResults.minByOrNull { it.medianMs!! }
        if (fastest != null) {
            _networkProfile.value = _networkProfile.value.copy(
                fastestDnsHost = fastest.dotHostname,
                fastestDnsLatencyMs = fastest.medianMs
            )
        }

        results
    }

    /**
     * Builds and sends a real UDP DNS A-record query directly to target IP on port 53 (Fix A5).
     */
    private fun sendUdpDnsQuery(serverIp: String, domain: String, timeoutMs: Int): Long? {
        var socket: DatagramSocket? = null
        return try {
            val random = Random()
            val queryId = random.nextInt(65535)

            val queryBytes = buildDnsQueryPacket(queryId, domain)
            val serverAddr = InetAddress.getByName(serverIp)

            socket = DatagramSocket().apply {
                soTimeout = timeoutMs
            }

            val sendPacket = DatagramPacket(queryBytes, queryBytes.size, serverAddr, 53)
            val receiveBuffer = ByteArray(512)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

            val start = System.currentTimeMillis()
            socket.send(sendPacket)
            socket.receive(receivePacket)
            val elapsed = System.currentTimeMillis() - start

            val responseData = receivePacket.data
            if (receivePacket.length < 12) return null

            // Validate response ID matches query ID
            val respId = ((responseData[0].toInt() and 0xFF) shl 8) or (responseData[1].toInt() and 0xFF)
            if (respId != queryId) return null

            // Validate RCODE is 0 (No error)
            val flags = ((responseData[2].toInt() and 0xFF) shl 8) or (responseData[3].toInt() and 0xFF)
            val rcode = flags and 0x0F
            if (rcode != 0) return null

            elapsed
        } catch (_: Throwable) {
            null
        } finally {
            try { socket?.close() } catch (_: Throwable) {}
        }
    }

    private fun buildDnsQueryPacket(queryId: Int, domain: String): ByteArray {
        val out = mutableListOf<Byte>()

        // 1. Transaction ID (2 bytes)
        out.add(((queryId shr 8) and 0xFF).toByte())
        out.add((queryId and 0xFF).toByte())

        // 2. Flags: Standard query, recursion desired (0x0100) (2 bytes)
        out.add(0x01.toByte())
        out.add(0x00.toByte())

        // 3. QDCOUNT: 1 question (2 bytes)
        out.add(0x00.toByte())
        out.add(0x01.toByte())

        // 4. ANCOUNT: 0 (2 bytes)
        out.add(0x00.toByte())
        out.add(0x00.toByte())

        // 5. NSCOUNT: 0 (2 bytes)
        out.add(0x00.toByte())
        out.add(0x00.toByte())

        // 6. ARCOUNT: 0 (2 bytes)
        out.add(0x00.toByte())
        out.add(0x00.toByte())

        // 7. QNAME (Labels)
        val labels = domain.split('.')
        for (label in labels) {
            out.add(label.length.toByte())
            for (char in label) {
                out.add(char.code.toByte())
            }
        }
        out.add(0x00.toByte()) // Null label terminator

        // 8. QTYPE: A (0x0001) (2 bytes)
        out.add(0x00.toByte())
        out.add(0x01.toByte())

        // 9. QCLASS: IN (0x0001) (2 bytes)
        out.add(0x00.toByte())
        out.add(0x01.toByte())

        return out.toByteArray()
    }

    fun clearSamples() {
        samples.clear()
        networkChangeCounter = 0
        _networkProfile.value = NetworkQualityProfile()
    }
}
