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
 * Philips Smart TV WiFi handler.
 * Uses JointSpace REST API on port 1925 (HTTP) or 1926 (HTTPS).
 */
class PhilipsWifiHandler : WifiProtocolHandler {

    companion object {
        private const val TAG = "PhilipsWifiHandler"
    }

    override val protocolName = "Philips JointSpace"
    override val defaultPort = 1925

    private var connectedDevice: DiscoveredDevice? = null
    private var isDeviceConnected = false

    private val buttonMap = mapOf(
        "Power" to "Standby",
        "Volume_Up" to "VolumeUp",
        "Volume_Down" to "VolumeDown",
        "Channel_Up" to "ChannelStepUp",
        "Channel_Down" to "ChannelStepDown",
        "Mute" to "Mute",
        "Up" to "CursorUp",
        "Down" to "CursorDown",
        "Left" to "CursorLeft",
        "Right" to "CursorRight",
        "OK" to "Confirm",
        "Enter" to "Confirm",
        "Select" to "Confirm",
        "Back" to "Back",
        "Return" to "Back",
        "Exit" to "Exit",
        "Menu" to "Home",
        "Home" to "Home",
        "Source" to "Source",
        "Input" to "Source",
        "Info" to "Info",
        "Guide" to "Guide",
        "Play" to "Play",
        "Pause" to "Pause",
        "Stop" to "Stop",
        "Rewind" to "Rewind",
        "Fast_Forward" to "FastForward",
        "0" to "Digit0", "1" to "Digit1", "2" to "Digit2",
        "3" to "Digit3", "4" to "Digit4", "5" to "Digit5",
        "6" to "Digit6", "7" to "Digit7", "8" to "Digit8",
        "9" to "Digit9",
        "Red" to "RedColour", "Green" to "GreenColour",
        "Yellow" to "YellowColour", "Blue" to "BlueColour",
        "Netflix" to "Netflix",
        "CC" to "SubtitlesOnOff",
        "Aspect" to "AdjustPicture",
        "Sleep" to "Standby"
    )

    override suspend fun discover(timeoutMs: Long): List<DiscoveredDevice> =
        withContext(Dispatchers.IO) {
            val devices = mutableListOf<DiscoveredDevice>()
            val ssdpResults = DeviceDiscovery.discoverSsdp(timeoutMs)

            for (result in ssdpResults) {
                if (result.server.contains("Philips", true) ||
                    result.location.contains("philips", true)
                ) {
                    devices.add(
                        DiscoveredDevice(
                            name = "Philips TV",
                            ipAddress = result.ip,
                            brand = "Philips",
                            port = defaultPort,
                            serviceType = result.st,
                            uniqueId = result.usn
                        )
                    )
                }
            }

            // Probe JointSpace port
            for (result in ssdpResults) {
                if (devices.none { it.ipAddress == result.ip }) {
                    if (DeviceDiscovery.probePort(result.ip, 1925, 500)) {
                        devices.add(
                            DiscoveredDevice(
                                name = "Philips TV",
                                ipAddress = result.ip,
                                brand = "Philips",
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
                val url = URL("http://${device.ipAddress}:${device.port}/6/system")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"

                val connected = conn.responseCode == 200
                conn.disconnect()

                if (connected) {
                    connectedDevice = device
                    isDeviceConnected = true
                    Log.d(TAG, "Connected to Philips TV at ${device.ipAddress}")
                }
                connected
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to Philips TV", e)
                false
            }
        }

    override suspend fun sendKey(buttonName: String): Boolean =
        withContext(Dispatchers.IO) {
            val device = connectedDevice ?: return@withContext false
            val keyCode = mapButtonName(buttonName) ?: return@withContext false

            try {
                val body = """{"key":"$keyCode"}"""

                val url = URL("http://${device.ipAddress}:${device.port}/6/input/key")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                conn.outputStream.use { it.write(body.toByteArray()) }

                val responseCode = conn.responseCode
                conn.disconnect()

                val success = responseCode in 200..299
                if (success) Log.d(TAG, "Sent key $keyCode to Philips TV")
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
