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
 * Roku WiFi handler using External Control Protocol (ECP).
 * Also works with Roku-based TCL and Hisense TVs.
 * Roku devices listen on port 8060 for HTTP commands.
 */
class RokuWifiHandler : WifiProtocolHandler {

    companion object {
        private const val TAG = "RokuWifiHandler"
    }

    override val protocolName = "Roku ECP"
    override val defaultPort = 8060

    private var connectedDevice: DiscoveredDevice? = null
    private var isDeviceConnected = false

    private val buttonMap = mapOf(
        "Power" to "Power",
        "PowerOff" to "PowerOff",
        "PowerOn" to "PowerOn",
        "Volume_Up" to "VolumeUp",
        "Volume_Down" to "VolumeDown",
        "Mute" to "VolumeMute",
        "Up" to "Up",
        "Down" to "Down",
        "Left" to "Left",
        "Right" to "Right",
        "OK" to "Select",
        "Enter" to "Select",
        "Select" to "Select",
        "Back" to "Back",
        "Return" to "Back",
        "Home" to "Home",
        "Info" to "Info",
        "Play" to "Play",
        "Pause" to "Play",
        "Rewind" to "Rev",
        "Fast_Forward" to "Fwd",
        "Stop" to "Play",
        "0" to "Lit_0", "1" to "Lit_1", "2" to "Lit_2",
        "3" to "Lit_3", "4" to "Lit_4", "5" to "Lit_5",
        "6" to "Lit_6", "7" to "Lit_7", "8" to "Lit_8",
        "9" to "Lit_9",
        "Netflix" to "Launch_12",  // Netflix app ID
        "Input" to "InputHDMI1",
        "HDMI1" to "InputHDMI1",
        "HDMI2" to "InputHDMI2",
        "HDMI3" to "InputHDMI3",
        "Sleep" to "Sleep"
    )

    override suspend fun discover(timeoutMs: Long): List<DiscoveredDevice> =
        withContext(Dispatchers.IO) {
            val devices = mutableListOf<DiscoveredDevice>()
            val ssdpResults = DeviceDiscovery.discoverSsdp(timeoutMs)

            for (result in ssdpResults) {
                if (result.server.contains("Roku", true) ||
                    result.st.contains("roku", true) ||
                    result.location.contains(":8060", true)
                ) {
                    val name = fetchDeviceName(result.ip) ?: "Roku Device"
                    devices.add(
                        DiscoveredDevice(
                            name = name,
                            ipAddress = result.ip,
                            brand = "Roku",
                            port = defaultPort,
                            serviceType = result.st,
                            uniqueId = result.usn
                        )
                    )
                }
            }

            // Probe port 8060 on all discovered IPs
            for (result in ssdpResults) {
                if (devices.none { it.ipAddress == result.ip }) {
                    if (DeviceDiscovery.probePort(result.ip, 8060, 500)) {
                        val name = fetchDeviceName(result.ip) ?: "Roku Device"
                        devices.add(
                            DiscoveredDevice(
                                name = name,
                                ipAddress = result.ip,
                                brand = "Roku",
                                port = defaultPort
                            )
                        )
                    }
                }
            }

            devices.distinctBy { it.ipAddress }
        }

    private fun fetchDeviceName(ip: String): String? {
        return try {
            val url = URL("http://$ip:8060/query/device-info")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val nameMatch = Regex("<friendly-device-name>([^<]+)</friendly-device-name>").find(body)
                    ?: Regex("<model-name>([^<]+)</model-name>").find(body)
                nameMatch?.groupValues?.get(1)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun connect(device: DiscoveredDevice): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("http://${device.ipAddress}:${device.port}/query/device-info")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"

                val connected = conn.responseCode == 200
                conn.disconnect()

                if (connected) {
                    connectedDevice = device
                    isDeviceConnected = true
                    Log.d(TAG, "Connected to Roku at ${device.ipAddress}")
                }
                connected
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to Roku", e)
                false
            }
        }

    override suspend fun sendKey(buttonName: String): Boolean =
        withContext(Dispatchers.IO) {
            val device = connectedDevice ?: return@withContext false
            val keyCode = mapButtonName(buttonName) ?: return@withContext false

            try {
                val url = URL("http://${device.ipAddress}:${device.port}/keypress/$keyCode")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Length", "0")
                conn.doOutput = true

                conn.outputStream.use { /* empty POST */ }

                val responseCode = conn.responseCode
                conn.disconnect()

                val success = responseCode in 200..299
                if (success) Log.d(TAG, "Sent key $keyCode to Roku")
                success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send key $keyCode", e)
                false
            }
        }

    override fun isConnected(): Boolean = isDeviceConnected && connectedDevice != null

    override fun disconnect() {
        connectedDevice = null
        isDeviceConnected = false
    }

    override fun mapButtonName(dbButtonName: String): String? {
        return buttonMap[dbButtonName] ?: buttonMap[dbButtonName.replace("_", " ")]
    }
}
