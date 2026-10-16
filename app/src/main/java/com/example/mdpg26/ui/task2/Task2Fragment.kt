package com.example.mdpg26.ui.task2

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
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
import com.example.mdpg26.viewmodel.LapTimerState
import com.example.mdpg26.viewmodel.Task2State
import com.example.mdpg26.viewmodel.Task2ViewModel
import com.example.mdpg26.viewmodel.formatLapTime
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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

    // Last lap-timer state drawn on this view, so the "lap complete" flash plays only on a live
    // Running -> Finished change, not when a recreated view (rotation, tab switch) first shows
    // an already-finished time.
    private var shownLapTimer: LapTimerState? = null
    private var lapPulse: AnimatorSet? = null

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
        task2ViewModel.bindStart(bluetoothViewModel.task2Started)
        task2ViewModel.bindDone(bluetoothViewModel.task2Done, bluetoothViewModel::logLocal)

        binding.recyclerMessages.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.recyclerMessages.adapter = messageAdapter

        binding.connectionPill.setOnClickListener {
            if (bluetoothViewModel.connectionState.value is ConnectionUiState.Disconnected) {
                DeviceListBottomSheet().show(parentFragmentManager, DeviceListBottomSheet.TAG)
            }
        }

        binding.btnCalibrateStraight.text = twoLineButtonLabel(
            getString(R.string.task2_calib_title),
            getString(R.string.task2_calib_straight)
        )

        binding.btnReset.setOnClickListener { confirmReset() }
        binding.btnClearLog.setOnClickListener { confirmClearLog() }
        binding.btnCalibrateStraight.setOnClickListener {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            bluetoothViewModel.sendMessage(RobotCommands.CALIBRATE_STRAIGHT)
        }
        binding.btnGyroInitCalib.setOnClickListener {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            bluetoothViewModel.sendMessage(RobotCommands.GYRO_INIT_CALIB)
        }
        binding.btnStart.setOnClickListener {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            // Cleared before sending so a retry run never shows the previous run's arrows.
            task2ViewModel.clearTargets()
            bluetoothViewModel.sendRaw(RobotCommands.TASK2_START)
        }

        observeState()
    }

    /** "CALIBRATE" bold over the smaller, dimmed raw command name, e.g. "calibStraight". */
    private fun twoLineButtonLabel(title: String, subtitle: String): CharSequence {
        val text = "$title\n$subtitle"
        return SpannableString(text).apply {
            val subtitleStart = title.length + 1
            setSpan(RelativeSizeSpan(0.55f), subtitleStart, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.button_subtitle_text)),
                subtitleStart, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_task2_reset_title)
            .setMessage(R.string.confirm_task2_reset_body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_reset) { _, _ ->
                view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                task2ViewModel.clearTargets()
                task2ViewModel.resetLapTimer()
            }
            .show()
    }

    private fun confirmClearLog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_clear_log_title)
            .setMessage(R.string.confirm_clear_log_body)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_clear) { _, _ -> bluetoothViewModel.clearMessages() }
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
                    // collectLatest cancels the tick loop as soon as the state changes; the
                    // repeatOnLifecycle scope cancels it whenever the tab isn't visible.
                    task2ViewModel.lapTimer.collectLatest { state ->
                        renderLapTimer(state)
                        if (state is LapTimerState.Running) {
                            while (true) {
                                binding.textLapTime.text =
                                    formatLapTime(System.currentTimeMillis() - state.startedAt)
                                delay(LAP_TICK_MS)
                            }
                        }
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

    private fun renderLapTimer(state: LapTimerState) {
        val colorRes = when (state) {
            LapTimerState.Idle -> R.color.lap_timer_idle
            is LapTimerState.Running -> R.color.lap_timer_running
            is LapTimerState.Finished -> R.color.lap_timer_finished
        }
        binding.textLapTime.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
        when (state) {
            LapTimerState.Idle -> binding.textLapTime.setText(R.string.task2_lap_idle)
            is LapTimerState.Running -> Unit // ticked by observeState()
            is LapTimerState.Finished -> binding.textLapTime.text = formatLapTime(state.durationMs)
        }

        val previous = shownLapTimer
        shownLapTimer = state
        lapPulse?.cancel()
        lapPulse = null
        binding.lapTimerContainer.scaleX = 1f
        binding.lapTimerContainer.scaleY = 1f
        if (state is LapTimerState.Finished) {
            if (previous != null && previous !is LapTimerState.Finished) {
                pulseLapTimer()
            } else {
                binding.lapTimerGlow.alpha = LAP_GLOW_RESTING_ALPHA
            }
        } else {
            binding.lapTimerGlow.alpha = 0f
        }
    }

    /** "Lap complete": the gold halo flashes twice and the plate pops, then a faint glow stays. */
    private fun pulseLapTimer() {
        val glow = ObjectAnimator.ofFloat(
            binding.lapTimerGlow, View.ALPHA, 0f, 1f, 0.2f, 1f, LAP_GLOW_RESTING_ALPHA
        ).setDuration(1200)
        val pop = ObjectAnimator.ofPropertyValuesHolder(
            binding.lapTimerContainer,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f, 1f, 1.05f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f, 1f, 1.05f, 1f)
        ).setDuration(800)
        lapPulse = AnimatorSet().apply {
            playTogether(glow, pop)
            start()
        }
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
        // the buttons say so rather than failing with a snackbar. Alpha is set on each button's
        // glow-halo FrameLayout wrapper (its parent), not the button itself, so the glow dims
        // along with the button instead of staying lit behind a faded one.
        val connected = state is ConnectionUiState.Connected
        listOf(binding.btnCalibrateStraight, binding.btnGyroInitCalib, binding.btnStart).forEach {
            it.isEnabled = connected
            (it.parent as View).alpha = if (connected) 1f else 0.25f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lapPulse?.cancel()
        lapPulse = null
        shownLapTimer = null
        _binding = null
    }
}

private const val LAP_TICK_MS = 33L
private const val LAP_GLOW_RESTING_ALPHA = 0.35f
