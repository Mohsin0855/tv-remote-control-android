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
 * Generic WiFi remote handler for brands without specific protocols.
 * Tries multiple approaches:
 * 1. SSDP discovery to find any UPnP device
 * 2. Port probing for known Smart TV ports
 * 3. UPnP rendering control for basic commands
 *
 * This handler provides best-effort control. Not all devices will respond.
 */
class GenericWifiHandler : WifiProtocolHandler {

    companion object {
        private const val TAG = "GenericWifiHandler"
        private val KNOWN_PORTS = listOf(8001, 8060, 3000, 1925, 55000, 9000, 7345, 80, 8080)
    }

    override val protocolName = "Generic UPnP"
    override val defaultPort = 80

    private var connectedDevice: DiscoveredDevice? = null
    private var isDeviceConnected = false
    private var detectedProtocol: String? = null
    private var delegateHandler: WifiProtocolHandler? = null

    private val buttonMap = mapOf(
        "Power" to "Power",
        "Volume_Up" to "VolumeUp",
        "Volume_Down" to "VolumeDown",
        "Channel_Up" to "ChannelUp",
        "Channel_Down" to "ChannelDown",
        "Mute" to "Mute",
        "Up" to "Up", "Down" to "Down",
        "Left" to "Left", "Right" to "Right",
        "OK" to "Enter", "Enter" to "Enter",
        "Back" to "Back", "Return" to "Return",
        "Home" to "Home", "Menu" to "Menu",
        "Source" to "Source", "Input" to "Input",
        "Play" to "Play", "Pause" to "Pause",
        "Stop" to "Stop", "Rewind" to "Rewind",
        "Fast_Forward" to "FastForward",
        "Info" to "Info", "Guide" to "Guide"
    )

    override suspend fun discover(timeoutMs: Long): List<DiscoveredDevice> =
        withContext(Dispatchers.IO) {
            val devices = mutableListOf<DiscoveredDevice>()
            val ssdpResults = DeviceDiscovery.discoverSsdp(timeoutMs)

            for (result in ssdpResults) {
                // Detect what protocol this device might speak
                val brand = detectBrand(result.server, result.location)
                devices.add(
                    DiscoveredDevice(
                        name = brand ?: "Smart TV (${result.ip})",
                        ipAddress = result.ip,
                        brand = brand ?: "Unknown",
                        port = detectPort(result),
                        serviceType = result.st,
                        uniqueId = result.usn
                    )
                )
            }

            devices.distinctBy { it.ipAddress }
        }

    private fun detectBrand(server: String, location: String): String? {
        val combined = "$server $location".lowercase()
        return when {
            "samsung" in combined -> "Samsung"
            "lg" in combined || "webos" in combined -> "LG"
            "sony" in combined || "bravia" in combined -> "Sony"
            "roku" in combined -> "Roku"
            "philips" in combined -> "Philips"
            "panasonic" in combined || "viera" in combined -> "Panasonic"
            "vizio" in combined || "smartcast" in combined -> "Vizio"
            "toshiba" in combined -> "Toshiba"
            "sharp" in combined -> "Sharp"
            "hisense" in combined -> "Hisense"
            "tcl" in combined -> "TCL"
            "xiaomi" in combined -> "Xiaomi"
            else -> null
        }
    }

    private fun detectPort(result: DeviceDiscovery.SsdpResponse): Int {
        // Try to extract port from LOCATION header
        try {
            val url = URL(result.location)
            if (url.port > 0) return url.port
        } catch (_: Exception) {}
        return defaultPort
    }

    override suspend fun connect(device: DiscoveredDevice): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Try to detect what protocol this device speaks by probing ports
                for (port in KNOWN_PORTS) {
                    if (DeviceDiscovery.probePort(device.ipAddress, port, 500)) {
                        val handler = getHandlerForPort(port)
                        if (handler != null) {
                            val testDevice = device.copy(port = port)
                            if (handler.connect(testDevice)) {
                                delegateHandler = handler
                                connectedDevice = testDevice
                                isDeviceConnected = true
                                detectedProtocol = handler.protocolName
                                Log.d(TAG, "Connected via ${handler.protocolName} on port $port")
                                return@withContext true
                            }
                        }
                    }
                }

                // Fallback: just mark as connected if device is reachable
                val reachable = KNOWN_PORTS.any {
                    DeviceDiscovery.probePort(device.ipAddress, it, 300)
                }
                if (reachable) {
                    connectedDevice = device
                    isDeviceConnected = true
                    Log.d(TAG, "Connected to device at ${device.ipAddress} (generic)")
                }
                reachable
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect", e)
                false
            }
        }

    private fun getHandlerForPort(port: Int): WifiProtocolHandler? {
        return when (port) {
            8001, 8002 -> SamsungWifiHandler()
            3000, 3001 -> LGWifiHandler()
            8060 -> RokuWifiHandler()
            1925, 1926 -> PhilipsWifiHandler()
            55000 -> PanasonicWifiHandler()
            7345, 9000 -> VizioWifiHandler()
            else -> null
        }
    }

    override suspend fun sendKey(buttonName: String): Boolean {
        // If we detected a specific protocol, delegate to that handler
        delegateHandler?.let { return it.sendKey(buttonName) }

        // Otherwise try generic UPnP rendering control
        val device = connectedDevice ?: return false
        return sendUPnPCommand(device, buttonName)
    }

    private suspend fun sendUPnPCommand(
        device: DiscoveredDevice,
        buttonName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Try UPnP rendering control
            val soapBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body>
<u:SendKey xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
<InstanceID>0</InstanceID>
<KeyName>${mapButtonName(buttonName) ?: buttonName}</KeyName>
</u:SendKey>
</s:Body>
</s:Envelope>"""

            val url = URL("http://${device.ipAddress}:${device.port}/upnp/control/RenderingControl1")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            conn.setRequestProperty(
                "SOAPAction",
                "\"urn:schemas-upnp-org:service:RenderingControl:1#SendKey\""
            )
            conn.doOutput = true

            conn.outputStream.use { it.write(soapBody.toByteArray()) }
            val ok = conn.responseCode in 200..299
            conn.disconnect()
            ok
        } catch (e: Exception) {
            Log.e(TAG, "UPnP command failed for $buttonName", e)
            false
        }
    }

    override fun isConnected(): Boolean = isDeviceConnected && connectedDevice != null

    override fun disconnect() {
        delegateHandler?.disconnect()
        delegateHandler = null
        connectedDevice = null
        isDeviceConnected = false
        detectedProtocol = null
    }

    override fun mapButtonName(dbButtonName: String): String? {
        return buttonMap[dbButtonName] ?: dbButtonName
    }
}
