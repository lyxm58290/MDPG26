package com.example.mdpg26.ui.terminal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mdpg26.bluetooth.ConnectionUiState
import com.example.mdpg26.databinding.FragmentTerminalBinding
import com.example.mdpg26.ui.devicepicker.DeviceListBottomSheet
import com.example.mdpg26.viewmodel.BluetoothViewModel
import kotlinx.coroutines.launch

/** Send/receive terminal for the Bluetooth serial link — checklist C.1. */
class TerminalFragment : Fragment() {

    private var _binding: FragmentTerminalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BluetoothViewModel by activityViewModels()
    private val adapter = MessageAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTerminalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerMessages.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.recyclerMessages.adapter = adapter

        binding.btnSend.setOnClickListener { sendCurrentInput() }
        binding.editMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentInput()
                true
            } else {
                false
            }
        }
        binding.bannerConnectAction.setOnClickListener { openDevicePicker() }

        observeState()
    }

    private fun sendCurrentInput() {
        val text = binding.editMessage.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        viewModel.sendMessage(text)
        binding.editMessage.text?.clear()
    }

    private fun openDevicePicker() {
        DeviceListBottomSheet().show(parentFragmentManager, DeviceListBottomSheet.TAG)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        adapter.submitList(messages)
                        binding.emptyState.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                        if (messages.isNotEmpty()) {
                            binding.recyclerMessages.scrollToPosition(messages.size - 1)
                        }
                    }
                }
                launch {
                    viewModel.connectionState.collect { state ->
                        binding.bannerNotConnected.visibility =
                            if (state is ConnectionUiState.Connected) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    viewModel.errors.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
