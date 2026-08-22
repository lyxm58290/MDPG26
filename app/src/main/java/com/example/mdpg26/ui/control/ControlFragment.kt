package com.example.mdpg26.ui.control

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.mdpg26.R
import com.example.mdpg26.bluetooth.ConnectionUiState
import com.example.mdpg26.bluetooth.MessageDirection
import com.example.mdpg26.bluetooth.RobotCommands
import com.example.mdpg26.databinding.FragmentControlBinding
import com.example.mdpg26.viewmodel.BluetoothViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Robot movement + mission controls (checklist C.3): labeled buttons drive the robot over the
 * Bluetooth link. Command strings match AMDTool's default "Received Commands" mapping so the
 * same buttons work against AMDtool.exe during testing and the real RPi on the day.
 */
class ControlFragment : Fragment() {

    private var _binding: FragmentControlBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BluetoothViewModel by activityViewModels()

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
        _binding = FragmentControlBinding.inflate(inflater, container, false)
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
            sendCommand(RobotCommands.SEND_ARENA, getString(R.string.cmd_send_arena))
        }

        observeState()
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
                        binding.bannerNotConnected.visibility = if (connected) View.GONE else View.VISIBLE
                        setControlsEnabled(connected)
                    }
                }
                launch {
                    viewModel.messages.collect { messages ->
                        val lastSent = messages.lastOrNull { it.direction == MessageDirection.SENT }
                        binding.textLastCommand.text = if (lastSent != null) {
                            getString(R.string.last_command_fmt, lastSent.text)
                        } else {
                            getString(R.string.last_command_none)
                        }
                    }
                }
            }
        }
    }

    private fun setControlsEnabled(enabled: Boolean) {
        movementButtons.forEach {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else 0.4f
        }
        // The mission buttons use saturated fills (primary/tertiary), which read as "still on"
        // at 40% alpha — dim them further so the disabled state is unambiguous at a glance.
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
