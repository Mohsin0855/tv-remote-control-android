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
 * LG webOS Smart TV WiFi handler.
 * Uses SSAP (Simple Service Access Protocol) HTTP commands.
 * LG TVs listen on port 3000 (WebSocket) and 3001 (HTTPS WebSocket).
 * This implementation uses HTTP-based control as a fallback.
 */
class LGWifiHandler : WifiProtocolHandler {

    companion object {
        private const val TAG = "LGWifiHandler"
    }

    override val protocolName = "LG webOS"
    override val defaultPort = 3000

    private var connectedDevice: DiscoveredDevice? = null
    private var isDeviceConnected = false
    private var clientKey: String? = null

    private val buttonMap = mapOf(
        "Power" to "KEY_POWER",
        "Volume_Up" to "volumeUp",
        "Volume_Down" to "volumeDown",
        "Channel_Up" to "channelUp",
        "Channel_Down" to "channelDown",
        "Mute" to "KEY_MUTE",
        "Up" to "UP",
        "Down" to "DOWN",
        "Left" to "LEFT",
        "Right" to "RIGHT",
        "OK" to "ENTER",
        "Enter" to "ENTER",
        "Select" to "ENTER",
        "Back" to "BACK",
        "Return" to "BACK",
        "Exit" to "EXIT",
        "Menu" to "MENU",
        "Home" to "HOME",
        "Source" to "INPUT",
        "Input" to "INPUT",
        "Info" to "INFO",
        "Guide" to "GUIDE",
        "Play" to "PLAY",
        "Pause" to "PAUSE",
        "Stop" to "STOP",
        "Rewind" to "REWIND",
        "Fast_Forward" to "FASTFORWARD",
        "0" to "0", "1" to "1", "2" to "2",
        "3" to "3", "4" to "4", "5" to "5",
        "6" to "6", "7" to "7", "8" to "8",
        "9" to "9",
        "Red" to "RED", "Green" to "GREEN",
        "Yellow" to "YELLOW", "Blue" to "BLUE",
        "Netflix" to "NETFLIX",
        "APPS" to "MYAPPS",
        "CC" to "CC",
        "Aspect" to "ASPECTRATIO",
        "Sleep" to "SLEEP"
    )

    override suspend fun discover(timeoutMs: Long): List<DiscoveredDevice> =
        withContext(Dispatchers.IO) {
            val devices = mutableListOf<DiscoveredDevice>()
            val ssdpResults = DeviceDiscovery.discoverSsdp(timeoutMs)

            for (result in ssdpResults) {
                if (result.server.contains("LG", true) ||
                    result.location.contains("LG", true) ||
                    result.server.contains("webOS", true) ||
                    result.st.contains("lge", true)
                ) {
                    devices.add(
                        DiscoveredDevice(
                            name = "LG TV",
                            ipAddress = result.ip,
                            brand = "LG",
                            port = defaultPort,
                            serviceType = result.st,
                            uniqueId = result.usn
                        )
                    )
                }
            }

            // Probe port 3000 on discovered IPs
            for (result in ssdpResults) {
                if (devices.none { it.ipAddress == result.ip }) {
                    if (DeviceDiscovery.probePort(result.ip, 3000, 500)) {
                        devices.add(
                            DiscoveredDevice(
                                name = "LG TV",
                                ipAddress = result.ip,
                                brand = "LG",
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
                // Try to reach the device - LG TVs respond on port 3000
                val reachable = DeviceDiscovery.probePort(device.ipAddress, device.port, 3000)
                if (reachable) {
                    connectedDevice = device
                    isDeviceConnected = true
                    Log.d(TAG, "Connected to LG TV at ${device.ipAddress}")
                }
                reachable
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to LG TV", e)
                false
            }
        }

    override suspend fun sendKey(buttonName: String): Boolean =
        withContext(Dispatchers.IO) {
            val device = connectedDevice ?: return@withContext false
            val keyCode = mapButtonName(buttonName) ?: return@withContext false

            try {
                // LG command endpoint
                val commandUrl = "http://${device.ipAddress}:${device.port}/roap/api/command"

                val xmlBody = """<?xml version="1.0" encoding="utf-8"?>
<command>
<name>HandleKeyInput</name>
<value>$keyCode</value>
</command>"""

                val url = URL(commandUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/atom+xml")
                conn.doOutput = true

                conn.outputStream.use { it.write(xmlBody.toByteArray()) }

                val responseCode = conn.responseCode
                conn.disconnect()

                val success = responseCode in 200..299
                if (success) Log.d(TAG, "Sent key $keyCode to LG TV")
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
        clientKey = null
    }

    override fun mapButtonName(dbButtonName: String): String? {
        return buttonMap[dbButtonName]
            ?: buttonMap[dbButtonName.replace("_", " ")]
            ?: dbButtonName.uppercase()
    }
}
