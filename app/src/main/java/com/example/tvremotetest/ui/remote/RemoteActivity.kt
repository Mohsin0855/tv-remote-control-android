package com.example.tvremotetest.ui.remote

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.example.tvremotetest.R
import com.example.tvremotetest.databinding.ActivityRemoteBinding
import com.example.tvremotetest.ir.IrTransmitter

class RemoteActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BRAND = "extra_brand"
        const val EXTRA_CATEGORY = "extra_category"
    }

    private lateinit var binding: ActivityRemoteBinding
    private lateinit var viewModel: RemoteViewModel
    private lateinit var irTransmitter: IrTransmitter
    private lateinit var buttonAdapter: RemoteButtonAdapter

    private var brand: String = ""
    private var category: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRemoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        brand = intent.getStringExtra(EXTRA_BRAND) ?: ""
        category = intent.getStringExtra(EXTRA_CATEGORY) ?: ""

        irTransmitter = IrTransmitter(this)

        setupToolbar()
        setupRecyclerView()
        setupViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "$brand - $category"
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        buttonAdapter = RemoteButtonAdapter { remoteButton ->
            val frequency = remoteButton.frequency ?: 0
            val irFrame = remoteButton.irRemoteFrame ?: ""

            if (frequency > 0 && irFrame.isNotBlank()) {
                val success = irTransmitter.transmit(frequency, irFrame)
                if (success) {
                    Toast.makeText(this, R.string.signal_sent, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, R.string.signal_failed, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, R.string.signal_failed, Toast.LENGTH_SHORT).show()
            }
        }

        binding.rvRemoteButtons.apply {
            layoutManager = GridLayoutManager(this@RemoteActivity, 3)
            adapter = buttonAdapter
            setHasFixedSize(false)
        }
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[RemoteViewModel::class.java]

        // Load remote fragments for this brand+category
        viewModel.getFragmentsLiveData(brand, category).observe(this) { fragments ->
            if (fragments.isNotEmpty()) {
                setupSpinner(fragments)
            }
        }

        // Observe buttons for the selected fragment
        viewModel.buttons.observe(this) { buttons ->
            buttonAdapter.submitList(buttons)
        }
    }

    private fun setupSpinner(fragments: List<String>) {
        val spinnerAdapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, fragments
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as TextView).setTextColor(resources.getColor(android.R.color.white, theme))
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as TextView).apply {
                    setTextColor(resources.getColor(android.R.color.white, theme))
                    setBackgroundColor(resources.getColor(android.R.color.black, theme))
                    setPadding(32, 24, 32, 24)
                }
                return view
            }
        }
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFragments.adapter = spinnerAdapter

        binding.spinnerFragments.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.selectFragment(fragments[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}
