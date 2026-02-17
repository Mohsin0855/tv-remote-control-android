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
 * Panasonic Viera Smart TV WiFi handler.
 * Uses SOAP/HTTP commands on port 55000.
 */
class PanasonicWifiHandler : WifiProtocolHandler {

    companion object {
        private const val TAG = "PanasonicWifiHandler"
    }

    override val protocolName = "Panasonic Viera"
    override val defaultPort = 55000

    private var connectedDevice: DiscoveredDevice? = null
    private var isDeviceConnected = false

    private val buttonMap = mapOf(
        "Power" to "NRC_POWER-ONOFF",
        "Volume_Up" to "NRC_VOLUP-ONOFF",
        "Volume_Down" to "NRC_VOLDOWN-ONOFF",
        "Channel_Up" to "NRC_CH_UP-ONOFF",
        "Channel_Down" to "NRC_CH_DOWN-ONOFF",
        "Mute" to "NRC_MUTE-ONOFF",
        "Up" to "NRC_UP-ONOFF",
        "Down" to "NRC_DOWN-ONOFF",
        "Left" to "NRC_LEFT-ONOFF",
        "Right" to "NRC_RIGHT-ONOFF",
        "OK" to "NRC_ENTER-ONOFF",
        "Enter" to "NRC_ENTER-ONOFF",
        "Select" to "NRC_ENTER-ONOFF",
        "Back" to "NRC_RETURN-ONOFF",
        "Return" to "NRC_RETURN-ONOFF",
        "Exit" to "NRC_CANCEL-ONOFF",
        "Menu" to "NRC_MENU-ONOFF",
        "Home" to "NRC_HOME-ONOFF",
        "Source" to "NRC_CHG_INPUT-ONOFF",
        "Input" to "NRC_CHG_INPUT-ONOFF",
        "Info" to "NRC_INFO-ONOFF",
        "Guide" to "NRC_EPG-ONOFF",
        "Play" to "NRC_PLAY-ONOFF",
        "Pause" to "NRC_PAUSE-ONOFF",
        "Stop" to "NRC_STOP-ONOFF",
        "Rewind" to "NRC_REW-ONOFF",
        "Fast_Forward" to "NRC_FF-ONOFF",
        "0" to "NRC_D0-ONOFF", "1" to "NRC_D1-ONOFF",
        "2" to "NRC_D2-ONOFF", "3" to "NRC_D3-ONOFF",
        "4" to "NRC_D4-ONOFF", "5" to "NRC_D5-ONOFF",
        "6" to "NRC_D6-ONOFF", "7" to "NRC_D7-ONOFF",
        "8" to "NRC_D8-ONOFF", "9" to "NRC_D9-ONOFF",
        "Red" to "NRC_RED-ONOFF", "Green" to "NRC_GREEN-ONOFF",
        "Yellow" to "NRC_YELLOW-ONOFF", "Blue" to "NRC_BLUE-ONOFF",
        "Netflix" to "NRC_NETFLIX-ONOFF",
        "APPS" to "NRC_APPS-ONOFF",
        "CC" to "NRC_STTL-ONOFF",
        "Aspect" to "NRC_DISP_MODE-ONOFF",
        "Sleep" to "NRC_OFFTIMER-ONOFF",
        "Surround" to "NRC_SURROUND-ONOFF",
        "VieraLink" to "NRC_VIERA_LINK-ONOFF",
        "submenu" to "NRC_SUBMENU-ONOFF"
    )

    override suspend fun discover(timeoutMs: Long): List<DiscoveredDevice> =
        withContext(Dispatchers.IO) {
            val devices = mutableListOf<DiscoveredDevice>()
            val ssdpResults = DeviceDiscovery.discoverSsdp(timeoutMs)

            for (result in ssdpResults) {
                if (result.server.contains("Panasonic", true) ||
                    result.location.contains("panasonic", true) ||
                    result.server.contains("Viera", true)
                ) {
                    devices.add(
                        DiscoveredDevice(
                            name = "Panasonic TV",
                            ipAddress = result.ip,
                            brand = "Panasonic",
                            port = defaultPort,
                            serviceType = result.st,
                            uniqueId = result.usn
                        )
                    )
                }
            }

            // Probe Viera port
            for (result in ssdpResults) {
                if (devices.none { it.ipAddress == result.ip }) {
                    if (DeviceDiscovery.probePort(result.ip, 55000, 500)) {
                        devices.add(
                            DiscoveredDevice(
                                name = "Panasonic TV",
                                ipAddress = result.ip,
                                brand = "Panasonic",
                                port = defaultPort
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
                    Log.d(TAG, "Connected to Panasonic TV at ${device.ipAddress}")
                }
                reachable
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to Panasonic TV", e)
                false
            }
        }

    override suspend fun sendKey(buttonName: String): Boolean =
        withContext(Dispatchers.IO) {
            val device = connectedDevice ?: return@withContext false
            val keyCode = mapButtonName(buttonName) ?: return@withContext false

            try {
                val soapBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body>
<u:X_SendKey xmlns:u="urn:panasonic-com:service:p00NetworkControl:1">
<X_KeyEvent>$keyCode</X_KeyEvent>
</u:X_SendKey>
</s:Body>
</s:Envelope>"""

                val url = URL("http://${device.ipAddress}:${device.port}/nrc/control_0")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
                conn.setRequestProperty("SOAPAction", "\"urn:panasonic-com:service:p00NetworkControl:1#X_SendKey\"")
                conn.doOutput = true

                conn.outputStream.use { it.write(soapBody.toByteArray()) }

                val responseCode = conn.responseCode
                conn.disconnect()

                val success = responseCode in 200..299
                if (success) Log.d(TAG, "Sent key $keyCode to Panasonic TV")
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
