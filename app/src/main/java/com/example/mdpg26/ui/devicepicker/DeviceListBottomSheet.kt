package com.example.mdpg26.ui.devicepicker

import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mdpg26.R
import com.example.mdpg26.bluetooth.BluetoothPermissions
import com.example.mdpg26.bluetooth.ConnectionUiState
import com.example.mdpg26.databinding.BottomsheetDeviceListBinding
import com.example.mdpg26.viewmodel.BluetoothViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * Bluetooth device picker: scans for nearby devices, lists paired devices, and connects on tap.
 * Satisfies checklist C.2 — initiate scanning, selection and connection with a Bluetooth device.
 */
class DeviceListBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetDeviceListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BluetoothViewModel by activityViewModels()
    private lateinit var adapter: DeviceListAdapter

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshAndMaybeScan() }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshAndMaybeScan() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetDeviceListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DeviceListAdapter(
            onDeviceClick = { item ->
                viewModel.connect(item.device.address)
                dismiss()
            },
            onActionClick = { item ->
                viewModel.connect(item.address)
                dismiss()
            }
        )
        binding.recyclerDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerDevices.adapter = adapter

        binding.btnScan.setOnClickListener { refreshAndMaybeScan() }
        binding.btnEnableBluetooth.setOnClickListener {
            enableBluetoothLauncher.launch(android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
        binding.btnGrantPermission.setOnClickListener {
            permissionLauncher.launch(BluetoothPermissions.required())
        }

        observeState()
        refreshAndMaybeScan()
    }

    private fun refreshAndMaybeScan() {
        if (!viewModel.isBluetoothSupported()) return
        if (!viewModel.isBluetoothEnabled()) {
            renderList()
            return
        }
        if (!BluetoothPermissions.allGranted(requireContext())) {
            renderList()
            return
        }
        viewModel.refreshPairedDevices()
        viewModel.startDiscovery()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.pairedDevices.collect { renderList() } }
                launch { viewModel.scannedDevices.collect { renderList() } }
                launch { viewModel.isScanning.collect { renderList() } }
                launch { viewModel.lastDevice.collect { renderList() } }
                launch { viewModel.connectionState.collect { renderList() } }
            }
        }
    }

    private fun renderList() {
        if (_binding == null) return
        val hasPermission = BluetoothPermissions.allGranted(requireContext())
        val btEnabled = viewModel.isBluetoothEnabled()

        binding.stateBluetoothOff.visibility = if (!btEnabled) View.VISIBLE else View.GONE
        binding.statePermissionMissing.visibility =
            if (btEnabled && !hasPermission) View.VISIBLE else View.GONE
        val showList = btEnabled && hasPermission
        binding.recyclerDevices.visibility = if (showList) View.VISIBLE else View.GONE
        binding.btnScan.isEnabled = showList

        if (!showList) return

        val paired = viewModel.pairedDevices.value
        val scanning = viewModel.isScanning.value
        val connectedAddress = (viewModel.connectionState.value as? ConnectionUiState.Connected)?.deviceAddress
        val pairedAddresses = paired.map { it.address }.toSet()
        val available = viewModel.scannedDevices.value.filterNot { it.address in pairedAddresses }
        val last = viewModel.lastDevice.value

        val items = mutableListOf<DeviceListItem>()

        if (last != null && last.address != connectedAddress && last.address !in pairedAddresses) {
            items += DeviceListItem.Action(
                getString(R.string.picker_reconnect_last_fmt, last.name),
                last.address
            )
        }

        items += DeviceListItem.Header(getString(R.string.picker_section_paired), showProgress = false)
        if (paired.isEmpty()) {
            items += DeviceListItem.Empty(getString(R.string.picker_empty_paired))
        } else {
            items += paired.map { DeviceListItem.Device(it) }
        }

        items += DeviceListItem.Header(getString(R.string.picker_section_available), showProgress = scanning)
        if (available.isEmpty()) {
            val message = if (scanning) getString(R.string.picker_scanning) else getString(R.string.picker_empty_available)
            items += DeviceListItem.Empty(message)
        } else {
            items += available.map { DeviceListItem.Device(it) }
        }

        adapter.submitList(items)
    }

    override fun onDestroyView() {
        viewModel.stopDiscovery()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DeviceListBottomSheet"
    }
}
