package com.example.tvremotetest.wifi

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates WiFi remote control operations.
 * Manages discovery, connection, and command sending.
 */
class WifiRemoteManager {

    companion object {
        private const val TAG = "WifiRemoteManager"
    }

    private var currentHandler: WifiProtocolHandler? = null
    private var currentBrand: String? = null
    private var connectedDevice: DiscoveredDevice? = null

    val isConnected: Boolean
        get() = currentHandler?.isConnected() == true

    val connectedDeviceName: String?
        get() = connectedDevice?.displayName

    val protocolName: String?
        get() = currentHandler?.protocolName

    /**
     * Discover Smart TVs for a specific brand.
     */
    suspend fun discoverDevices(brand: String, timeoutMs: Long = 4000): List<DiscoveredDevice> =
        withContext(Dispatchers.IO) {
            val handler = BrandProtocolMap.getHandler(brand)
            try {
                val devices = handler.discover(timeoutMs)
                Log.d(TAG, "Found ${devices.size} devices for $brand")
                devices
            } catch (e: Exception) {
                Log.e(TAG, "Discovery failed for $brand", e)
                emptyList()
            }
        }

    /**
     * Connect to a specific device.
     */
    suspend fun connect(brand: String, device: DiscoveredDevice): Boolean {
        // Disconnect from any existing connection
        disconnect()

        val handler = BrandProtocolMap.getHandler(brand)
        val success = handler.connect(device)

        if (success) {
            currentHandler = handler
            currentBrand = brand
            connectedDevice = device
            Log.d(TAG, "Connected to ${device.displayName} via ${handler.protocolName}")
        }

        return success
    }

    /**
     * Connect to a device by IP address (manual entry).
     */
    suspend fun connectByIp(brand: String, ipAddress: String): Boolean {
        val handler = BrandProtocolMap.getHandler(brand)
        val device = DiscoveredDevice(
            name = "$brand TV",
            ipAddress = ipAddress,
            brand = brand,
            port = handler.defaultPort
        )
        return connect(brand, device)
    }

    /**
     * Send a key command to the connected device.
     */
    suspend fun sendCommand(buttonName: String): Boolean {
        val handler = currentHandler ?: return false

        return try {
            handler.sendKey(buttonName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command $buttonName", e)
            false
        }
    }

    /**
     * Disconnect from the current device.
     */
    fun disconnect() {
        currentHandler?.disconnect()
        currentHandler = null
        currentBrand = null
        connectedDevice = null
    }
}
