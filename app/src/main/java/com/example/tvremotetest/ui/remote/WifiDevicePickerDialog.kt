package com.example.tvremotetest.ui.remote

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.tvremotetest.R
import com.example.tvremotetest.wifi.DiscoveredDevice
import com.example.tvremotetest.wifi.WifiRemoteManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/**
 * Dialog for discovering and connecting to WiFi Smart TVs.
 */
class WifiDevicePickerDialog : DialogFragment() {

    companion object {
        private const val ARG_BRAND = "brand"

        fun newInstance(brand: String): WifiDevicePickerDialog {
            return WifiDevicePickerDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_BRAND, brand)
                }
            }
        }
    }

    private var brand: String = ""
    private val wifiManager = WifiRemoteManager()
    var onDeviceConnected: ((WifiRemoteManager) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        brand = arguments?.getString(ARG_BRAND) ?: ""
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        // Progress bar
        val progressBar = ProgressBar(context).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
        }

        // Status text
        val statusText = TextView(context).apply {
            text = getString(R.string.wifi_searching, brand)
            textSize = 14f
            setTextColor(0xFF8E8E93.toInt())
            setPadding(0, 16, 0, 16)
            gravity = android.view.Gravity.CENTER
        }

        // Device list container
        val deviceList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Manual IP entry section
        val manualSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        val ipInputLayout = TextInputLayout(context).apply {
            hint = getString(R.string.wifi_enter_ip)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }

        val ipInput = TextInputEditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText("192.168.")
        }
        ipInputLayout.addView(ipInput)

        val connectBtn = MaterialButton(context).apply {
            text = getString(R.string.wifi_connect)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }

        manualSection.addView(ipInputLayout)
        manualSection.addView(connectBtn)

        // "Enter IP manually" link
        val manualLink = TextView(context).apply {
            text = getString(R.string.wifi_manual_ip)
            setTextColor(0xFFE94560.toInt())
            textSize = 13f
            setPadding(0, 24, 0, 8)
            gravity = android.view.Gravity.CENTER
            setOnClickListener {
                manualSection.visibility = View.VISIBLE
                this.visibility = View.GONE
            }
        }

        container.addView(progressBar)
        container.addView(statusText)
        container.addView(deviceList)
        container.addView(manualLink)
        container.addView(manualSection)

        // Manual connect handler
        connectBtn.setOnClickListener {
            val ip = ipInput.text?.toString()?.trim() ?: ""
            if (ip.length >= 7) {
                progressBar.visibility = View.VISIBLE
                statusText.text = getString(R.string.wifi_connecting)

                lifecycleScope.launch {
                    val success = wifiManager.connectByIp(brand, ip)
                    if (success) {
                        onDeviceConnected?.invoke(wifiManager)
                        dismiss()
                    } else {
                        progressBar.visibility = View.GONE
                        statusText.text = getString(R.string.wifi_connect_failed)
                        Toast.makeText(context, R.string.wifi_connect_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Start discovery
        lifecycleScope.launch {
            val devices = wifiManager.discoverDevices(brand)

            progressBar.visibility = View.GONE

            if (devices.isEmpty()) {
                statusText.text = getString(R.string.wifi_no_devices)
                manualSection.visibility = View.VISIBLE
                manualLink.visibility = View.GONE
            } else {
                statusText.text = getString(R.string.wifi_found_devices, devices.size)

                for (device in devices) {
                    val card = createDeviceCard(device, statusText, progressBar, container)
                    deviceList.addView(card)
                }
            }
        }

        return MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.wifi_discover_title))
            .setView(container)
            .setNegativeButton(R.string.wifi_cancel) { _, _ -> dismiss() }
            .create()
    }

    private fun createDeviceCard(
        device: DiscoveredDevice,
        statusText: TextView,
        progressBar: ProgressBar,
        container: ViewGroup
    ): MaterialCardView {
        val context = requireContext()

        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
            radius = 16f
            cardElevation = 4f
            setCardBackgroundColor(0xFF1A1A2E.toInt())
            strokeColor = 0xFF0F3460.toInt()
            strokeWidth = 2
        }

        val cardContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        val nameText = TextView(context).apply {
            text = device.displayName
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
        }

        val infoText = TextView(context).apply {
            text = "${device.brand} • ${device.ipAddress}"
            textSize = 12f
            setTextColor(0xFF8E8E93.toInt())
        }

        cardContent.addView(nameText)
        cardContent.addView(infoText)
        card.addView(cardContent)

        card.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            statusText.text = getString(R.string.wifi_connecting)

            lifecycleScope.launch {
                val success = wifiManager.connect(brand, device)
                if (success) {
                    onDeviceConnected?.invoke(wifiManager)
                    dismiss()
                } else {
                    progressBar.visibility = View.GONE
                    statusText.text = getString(R.string.wifi_connect_failed)
                    Toast.makeText(context, R.string.wifi_connect_failed, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        return card
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!wifiManager.isConnected) {
            wifiManager.disconnect()
        }
    }
}
