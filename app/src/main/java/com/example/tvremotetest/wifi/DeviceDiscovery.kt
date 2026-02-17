package com.example.tvremotetest.wifi

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/**
 * SSDP-based device discovery using pure Java DatagramSocket.
 * Sends M-SEARCH multicast to find UPnP devices on the local network.
 */
object DeviceDiscovery {

    private const val TAG = "DeviceDiscovery"
    private const val SSDP_ADDRESS = "239.255.255.250"
    private const val SSDP_PORT = 1900

    private val SEARCH_TARGETS = listOf(
        "ssdp:all",
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        "urn:dial-multiscreen-org:service:dial:1"
    )

    data class SsdpResponse(
        val location: String,
        val server: String,
        val st: String,
        val usn: String,
        val ip: String
    )

    /**
     * Discover devices on the local network via SSDP M-SEARCH.
     * @param timeoutMs how long to wait for responses
     * @return list of raw SSDP responses
     */
    suspend fun discoverSsdp(timeoutMs: Long = 3000): List<SsdpResponse> =
        withContext(Dispatchers.IO) {
            val responses = mutableListOf<SsdpResponse>()

            for (target in SEARCH_TARGETS) {
                try {
                    val found = sendMSearch(target, timeoutMs)
                    responses.addAll(found)
                } catch (e: Exception) {
                    Log.e(TAG, "SSDP search failed for $target", e)
                }
            }

            // Deduplicate by IP
            responses.distinctBy { it.ip }
        }

    private fun sendMSearch(searchTarget: String, timeoutMs: Long): List<SsdpResponse> {
        val results = mutableListOf<SsdpResponse>()

        val message = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 3\r\n")
            append("ST: $searchTarget\r\n")
            append("\r\n")
        }

        val socket = DatagramSocket(null)
        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(0))
            socket.soTimeout = timeoutMs.toInt()

            val sendData = message.toByteArray()
            val sendPacket = DatagramPacket(
                sendData, sendData.size,
                InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT
            )
            socket.send(sendPacket)

            val buffer = ByteArray(4096)
            val endTime = System.currentTimeMillis() + timeoutMs

            while (System.currentTimeMillis() < endTime) {
                try {
                    val receivePacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(receivePacket)

                    val response = String(receivePacket.data, 0, receivePacket.length)
                    val ip = receivePacket.address.hostAddress ?: continue

                    val parsed = parseSsdpResponse(response, ip)
                    if (parsed != null) {
                        results.add(parsed)
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "M-SEARCH error", e)
        } finally {
            socket.close()
        }

        return results
    }

    private fun parseSsdpResponse(response: String, ip: String): SsdpResponse? {
        val headers = mutableMapOf<String, String>()

        for (line in response.split("\r\n")) {
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim().lowercase()
                val value = line.substring(colonIdx + 1).trim()
                headers[key] = value
            }
        }

        return SsdpResponse(
            location = headers["location"] ?: "",
            server = headers["server"] ?: "",
            st = headers["st"] ?: "",
            usn = headers["usn"] ?: "",
            ip = ip
        )
    }

    /**
     * Quick scan: try to connect to specific ports to detect brand.
     */
    suspend fun probePort(ip: String, port: Int, timeoutMs: Int = 1000): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val socket = java.net.Socket()
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                socket.close()
                true
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Get the local device's IP address on WiFi.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local IP", e)
        }
        return null
    }
}
