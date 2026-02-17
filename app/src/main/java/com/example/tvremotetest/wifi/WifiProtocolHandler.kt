package com.example.tvremotetest.wifi

/**
 * Represents a Smart TV discovered on the local network.
 */
data class DiscoveredDevice(
    val name: String,
    val ipAddress: String,
    val brand: String,
    val port: Int,
    val serviceType: String = "",
    val uniqueId: String = ""
) {
    val displayName: String
        get() = if (name.isNotBlank()) "$name ($ipAddress)" else ipAddress
}

/**
 * Base interface for WiFi remote protocol handlers.
 * Each Smart TV brand implements this with its specific protocol.
 */
interface WifiProtocolHandler {

    /** Human-readable protocol name */
    val protocolName: String

    /** Default port for this protocol */
    val defaultPort: Int

    /**
     * Discover devices on the network that this handler can control.
     * @param timeoutMs discovery timeout in milliseconds
     * @return list of discovered devices
     */
    suspend fun discover(timeoutMs: Long = 3000): List<DiscoveredDevice>

    /**
     * Connect to a specific device.
     * @return true if connection was successful
     */
    suspend fun connect(device: DiscoveredDevice): Boolean

    /**
     * Send a key command to the connected device.
     * @param buttonName the button name from the database (e.g. "Power", "Volume_Up")
     * @return true if command was sent successfully
     */
    suspend fun sendKey(buttonName: String): Boolean

    /**
     * Check if currently connected to a device.
     */
    fun isConnected(): Boolean

    /**
     * Disconnect from the current device.
     */
    fun disconnect()

    /**
     * Map a database button name to this protocol's key code.
     * e.g. "Volume_Up" → "KEY_VOLUP" for Samsung
     */
    fun mapButtonName(dbButtonName: String): String?
}
