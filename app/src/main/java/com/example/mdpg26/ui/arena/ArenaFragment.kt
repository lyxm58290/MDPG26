package com.example.mdpg26.ui.arena

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.mdpg26.R
import com.example.mdpg26.arena.ArenaProtocol
import com.example.mdpg26.arena.Facing
import com.example.mdpg26.bluetooth.ConnectionUiState
import com.example.mdpg26.databinding.DialogTargetFaceBinding
import com.example.mdpg26.databinding.FragmentArenaBinding
import com.example.mdpg26.viewmodel.ArenaViewModel
import com.example.mdpg26.viewmodel.BluetoothViewModel
import com.example.mdpg26.viewmodel.MoveOutcome
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * 2D exploration arena: displays obstacles + robot (C.5), interactive obstacle placement/
 * movement (C.6) and target-face annotation (C.7), plus local robot placement. [ArenaView]
 * handles rendering/gestures; [ArenaViewModel] owns the canonical state; this fragment is the
 * only place that knows about Bluetooth, translating accepted mutations into [ArenaProtocol]
 * messages.
 */
class ArenaFragment : Fragment() {

    private var _binding: FragmentArenaBinding? = null
    private val binding get() = _binding!!

    private val bluetoothViewModel: BluetoothViewModel by activityViewModels()
    private val arenaViewModel: ArenaViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArenaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Read the group's own checkedButtonId rather than reacting to each button's individual
        // checked/unchecked event: MaterialButtonToggleGroup fires those per-child in layout order
        // on every switch, so trusting a single event's isChecked/checkedId can catch a stale
        // uncheck from the previously-selected button firing after the new one's check event.
        binding.toolToggleGroup.addOnButtonCheckedListener { group, _, _ ->
            binding.arenaView.tool = when (group.checkedButtonId) {
                binding.btnToolPlace.id -> ArenaView.Tool.PLACE_OBSTACLE
                binding.btnToolRemove.id -> ArenaView.Tool.REMOVE_OBSTACLE
                binding.btnToolRobot.id -> ArenaView.Tool.PLACE_ROBOT
                else -> ArenaView.Tool.NONE
            }
        }

        binding.arenaView.onObstaclePlaceRequested = { x, y -> showFacePickerForNewObstacle(x, y) }
        binding.arenaView.onObstacleRemoveRequested = { id ->
            arenaViewModel.removeObstacle(id)?.let { removed ->
                bluetoothViewModel.sendMessage(ArenaProtocol.obstacleRemoved(removed))
            }
        }
        binding.arenaView.onObstacleMoveRequested = { id, newX, newY ->
            when (val outcome = arenaViewModel.moveObstacle(id, newX, newY)) {
                is MoveOutcome.Moved -> bluetoothViewModel.sendMessage(ArenaProtocol.obstaclePlaced(outcome.obstacle))
                is MoveOutcome.Removed -> bluetoothViewModel.sendMessage(ArenaProtocol.obstacleRemoved(outcome.obstacle))
                is MoveOutcome.Rejected -> Unit
            }
        }
        binding.arenaView.onObstacleTapRequested = { id -> showFacePickerForExistingObstacle(id) }
        binding.arenaView.onRobotPlaceRequested = { x, y -> arenaViewModel.moveRobot(x, y) }

        binding.btnRotateRobotLeft.setOnClickListener { arenaViewModel.rotateRobot(clockwise = false) }
        binding.btnRotateRobotRight.setOnClickListener { arenaViewModel.rotateRobot(clockwise = true) }

        observeState()
    }

    private fun showFacePickerForNewObstacle(x: Int, y: Int) {
        showFacePickerDialog(
            subtitle = getString(R.string.dialog_face_subtitle_new_fmt, x, y)
        ) { face ->
            arenaViewModel.addObstacle(x, y, face)?.let { obstacle ->
                bluetoothViewModel.sendMessage(ArenaProtocol.obstaclePlaced(obstacle))
            }
        }
    }

    private fun showFacePickerForExistingObstacle(id: Int) {
        showFacePickerDialog(
            subtitle = getString(R.string.dialog_face_subtitle_edit_fmt, id)
        ) { face ->
            arenaViewModel.setObstacleFace(id, face)?.let { updated ->
                bluetoothViewModel.sendMessage(ArenaProtocol.obstaclePlaced(updated))
            }
        }
    }

    private fun showFacePickerDialog(subtitle: String, onChosen: (Facing) -> Unit) {
        val dialogBinding = DialogTargetFaceBinding.inflate(layoutInflater)
        dialogBinding.textDialogSubtitle.text = subtitle

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_face_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        val pick: (Facing) -> Unit = { face ->
            onChosen(face)
            dialog.dismiss()
        }
        dialogBinding.btnFaceNorth.setOnClickListener { pick(Facing.NORTH) }
        dialogBinding.btnFaceEast.setOnClickListener { pick(Facing.EAST) }
        dialogBinding.btnFaceSouth.setOnClickListener { pick(Facing.SOUTH) }
        dialogBinding.btnFaceWest.setOnClickListener { pick(Facing.WEST) }

        dialog.show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    arenaViewModel.state.collect { state ->
                        binding.arenaView.setState(state)
                        binding.textRobotStatus.text = getString(
                            R.string.arena_robot_status_fmt,
                            state.robot.x, state.robot.y, state.robot.facing.name
                        )
                    }
                }
                launch {
                    bluetoothViewModel.connectionState.collect { state ->
                        val connected = state is ConnectionUiState.Connected
                        binding.bannerNotConnected.visibility = if (connected) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    bluetoothViewModel.targetDetections.collect { detection ->
                        arenaViewModel.setObstacleTarget(detection.obstacleId, detection.targetId)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.arenaView.onObstaclePlaceRequested = null
        binding.arenaView.onObstacleRemoveRequested = null
        binding.arenaView.onObstacleMoveRequested = null
        binding.arenaView.onObstacleTapRequested = null
        binding.arenaView.onRobotPlaceRequested = null
        super.onDestroyView()
        _binding = null
    }
}
