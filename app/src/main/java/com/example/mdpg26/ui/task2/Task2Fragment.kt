package com.example.mdpg26.ui.task2

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mdpg26.R
import com.example.mdpg26.bluetooth.ConnectionUiState
import com.example.mdpg26.bluetooth.RobotCommands
import com.example.mdpg26.bluetooth.Task2Direction
import com.example.mdpg26.databinding.FragmentTask2Binding
import com.example.mdpg26.ui.devicepicker.DeviceListBottomSheet
import com.example.mdpg26.ui.terminal.MessageAdapter
import com.example.mdpg26.viewmodel.BluetoothViewModel
import com.example.mdpg26.viewmodel.Task2State
import com.example.mdpg26.viewmodel.Task2ViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Task 2 (Fastest Car): a schematic of the carpark and both obstacles that fills in each
 * obstacle's arrow as the RPi's `TARGET,<id>,LEFT|RIGHT` results arrive, plus the two buttons
 * used on the day — CALIB_STRAIGHT (same command as the Control tab's) and START (sent raw, no
 * terminator) — with a log of sent/received messages underneath. Shares the activity's
 * [BluetoothViewModel], so the connection and message history outlive this screen.
 */
class Task2Fragment : Fragment() {

    private var _binding: FragmentTask2Binding? = null
    private val binding get() = _binding!!

    private val bluetoothViewModel: BluetoothViewModel by activityViewModels()
    private val task2ViewModel: Task2ViewModel by activityViewModels()
    private val messageAdapter = MessageAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTask2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        task2ViewModel.bindTargets(bluetoothViewModel.task2Targets)

        binding.recyclerMessages.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.recyclerMessages.adapter = messageAdapter

        binding.connectionPill.setOnClickListener {
            if (bluetoothViewModel.connectionState.value is ConnectionUiState.Disconnected) {
                DeviceListBottomSheet().show(parentFragmentManager, DeviceListBottomSheet.TAG)
            }
        }

        binding.btnReset.setOnClickListener { confirmReset() }
        binding.btnCalibrateStraight.setOnClickListener {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            bluetoothViewModel.sendMessage(RobotCommands.CALIBRATE_STRAIGHT)
        }
        binding.btnStart.setOnClickListener {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            // Cleared before sending so a retry run never shows the previous run's arrows.
            task2ViewModel.clearTargets()
            bluetoothViewModel.sendRaw(RobotCommands.TASK2_START)
        }

        observeState()
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_task2_reset_title)
            .setMessage(R.string.confirm_task2_reset_body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_reset) { _, _ ->
                view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                task2ViewModel.clearTargets()
            }
            .show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    task2ViewModel.state.collect { state ->
                        binding.task2ArenaView.setState(state)
                        val summary = summaryText(state)
                        binding.textTargets.text = summary
                        binding.task2ArenaView.contentDescription =
                            getString(R.string.cd_task2_arena_fmt, summary)
                    }
                }
                launch {
                    bluetoothViewModel.connectionState.collect { renderConnection(it) }
                }
                launch {
                    bluetoothViewModel.messages.collect { messages ->
                        messageAdapter.submitList(messages)
                        binding.textMessagesEmpty.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                        if (messages.isNotEmpty()) {
                            binding.recyclerMessages.scrollToPosition(messages.size - 1)
                        }
                    }
                }
            }
        }
    }

    private fun summaryText(state: Task2State): String =
        getString(R.string.task2_summary_fmt, directionLabel(state.obstacle1), directionLabel(state.obstacle2))

    private fun directionLabel(direction: Task2Direction?): String = when (direction) {
        Task2Direction.LEFT -> getString(R.string.task2_direction_left)
        Task2Direction.RIGHT -> getString(R.string.task2_direction_right)
        null -> getString(R.string.task2_direction_unknown)
    }

    private fun renderConnection(state: ConnectionUiState) {
        val (dotColorRes, label) = when (state) {
            is ConnectionUiState.Connected ->
                R.color.status_connected to getString(R.string.status_connected_title)
            is ConnectionUiState.Connecting ->
                R.color.status_connecting to getString(R.string.status_connecting_title)
            is ConnectionUiState.Reconnecting ->
                R.color.status_connecting to getString(R.string.status_reconnecting_title_fmt, state.attempt)
            is ConnectionUiState.Disconnected ->
                R.color.status_disconnected to getString(R.string.status_disconnected_title)
        }
        binding.connectionDot.background.mutate().setTint(ContextCompat.getColor(requireContext(), dotColorRes))
        binding.textConnection.text = label

        // Same convention as the Control tab: commands can't go anywhere while disconnected, so
        // the buttons say so rather than failing with a snackbar.
        val connected = state is ConnectionUiState.Connected
        listOf(binding.btnCalibrateStraight, binding.btnStart).forEach {
            it.isEnabled = connected
            it.alpha = if (connected) 1f else 0.25f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
