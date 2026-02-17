package com.example.tvremotetest.ui.casting

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.tvremotetest.R
import com.example.tvremotetest.casting.ScreenCaptureService
import com.example.tvremotetest.databinding.ActivityCastingBinding

class CastingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCastingBinding
    private lateinit var viewModel: CastingViewModel

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startCaptureService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCastingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupViewModel()
        setupClickListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[CastingViewModel::class.java]

        viewModel.isStreaming.observe(this) { streaming ->
            updateUI(streaming)
        }

        viewModel.streamUrl.observe(this) { url ->
            binding.tvUrl.text = url ?: ""
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnToggleCast.setOnClickListener {
            if (ScreenCaptureService.isRunning) {
                stopCasting()
            } else {
                startCasting()
            }
        }

        binding.btnCopyUrl.setOnClickListener {
            val url = binding.tvUrl.text.toString()
            if (url.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Stream URL", url))
                Toast.makeText(this, R.string.url_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCasting() {
        if (!viewModel.prepareStreamUrl()) return

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun startCaptureService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopCasting() {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        startService(serviceIntent)
    }

    private fun updateUI(streaming: Boolean) {
        if (streaming) {
            binding.btnToggleCast.text = getString(R.string.stop_casting)
            binding.btnToggleCast.setBackgroundColor(resources.getColor(android.R.color.holo_red_dark, null))
            binding.tvStatus.text = getString(R.string.cast_active)
            binding.tvStatus.setTextColor(0xFF4CAF50.toInt())
            binding.cardUrl.visibility = View.VISIBLE

            // Pulse animation on cast icon
            val pulse = AlphaAnimation(1.0f, 0.4f).apply {
                duration = 800
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            binding.ivCastIcon.startAnimation(pulse)
        } else {
            binding.btnToggleCast.text = getString(R.string.start_casting)
            binding.tvStatus.text = getString(R.string.cast_ready)
            binding.tvStatus.setTextColor(0xFFFFFFFF.toInt())
            binding.cardUrl.visibility = View.GONE
            binding.ivCastIcon.clearAnimation()
        }
    }
}
