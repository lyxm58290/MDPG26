package com.example.mdpg26.ui.main

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.mdpg26.R
import com.example.mdpg26.bluetooth.ConnectionUiState
import com.example.mdpg26.databinding.ActivityMainBinding
import com.example.mdpg26.ui.devicepicker.DeviceListBottomSheet
import com.example.mdpg26.viewmodel.BluetoothViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: BluetoothViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.setPadding(
                binding.toolbar.paddingLeft, bars.top, binding.toolbar.paddingRight, binding.toolbar.paddingBottom
            )
            binding.bottomNav.setPadding(
                binding.bottomNav.paddingLeft, binding.bottomNav.paddingTop, binding.bottomNav.paddingRight, bars.bottom
            )
            insets
        }

        setupNavigation()
        setupStatusStrip()
        observeConnectionState()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)
    }

    private fun setupStatusStrip() {
        binding.statusStrip.setOnClickListener {
            when (viewModel.connectionState.value) {
                is ConnectionUiState.Connected, is ConnectionUiState.Reconnecting -> showConnectionMenu()
                is ConnectionUiState.Disconnected -> openDevicePicker()
                is ConnectionUiState.Connecting -> Unit
            }
        }
        binding.btnConnect.setOnClickListener { openDevicePicker() }
        binding.btnStatusMenu.setOnClickListener { showConnectionMenu() }
    }

    private fun openDevicePicker() {
        DeviceListBottomSheet().show(supportFragmentManager, DeviceListBottomSheet.TAG)
    }

    private fun showConnectionMenu() {
        val anchor = if (binding.btnStatusMenu.visibility == View.VISIBLE) binding.btnStatusMenu else binding.statusStrip
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.connection_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_disconnect -> {
                    viewModel.disconnect()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun observeConnectionState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.connectionState.collect { renderStatus(it) } }
                launch {
                    viewModel.errors.collect { message ->
                        Snackbar.make(binding.main, message, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun renderStatus(state: ConnectionUiState) {
        val dotColorRes: Int
        val progressVisible: Boolean
        val connectVisible: Boolean
        val menuVisible: Boolean
        val stripClickable: Boolean

        when (state) {
            is ConnectionUiState.Disconnected -> {
                dotColorRes = R.color.status_disconnected
                binding.statusTitle.text = getString(R.string.status_disconnected_title)
                binding.statusSubtitle.text = getString(R.string.status_disconnected_subtitle)
                progressVisible = false
                connectVisible = true
                menuVisible = false
                stripClickable = true
            }
            is ConnectionUiState.Connecting -> {
                dotColorRes = R.color.status_connecting
                binding.statusTitle.text = getString(R.string.status_connecting_title)
                binding.statusSubtitle.text = getString(R.string.status_connecting_subtitle_fmt, state.deviceName)
                progressVisible = true
                connectVisible = false
                menuVisible = false
                stripClickable = false
            }
            is ConnectionUiState.Connected -> {
                dotColorRes = R.color.status_connected
                binding.statusTitle.text = getString(R.string.status_connected_title)
                binding.statusSubtitle.text = state.deviceName
                progressVisible = false
                connectVisible = false
                menuVisible = true
                stripClickable = true
            }
            is ConnectionUiState.Reconnecting -> {
                dotColorRes = R.color.status_connecting
                binding.statusTitle.text = getString(R.string.status_reconnecting_title_fmt, state.attempt)
                binding.statusSubtitle.text = state.deviceName
                progressVisible = true
                connectVisible = false
                menuVisible = true
                stripClickable = true
            }
        }

        binding.statusDot.background.mutate().setTint(ContextCompat.getColor(this, dotColorRes))
        binding.statusProgress.visibility = if (progressVisible) View.VISIBLE else View.GONE
        binding.btnConnect.visibility = if (connectVisible) View.VISIBLE else View.GONE
        binding.btnStatusMenu.visibility = if (menuVisible) View.VISIBLE else View.GONE
        binding.statusStrip.isClickable = stripClickable
    }
}
