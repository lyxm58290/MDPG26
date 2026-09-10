package com.example.mdpg26.ui.control

import android.content.pm.ActivityInfo
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
import com.example.mdpg26.R
import com.example.mdpg26.arena.ArenaProtocol
import com.example.mdpg26.bluetooth.ConnectionUiState
import com.example.mdpg26.bluetooth.MessageDirection
import com.example.mdpg26.bluetooth.RobotCommands
import com.example.mdpg26.databinding.FragmentWheelControlBinding
import com.example.mdpg26.ui.devicepicker.DeviceListBottomSheet
import com.example.mdpg26.viewmodel.ArenaViewModel
import com.example.mdpg26.viewmodel.BluetoothViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Revamp of [ControlFragment] as a single F1-style driving wheel: same commands (checklist C.3),
 * laid out landscape with every button mounted on the wheel's rim/hub instead of a gamepad body.
 * [ControlFragment] is kept around unchanged as a fallback — this is an alternative, not a
 * replacement, so both live as separate tabs.
 */
class WheelControlFragment : Fragment() {

    private var _binding: FragmentWheelControlBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BluetoothViewModel by activityViewModels()
    private val arenaViewModel: ArenaViewModel by activityViewModels()

    private val movementButtons: List<MaterialButton> by lazy {
        listOf(
            binding.btnForward, binding.btnReverse,
            binding.btnStrafeLeft, binding.btnStrafeRight,
            binding.btnRotateLeft, binding.btnRotateRight
        )
    }
    private val missionButtons: List<MaterialButton> by lazy {
        listOf(binding.btnBeginExplore, binding.btnBeginFastest, binding.btnSendArena)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWheelControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnForward.setOnClickListener {
            sendCommand(RobotCommands.FORWARD, getString(R.string.cmd_forward))
        }
        binding.btnReverse.setOnClickListener {
            sendCommand(RobotCommands.REVERSE, getString(R.string.cmd_reverse))
        }
        binding.btnStrafeLeft.setOnClickListener {
            sendCommand(RobotCommands.STRAFE_LEFT, getString(R.string.cmd_strafe_left))
        }
        binding.btnStrafeRight.setOnClickListener {
            sendCommand(RobotCommands.STRAFE_RIGHT, getString(R.string.cmd_strafe_right))
        }
        binding.btnRotateLeft.setOnClickListener {
            sendCommand(RobotCommands.ROTATE_LEFT, getString(R.string.cmd_rotate_left))
        }
        binding.btnRotateRight.setOnClickListener {
            sendCommand(RobotCommands.ROTATE_RIGHT, getString(R.string.cmd_rotate_right))
        }

        binding.btnBeginExplore.setOnClickListener {
            confirmThenSend(
                titleRes = R.string.confirm_begin_explore_title,
                bodyRes = R.string.confirm_begin_explore_body,
                command = RobotCommands.BEGIN_EXPLORE,
                label = getString(R.string.cmd_begin_explore)
            )
        }
        binding.btnBeginFastest.setOnClickListener {
            confirmThenSend(
                titleRes = R.string.confirm_begin_fastest_title,
                bodyRes = R.string.confirm_begin_fastest_body,
                command = RobotCommands.BEGIN_FASTEST,
                label = getString(R.string.cmd_begin_fastest)
            )
        }
        binding.btnSendArena.setOnClickListener {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            viewModel.sendMessage(ArenaProtocol.arenaSnapshot(arenaViewModel.state.value))
        }

        observeState()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    override fun onPause() {
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        super.onPause()
    }

    private fun confirmThenSend(titleRes: Int, bodyRes: Int, command: String, label: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setMessage(bodyRes)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_start) { _, _ -> sendCommand(command, label) }
            .show()
    }

    private fun sendCommand(command: String, label: String) {
        view?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        viewModel.sendMessage(command)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.connectionState.collect { state ->
                        val connected = state is ConnectionUiState.Connected
                        setControlsEnabled(connected)
                        updateScreen(connected, lastSentText())
                    }
                }
                launch {
                    viewModel.messages.collect {
                        val connected = viewModel.connectionState.value is ConnectionUiState.Connected
                        updateScreen(connected, lastSentText())
                    }
                }
            }
        }
    }

    private fun lastSentText(): String? =
        viewModel.messages.value.lastOrNull { it.direction == MessageDirection.SENT }?.text

    /**
     * The wheel's center MFD screen doubles as the connection banner — the global status strip
     * (checklist C.2) is hidden while this tab is active to make room for the wheel, so this is
     * the only place left to surface "not connected" and to reach the device picker from here.
     */
    private fun updateScreen(connected: Boolean, lastSent: String?) {
        if (connected) {
            binding.textWheelScreen.text = if (lastSent != null) {
                getString(R.string.last_command_fmt, lastSent)
            } else {
                getString(R.string.last_command_none)
            }
            binding.textWheelScreen.setTextColor(ContextCompat.getColor(requireContext(), R.color.wheel_display_text_ok))
            binding.textWheelScreen.setOnClickListener(null)
            binding.textWheelScreen.isClickable = false
        } else {
            binding.textWheelScreen.text = getString(R.string.control_banner_not_connected)
            binding.textWheelScreen.setTextColor(ContextCompat.getColor(requireContext(), R.color.wheel_display_text_alert))
            binding.textWheelScreen.setOnClickListener {
                DeviceListBottomSheet().show(parentFragmentManager, DeviceListBottomSheet.TAG)
            }
        }
    }

    private fun setControlsEnabled(enabled: Boolean) {
        movementButtons.forEach {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else 0.4f
        }
        missionButtons.forEach {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else 0.25f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
