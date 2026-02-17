package com.example.tvremotetest.wifi.handlers

import android.util.Base64
import android.util.Log
import com.example.tvremotetest.wifi.DeviceDiscovery
import com.example.tvremotetest.wifi.DiscoveredDevice
import com.example.tvremotetest.wifi.WifiProtocolHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Samsung Smart TV WiFi handler.
 * Uses WebSocket-like HTTP connection to send key commands.
 * Samsung TVs (2016+) listen on port 8001 (HTTP) or 8002 (HTTPS).
 */
class SamsungWifiHandler : WifiProtocolHandler {

    companion object {
        private const val TAG = "SamsungWifiHandler"
        private const val APP_NAME = "TVRemote"
    }

    override val protocolName = "Samsung Smart TV"
    override val defaultPort = 8001

    private var connectedDevice: DiscoveredDevice? = null
    private var isDeviceConnected = false

    private val buttonMap = mapOf(
        "Power" to "KEY_POWER",
        "Volume_Up" to "KEY_VOLUP",
        "Volume_Down" to "KEY_VOLDOWN",
        "Channel_Up" to "KEY_CHUP",
        "Channel_Down" to "KEY_CHDOWN",
        "Mute" to "KEY_MUTE",
        "Up" to "KEY_UP",
        "Down" to "KEY_DOWN",
        "Left" to "KEY_LEFT",
        "Right" to "KEY_RIGHT",
        "OK" to "KEY_ENTER",
        "Enter" to "KEY_ENTER",
        "Select" to "KEY_ENTER",
        "Back" to "KEY_RETURN",
        "Return" to "KEY_RETURN",
        "Exit" to "KEY_EXIT",
        "Menu" to "KEY_MENU",
        "Home" to "KEY_HOME",
        "Source" to "KEY_SOURCE",
        "Input" to "KEY_SOURCE",
        "Info" to "KEY_INFO",
        "Guide" to "KEY_GUIDE",
        "Play" to "KEY_PLAY",
        "Pause" to "KEY_PAUSE",
        "Stop" to "KEY_STOP",
        "Rewind" to "KEY_REWIND",
        "Fast_Forward" to "KEY_FF",
        "0" to "KEY_0", "1" to "KEY_1", "2" to "KEY_2",
        "3" to "KEY_3", "4" to "KEY_4", "5" to "KEY_5",
        "6" to "KEY_6", "7" to "KEY_7", "8" to "KEY_8",
        "9" to "KEY_9",
        "Red" to "KEY_RED", "Green" to "KEY_GREEN",
        "Yellow" to "KEY_YELLOW", "Blue" to "KEY_BLUE",
        "HDMI1" to "KEY_HDMI1", "HDMI2" to "KEY_HDMI2",
        "HDMI3" to "KEY_HDMI3", "HDMI4" to "KEY_HDMI4",
        "Netflix" to "KEY_NETFLIX",
        "APPS" to "KEY_APPS",
        "Sleep" to "KEY_SLEEP",
        "Aspect" to "KEY_PANELCHG",
        "CC" to "KEY_CC",
        "PIP" to "KEY_PIP_ONOFF",
        "Zoom" to "KEY_ZOOM_IN",
        "Swap" to "KEY_ZOOM_MOVE"
    )

    override suspend fun discover(timeoutMs: Long): List<DiscoveredDevice> =
        withContext(Dispatchers.IO) {
            val devices = mutableListOf<DiscoveredDevice>()
            val ssdpResults = DeviceDiscovery.discoverSsdp(timeoutMs)

            for (result in ssdpResults) {
                if (result.server.contains("Samsung", true) ||
                    result.location.contains("samsung", true) ||
                    result.st.contains("samsung", true)
                ) {
                    devices.add(
                        DiscoveredDevice(
                            name = extractName(result.server),
                            ipAddress = result.ip,
                            brand = "Samsung",
                            port = defaultPort,
                            serviceType = result.st,
                            uniqueId = result.usn
                        )
                    )
                }
            }

            // Also try fetching device info from known SSDP IPs
            for (result in ssdpResults) {
                if (devices.none { it.ipAddress == result.ip }) {
                    if (DeviceDiscovery.probePort(result.ip, 8001, 500)) {
                        try {
                            val info = fetchDeviceInfo(result.ip)
                            if (info != null) {
                                devices.add(
                                    DiscoveredDevice(
                                        name = info,
                                        ipAddress = result.ip,
                                        brand = "Samsung",
                                        port = defaultPort
                                    )
                                )
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            devices.distinctBy { it.ipAddress }
        }

    private fun fetchDeviceInfo(ip: String): String? {
        return try {
            val url = URL("http://$ip:8001/api/v2/")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                // Parse name from JSON
                val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(body)
                nameMatch?.groupValues?.get(1) ?: "Samsung TV"
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun connect(device: DiscoveredDevice): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Test connectivity by fetching device info
                val url = URL("http://${device.ipAddress}:${device.port}/api/v2/")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"

                val connected = conn.responseCode == 200
                conn.disconnect()

                if (connected) {
                    connectedDevice = device
                    isDeviceConnected = true
                    Log.d(TAG, "Connected to Samsung TV at ${device.ipAddress}")
                }
                connected
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to Samsung TV", e)
                false
            }
        }

    override suspend fun sendKey(buttonName: String): Boolean =
        withContext(Dispatchers.IO) {
            val device = connectedDevice ?: return@withContext false
            val keyCode = mapButtonName(buttonName) ?: return@withContext false

            try {
                val appNameEncoded = Base64.encodeToString(
                    APP_NAME.toByteArray(), Base64.NO_WRAP
                )
                val keyEncoded = Base64.encodeToString(
                    keyCode.toByteArray(), Base64.NO_WRAP
                )

                // Send via HTTP REST fallback (works on older Samsung TVs)
                val bodyJson = """{"method":"ms.remote.control","params":{"Cmd":"Click","DataOfCmd":"$keyCode","Option":"false","TypeOfRemote":"SendRemoteKey"}}"""

                val url = URL("http://${device.ipAddress}:${device.port}/api/v2/channels/samsung.remote.control")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                conn.outputStream.use { it.write(bodyJson.toByteArray()) }

                val responseCode = conn.responseCode
                conn.disconnect()

                if (responseCode in 200..299) {
                    Log.d(TAG, "Sent key $keyCode to Samsung TV")
                    true
                } else {
                    // Fallback: try legacy SOAP-based key send
                    sendKeyLegacy(device.ipAddress, keyCode)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send key $keyCode", e)
                sendKeyLegacy(device.ipAddress, keyCode)
            }
        }

    private fun sendKeyLegacy(ip: String, keyCode: String): Boolean {
        return try {
            val soapBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body>
<u:SendKeyCode xmlns:u="urn:samsung.com:service:MultiScreenService:1">
<KeyCode>$keyCode</KeyCode>
</u:SendKeyCode>
</s:Body>
</s:Envelope>"""

            val url = URL("http://$ip:55000/MultiScreenService/control/SendKeyCode")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            conn.setRequestProperty("SOAPAction", "\"urn:samsung.com:service:MultiScreenService:1#SendKeyCode\"")
            conn.doOutput = true

            conn.outputStream.use { it.write(soapBody.toByteArray()) }
            val ok = conn.responseCode in 200..299
            conn.disconnect()
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Legacy key send failed", e)
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
               ?: "KEY_${dbButtonName.uppercase()}"
    }

    private fun extractName(server: String): String {
        return if (server.isNotBlank()) "Samsung TV" else "Samsung TV"
    }
}
