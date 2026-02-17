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
 * Sony Bravia Smart TV WiFi handler.
 * Uses IRCC-IP (Infrared Remote Control over IP) REST API.
 */
class SonyWifiHandler : WifiProtocolHandler {

    companion object {
        private const val TAG = "SonyWifiHandler"
        private const val DEFAULT_PSK = "0000"
    }

    override val protocolName = "Sony Bravia IRCC"
    override val defaultPort = 80

    private var connectedDevice: DiscoveredDevice? = null
    private var isDeviceConnected = false
    private var authPsk: String = DEFAULT_PSK

    private val buttonMap = mapOf(
        "Power" to "AAAAAQAAAAEAAAAVAw==",
        "Volume_Up" to "AAAAAQAAAAEAAAASAw==",
        "Volume_Down" to "AAAAAQAAAAEAAAATAw==",
        "Channel_Up" to "AAAAAQAAAAEAAAAQAw==",
        "Channel_Down" to "AAAAAQAAAAEAAAARAw==",
        "Mute" to "AAAAAQAAAAEAAAAUAw==",
        "Up" to "AAAAAQAAAAEAAAB0Aw==",
        "Down" to "AAAAAQAAAAEAAAB1Aw==",
        "Left" to "AAAAAQAAAAEAAAB2Aw==",
        "Right" to "AAAAAQAAAAEAAAB3Aw==",
        "OK" to "AAAAAQAAAAEAAABlAw==",
        "Enter" to "AAAAAQAAAAEAAABlAw==",
        "Select" to "AAAAAQAAAAEAAABlAw==",
        "Back" to "AAAAAgAAAJcAAAAjAw==",
        "Return" to "AAAAAgAAAJcAAAAjAw==",
        "Exit" to "AAAAAQAAAAEAAABjAw==",
        "Menu" to "AAAAAgAAAJcAAAA3Aw==",
        "Home" to "AAAAAQAAAAEAAABgAw==",
        "Source" to "AAAAAQAAAAEAAAAlAw==",
        "Input" to "AAAAAQAAAAEAAAAlAw==",
        "Info" to "AAAAAQAAAAEAAAA6Aw==",
        "Guide" to "AAAAAgAAAKQAAABbAw==",
        "Play" to "AAAAAgAAAJcAAAAaAw==",
        "Pause" to "AAAAAgAAAJcAAAAZAw==",
        "Stop" to "AAAAAgAAAJcAAAAYAw==",
        "Rewind" to "AAAAAgAAAJcAAAAbAw==",
        "Fast_Forward" to "AAAAAgAAAJcAAAAcAw==",
        "0" to "AAAAAQAAAAEAAAAJAw==",
        "1" to "AAAAAQAAAAEAAAAAAw==",
        "2" to "AAAAAQAAAAEAAAABAw==",
        "3" to "AAAAAQAAAAEAAAACAw==",
        "4" to "AAAAAQAAAAEAAAADAw==",
        "5" to "AAAAAQAAAAEAAAAEAw==",
        "6" to "AAAAAQAAAAEAAAAFAw==",
        "7" to "AAAAAQAAAAEAAAAGAw==",
        "8" to "AAAAAQAAAAEAAAAHAw==",
        "9" to "AAAAAQAAAAEAAAAIAw==",
        "Red" to "AAAAAgAAAJcAAAAlAw==",
        "Green" to "AAAAAgAAAJcAAAAmAw==",
        "Yellow" to "AAAAAgAAAJcAAAAnAw==",
        "Blue" to "AAAAAgAAAJcAAAAkAw==",
        "Netflix" to "AAAAAgAAABoAAAB8Aw==",
        "CC" to "AAAAAgAAAJcAAAAoAw==",
        "Sleep" to "AAAAAgAAAJcAAAA2Aw==",
        "Aspect" to "AAAAAQAAAAEAAABQAW=="
    )

    override suspend fun discover(timeoutMs: Long): List<DiscoveredDevice> =
        withContext(Dispatchers.IO) {
            val devices = mutableListOf<DiscoveredDevice>()
            val ssdpResults = DeviceDiscovery.discoverSsdp(timeoutMs)

            for (result in ssdpResults) {
                if (result.server.contains("Sony", true) ||
                    result.location.contains("sony", true) ||
                    result.server.contains("BRAVIA", true)
                ) {
                    devices.add(
                        DiscoveredDevice(
                            name = "Sony TV",
                            ipAddress = result.ip,
                            brand = "Sony",
                            port = defaultPort,
                            serviceType = result.st,
                            uniqueId = result.usn
                        )
                    )
                }
            }

            devices.distinctBy { it.ipAddress }
        }

    override suspend fun connect(device: DiscoveredDevice): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Try to fetch system info to verify connection
                val url = URL("http://${device.ipAddress}/sony/system")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-Auth-PSK", authPsk)
                conn.doOutput = true

                val body = """{"method":"getSystemInformation","id":33,"params":[],"version":"1.0"}"""
                conn.outputStream.use { it.write(body.toByteArray()) }

                val connected = conn.responseCode == 200
                conn.disconnect()

                if (connected) {
                    connectedDevice = device
                    isDeviceConnected = true
                    Log.d(TAG, "Connected to Sony TV at ${device.ipAddress}")
                }
                connected
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to Sony TV", e)
                false
            }
        }

    override suspend fun sendKey(buttonName: String): Boolean =
        withContext(Dispatchers.IO) {
            val device = connectedDevice ?: return@withContext false
            val irccCode = mapButtonName(buttonName) ?: return@withContext false

            try {
                val soapBody = """<?xml version="1.0"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body>
<u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1">
<IRCCCode>$irccCode</IRCCCode>
</u:X_SendIRCC>
</s:Body>
</s:Envelope>"""

                val url = URL("http://${device.ipAddress}/sony/IRCC")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
                conn.setRequestProperty("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
                conn.setRequestProperty("X-Auth-PSK", authPsk)
                conn.doOutput = true

                conn.outputStream.use { it.write(soapBody.toByteArray()) }

                val responseCode = conn.responseCode
                conn.disconnect()

                val success = responseCode in 200..299
                if (success) Log.d(TAG, "Sent IRCC code for $buttonName to Sony TV")
                success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send IRCC code for $buttonName", e)
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
