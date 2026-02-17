package com.example.tvremotetest

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tvremotetest.databinding.ActivityMainBinding
import com.example.tvremotetest.ir.IrTransmitter
import com.example.tvremotetest.ui.brandlist.BrandAdapter
import com.example.tvremotetest.ui.brandlist.BrandListViewModel
import com.example.tvremotetest.ui.casting.CastingActivity
import com.example.tvremotetest.ui.remote.RemoteActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: BrandListViewModel
    private lateinit var brandAdapter: BrandAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Set toolbar as action bar for menu support
        setSupportActionBar(binding.toolbar)

        // Check IR sensor
        val irTransmitter = IrTransmitter(this)
        if (!irTransmitter.hasIrEmitter()) {
            showIrNotSupportedDialog()
            return
        }

        setupRecyclerView()
        setupViewModel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_cast -> {
                startActivity(Intent(this, CastingActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showIrNotSupportedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.ir_not_supported_title)
            .setMessage(R.string.ir_not_supported_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun setupRecyclerView() {
        brandAdapter = BrandAdapter { brandDevice ->
            val intent = Intent(this, RemoteActivity::class.java).apply {
                putExtra(RemoteActivity.EXTRA_BRAND, brandDevice.brandModel)
                putExtra(RemoteActivity.EXTRA_CATEGORY, brandDevice.deviceCategory)
            }
            startActivity(intent)
        }

        binding.rvBrands.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = brandAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[BrandListViewModel::class.java]
        viewModel.brandsWithCategory.observe(this) { brands ->
            brandAdapter.submitList(brands)
        }
    }
}