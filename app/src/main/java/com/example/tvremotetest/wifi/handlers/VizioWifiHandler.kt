package com.example.tvremotetest.wifi.handlers

import android.util.Log
import com.example.tvremotetest.wifi.DeviceDiscovery
import com.example.tvremotetest.wifi.DiscoveredDevice
import com.example.tvremotetest.wifi.WifiProtocolHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Vizio SmartCast TV WiFi handler.
 * Uses HTTPS REST API on port 7345 (or HTTP on 9000 for older models).
 */
class VizioWifiHandler : WifiProtocolHandler {

    companion object {
        private const val TAG = "VizioWifiHandler"
    }

    override val protocolName = "Vizio SmartCast"
    override val defaultPort = 7345

    private var connectedDevice: DiscoveredDevice? = null
    private var isDeviceConnected = false
    private var authToken: String? = null

    private val buttonMap = mapOf(
        "Power" to Pair(1, 0), // KEYLIST -> CODESET, CODE
        "Volume_Up" to Pair(5, 1),
        "Volume_Down" to Pair(5, 0),
        "Channel_Up" to Pair(8, 1),
        "Channel_Down" to Pair(8, 0),
        "Mute" to Pair(5, 4),
        "Up" to Pair(3, 8),
        "Down" to Pair(3, 0),
        "Left" to Pair(3, 1),
        "Right" to Pair(3, 7),
        "OK" to Pair(3, 2),
        "Enter" to Pair(3, 2),
        "Select" to Pair(3, 2),
        "Back" to Pair(4, 0),
        "Return" to Pair(4, 0),
        "Exit" to Pair(9, 0),
        "Menu" to Pair(4, 8),
        "Home" to Pair(4, 3),
        "Info" to Pair(4, 6),
        "Play" to Pair(2, 3),
        "Pause" to Pair(2, 2),
        "Input" to Pair(7, 1),
        "Source" to Pair(7, 1),
        "CC" to Pair(4, 4)
    )

    override suspend fun discover(timeoutMs: Long): List<DiscoveredDevice> =
        withContext(Dispatchers.IO) {
            val devices = mutableListOf<DiscoveredDevice>()
            val ssdpResults = DeviceDiscovery.discoverSsdp(timeoutMs)

            for (result in ssdpResults) {
                if (result.server.contains("Vizio", true) ||
                    result.location.contains("vizio", true) ||
                    result.server.contains("SmartCast", true)
                ) {
                    devices.add(
                        DiscoveredDevice(
                            name = "Vizio TV",
                            ipAddress = result.ip,
                            brand = "Vizio",
                            port = defaultPort,
                            serviceType = result.st,
                            uniqueId = result.usn
                        )
                    )
                }
            }

            // Probe SmartCast port
            for (result in ssdpResults) {
                if (devices.none { it.ipAddress == result.ip }) {
                    if (DeviceDiscovery.probePort(result.ip, 7345, 500) ||
                        DeviceDiscovery.probePort(result.ip, 9000, 500)
                    ) {
                        devices.add(
                            DiscoveredDevice(
                                name = "Vizio TV",
                                ipAddress = result.ip,
                                brand = "Vizio",
                                port = if (DeviceDiscovery.probePort(
                                        result.ip, 7345, 300
                                    )
                                ) 7345 else 9000
                            )
                        )
                    }
                }
            }

            devices.distinctBy { it.ipAddress }
        }

    override suspend fun connect(device: DiscoveredDevice): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val reachable = DeviceDiscovery.probePort(device.ipAddress, device.port, 3000)
                if (reachable) {
                    connectedDevice = device
                    isDeviceConnected = true
                    Log.d(TAG, "Connected to Vizio TV at ${device.ipAddress}")
                }
                reachable
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to Vizio TV", e)
                false
            }
        }

    override suspend fun sendKey(buttonName: String): Boolean =
        withContext(Dispatchers.IO) {
            val device = connectedDevice ?: return@withContext false
            val keyPair = buttonMap[buttonName] ?: buttonMap[buttonName.replace("_", " ")]
            ?: return@withContext false

            try {
                val body = """{"KEYLIST":[{"CODESET":${keyPair.first},"CODE":${keyPair.second},"ACTION":"KEYPRESS"}]}"""

                val url = URL("https://${device.ipAddress}:${device.port}/key_command/")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "PUT"
                conn.setRequestProperty("Content-Type", "application/json")
                authToken?.let { conn.setRequestProperty("AUTH", it) }
                conn.doOutput = true

                conn.outputStream.use { it.write(body.toByteArray()) }

                val responseCode = conn.responseCode
                conn.disconnect()

                val success = responseCode in 200..299
                if (success) Log.d(TAG, "Sent key $buttonName to Vizio TV")
                success
            } catch (e: Exception) {
                // Try HTTP fallback on port 9000
                sendKeyHttp(device, buttonName, keyPair)
            }
        }

    private fun sendKeyHttp(
        device: DiscoveredDevice,
        buttonName: String,
        keyPair: Pair<Int, Int>
    ): Boolean {
        return try {
            val body = """{"KEYLIST":[{"CODESET":${keyPair.first},"CODE":${keyPair.second},"ACTION":"KEYPRESS"}]}"""
            val url = URL("http://${device.ipAddress}:9000/key_command/")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            conn.outputStream.use { it.write(body.toByteArray()) }
            val ok = conn.responseCode in 200..299
            conn.disconnect()
            ok
        } catch (e: Exception) {
            Log.e(TAG, "HTTP fallback failed for $buttonName", e)
            false
        }
    }

    override fun isConnected(): Boolean = isDeviceConnected && connectedDevice != null

    override fun disconnect() {
        connectedDevice = null
        isDeviceConnected = false
        authToken = null
    }

    override fun mapButtonName(dbButtonName: String): String? {
        return if (buttonMap.containsKey(dbButtonName) ||
            buttonMap.containsKey(dbButtonName.replace("_", " "))
        ) dbButtonName else null
    }
}
