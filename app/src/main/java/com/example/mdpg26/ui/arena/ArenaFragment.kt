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
import com.example.mdpg26.bluetooth.ConnectionUiState
import com.example.mdpg26.databinding.FragmentArenaBinding
import com.example.mdpg26.viewmodel.ArenaViewModel
import com.example.mdpg26.viewmodel.BluetoothViewModel
import com.example.mdpg26.viewmodel.MoveOutcome
import kotlinx.coroutines.launch

/**
 * 2D exploration arena: displays obstacles + robot (C.5), interactive obstacle placement/
 * movement (C.6) and target-face annotation (C.7). [ArenaView] handles rendering/gestures;
 * [ArenaViewModel] owns the canonical state; this fragment is the only place that knows about
 * Bluetooth, translating accepted mutations into [ArenaProtocol] messages.
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

        binding.toolToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            binding.arenaView.tool = when {
                !isChecked -> ArenaView.Tool.NONE
                checkedId == binding.btnToolPlace.id -> ArenaView.Tool.PLACE_OBSTACLE
                checkedId == binding.btnToolRemove.id -> ArenaView.Tool.REMOVE_OBSTACLE
                else -> ArenaView.Tool.NONE
            }
        }

        binding.arenaView.onObstaclePlaceRequested = { x, y ->
            arenaViewModel.addObstacle(x, y)?.let { obstacle ->
                bluetoothViewModel.sendMessage(ArenaProtocol.obstaclePlaced(obstacle))
            }
        }
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
        binding.arenaView.onObstacleTapRequested = { id ->
            arenaViewModel.cycleTargetFace(id)?.let { updated ->
                bluetoothViewModel.sendMessage(ArenaProtocol.targetFaceSet(updated))
            }
        }

        observeState()
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
            }
        }
    }

    override fun onDestroyView() {
        binding.arenaView.onObstaclePlaceRequested = null
        binding.arenaView.onObstacleRemoveRequested = null
        binding.arenaView.onObstacleMoveRequested = null
        binding.arenaView.onObstacleTapRequested = null
        super.onDestroyView()
        _binding = null
    }
}
