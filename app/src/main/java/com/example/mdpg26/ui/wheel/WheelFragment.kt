package com.example.mdpg26.ui.wheel

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mdpg26.R
import com.example.mdpg26.arena.ArenaProtocol
import com.example.mdpg26.bluetooth.ConnectionUiState
import com.example.mdpg26.bluetooth.RobotCommands
import com.example.mdpg26.databinding.FragmentWheelBinding
import com.example.mdpg26.ui.arena.ArenaFragment
import com.example.mdpg26.ui.arena.ArenaView
import com.example.mdpg26.ui.devicepicker.DeviceListBottomSheet
import com.example.mdpg26.ui.terminal.MessageAdapter
import com.example.mdpg26.viewmodel.ArenaViewModel
import com.example.mdpg26.viewmodel.BluetoothViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * "Wheel" tab: an F1-wheel-styled cockpit combining the exploration arena (top — the actual
 * [com.example.mdpg26.ui.arena.ArenaFragment] embedded as a child fragment, so it's the exact
 * same tool/gesture logic as the Arena tab, not a re-implementation), an embedded terminal (the
 * dashboard "screen"), and movement/mission controls (bottom), so the whole run can be driven
 * from one screen instead of switching between Terminal/Control/Arena. Command strings and
 * confirm-dialog behavior mirror [com.example.mdpg26.ui.control.ControlFragment] exactly; only
 * the button layout differs.
 */
class WheelFragment : Fragment() {

    private var _binding: FragmentWheelBinding? = null
    private val binding get() = _binding!!

    private val bluetoothViewModel: BluetoothViewModel by activityViewModels()
    private val arenaViewModel: ArenaViewModel by activityViewModels()
    private val adapter = MessageAdapter()
    private var arenaFragment: ArenaFragment? = null

    private val movementButtons: List<MaterialButton> by lazy {
        listOf(
            binding.btnForward, binding.btnReverse,
            binding.btnTurnLeft, binding.btnTurnRight,
            binding.btnRotateLeft, binding.btnRotateRight
        )
    }
    private val missionButtons: List<MaterialButton> by lazy {
        listOf(binding.btnBeginExplore, binding.btnBeginFastest, binding.btnSendArena)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The embedded ArenaFragment is auto-instantiated by FragmentContainerView's
        // android:name from XML, which gives no chance to pass arguments beforehand — so this
        // factory injects "hide your own tool controls" into its arguments right at creation,
        // before its onCreateView/onViewCreated ever run. Avoids any race between this fragment
        // hiding those controls after the fact and them briefly flashing on screen.
        childFragmentManager.fragmentFactory = object : FragmentFactory() {
            override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
                val fragment = super.instantiate(classLoader, className)
                if (fragment is ArenaFragment) {
                    fragment.arguments = Bundle().apply {
                        putBoolean(ArenaFragment.ARG_SHOW_TOOL_CONTROLS, false)
                    }
                }
                return fragment
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWheelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerMessages.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.recyclerMessages.adapter = adapter

        // Only needed now to drive setTool() from the toggle group below — visibility is already
        // handled by the FragmentFactory in onCreate.
        arenaFragment = childFragmentManager.findFragmentById(R.id.arenaFragmentContainer) as? ArenaFragment

        // Read the group's own checkedButtonId rather than reacting to each button's individual
        // checked/unchecked event, same reasoning as ArenaFragment's own listener.
        binding.toolToggleGroup.addOnButtonCheckedListener { group, _, _ ->
            val tool = when (group.checkedButtonId) {
                binding.btnToolPlace.id -> ArenaView.Tool.PLACE_OBSTACLE
                binding.btnToolRemove.id -> ArenaView.Tool.REMOVE_OBSTACLE
                binding.btnToolRobot.id -> ArenaView.Tool.PLACE_ROBOT
                else -> ArenaView.Tool.NONE
            }
            arenaFragment?.setTool(tool)
        }

        binding.btnForward.setOnClickListener {
            sendCommand(RobotCommands.FORWARD, getString(R.string.cmd_forward))
        }
        binding.btnReverse.setOnClickListener {
            sendCommand(RobotCommands.REVERSE, getString(R.string.cmd_reverse))
        }
        binding.btnTurnLeft.setOnClickListener {
            sendCommand(RobotCommands.STRAFE_LEFT, getString(R.string.cmd_strafe_left))
        }
        binding.btnTurnRight.setOnClickListener {
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
            ArenaProtocol.arenaSnapshot(arenaViewModel.state.value).forEach(bluetoothViewModel::sendMessage)
        }

        binding.btnBluetooth.setOnClickListener {
            when (bluetoothViewModel.connectionState.value) {
                is ConnectionUiState.Connected, is ConnectionUiState.Reconnecting -> showConnectionMenu()
                is ConnectionUiState.Disconnected -> openDevicePicker()
                is ConnectionUiState.Connecting -> Unit
            }
        }

        binding.btnSend.setOnClickListener { sendCurrentInput() }
        binding.editMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentInput()
                true
            } else {
                false
            }
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
        bluetoothViewModel.sendMessage(command)
    }

    private fun sendCurrentInput() {
        val text = binding.editMessage.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        bluetoothViewModel.sendMessage(text)
        binding.editMessage.text?.clear()
    }

    private fun openDevicePicker() {
        DeviceListBottomSheet().show(parentFragmentManager, DeviceListBottomSheet.TAG)
    }

    private fun showConnectionMenu() {
        val popup = PopupMenu(requireContext(), binding.btnBluetooth)
        popup.menuInflater.inflate(R.menu.connection_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_disconnect -> {
                    bluetoothViewModel.disconnect()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    bluetoothViewModel.connectionState.collect { state ->
                        val connected = state is ConnectionUiState.Connected
                        setControlsEnabled(connected)
                        binding.btnBluetooth.iconTint = ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), connectionDotColorRes(state))
                        )
                    }
                }
                launch {
                    bluetoothViewModel.messages.collect { messages ->
                        adapter.submitList(messages)
                        binding.textScreenEmpty.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                        if (messages.isNotEmpty()) {
                            binding.recyclerMessages.scrollToPosition(messages.size - 1)
                        }
                    }
                }
                launch {
                    bluetoothViewModel.errors.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun connectionDotColorRes(state: ConnectionUiState): Int = when (state) {
        is ConnectionUiState.Connected -> R.color.status_connected
        is ConnectionUiState.Connecting, is ConnectionUiState.Reconnecting -> R.color.status_connecting
        is ConnectionUiState.Disconnected -> R.color.status_disconnected
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
        arenaFragment = null
        _binding = null
    }
}
