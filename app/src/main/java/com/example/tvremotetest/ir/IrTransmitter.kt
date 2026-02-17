package com.example.tvremotetest.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log

class IrTransmitter(context: Context) {

    private val irManager: ConsumerIrManager? =
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    companion object {
        private const val TAG = "IrTransmitter"
    }

    /**
     * Check if the device has an IR blaster.
     */
    fun hasIrEmitter(): Boolean {
        return irManager?.hasIrEmitter() == true
    }

    /**
     * Transmit an IR signal.
     * @param frequency The carrier frequency in Hertz
     * @param irFrameString Comma-separated string of alternating on/off pattern durations in microseconds
     */
    fun transmit(frequency: Int, irFrameString: String): Boolean {
        if (irManager == null || !irManager.hasIrEmitter()) {
            Log.e(TAG, "No IR emitter available")
            return false
        }

        return try {
            val pattern = irFrameString
                .split(",")
                .map { it.trim().toInt() }
                .toIntArray()

            irManager.transmit(frequency, pattern)
            Log.d(TAG, "Transmitted IR signal: freq=$frequency, pattern size=${pattern.size}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error transmitting IR signal", e)
            false
        }
    }
}
