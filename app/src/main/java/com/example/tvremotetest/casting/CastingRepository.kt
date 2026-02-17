package com.example.tvremotetest.casting

import android.content.Context
import android.net.wifi.WifiManager
import java.util.Locale

class CastingRepository(private val context: Context) {

    companion object {
        const val SERVER_PORT = 8080
    }

    fun getDeviceIpAddress(): String? {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ipAddress = wifiInfo.ipAddress

        if (ipAddress == 0) return null

        return String.format(
            Locale.US,
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    }

    fun getStreamUrl(): String? {
        val ip = getDeviceIpAddress() ?: return null
        return "http://$ip:$SERVER_PORT"
    }

    fun isStreaming(): Boolean = ScreenCaptureService.isRunning
}
