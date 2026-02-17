package com.example.tvremotetest.wifi

import com.example.tvremotetest.wifi.handlers.*

/**
 * Maps all 94 database brands to their WiFi protocol handler.
 */
object BrandProtocolMap {

    /**
     * Get the appropriate WiFi protocol handler for a brand.
     */
    fun getHandler(brandName: String): WifiProtocolHandler {
        return when (brandName.lowercase().trim()) {
            // Samsung
            "samsung" -> SamsungWifiHandler()

            // LG
            "lg" -> LGWifiHandler()

            // Sony
            "sony" -> SonyWifiHandler()

            // Roku and Roku-based brands
            "roku" -> RokuWifiHandler()
            "tcl" -> RokuWifiHandler()  // Most TCL TVs use Roku OS

            // Philips
            "philips" -> PhilipsWifiHandler()

            // Panasonic
            "panasonic" -> PanasonicWifiHandler()

            // Vizio
            "vizio" -> VizioWifiHandler()

            // Brands that may use Android TV (generic handler will port-probe)
            "xiaomi", "hisense", "sharp", "toshiba",
            "haier", "skyworth", "tcl", "vu",
            "realme", "oneplus", "nokia" -> GenericWifiHandler()

            // All other brands - generic handler
            else -> GenericWifiHandler()
        }
    }

    /**
     * Check if a brand has dedicated WiFi protocol support.
     */
    fun hasDedicatedSupport(brandName: String): Boolean {
        return when (brandName.lowercase().trim()) {
            "samsung", "lg", "sony", "roku", "tcl",
            "philips", "panasonic", "vizio" -> true
            else -> false
        }
    }

    /**
     * Get the protocol name for a brand.
     */
    fun getProtocolName(brandName: String): String {
        return getHandler(brandName).protocolName
    }
}
