package com.example.mdpg26.ui.devicepicker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mdpg26.R
import com.example.mdpg26.databinding.ItemDeviceActionBinding
import com.example.mdpg26.databinding.ItemDeviceBinding
import com.example.mdpg26.databinding.ItemDeviceEmptyBinding
import com.example.mdpg26.databinding.ItemDeviceHeaderBinding

class DeviceListAdapter(
    private val onDeviceClick: (DeviceListItem.Device) -> Unit,
    private val onActionClick: (DeviceListItem.Action) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<DeviceListItem> = emptyList()

    fun submitList(newItems: List<DeviceListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is DeviceListItem.Header -> TYPE_HEADER
        is DeviceListItem.Device -> TYPE_DEVICE
        is DeviceListItem.Empty -> TYPE_EMPTY
        is DeviceListItem.Action -> TYPE_ACTION
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(ItemDeviceHeaderBinding.inflate(inflater, parent, false))
            TYPE_DEVICE -> DeviceViewHolder(ItemDeviceBinding.inflate(inflater, parent, false))
            TYPE_EMPTY -> EmptyViewHolder(ItemDeviceEmptyBinding.inflate(inflater, parent, false))
            else -> ActionViewHolder(ItemDeviceActionBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DeviceListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is DeviceListItem.Device -> (holder as DeviceViewHolder).bind(item)
            is DeviceListItem.Empty -> (holder as EmptyViewHolder).bind(item)
            is DeviceListItem.Action -> (holder as ActionViewHolder).bind(item)
        }
    }

    private class HeaderViewHolder(private val binding: ItemDeviceHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DeviceListItem.Header) {
            binding.textHeader.text = item.title
            binding.headerProgress.visibility = if (item.showProgress) View.VISIBLE else View.GONE
        }
    }

    private inner class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DeviceListItem.Device) {
            val device = item.device
            binding.textDeviceName.text = device.name.ifBlank {
                binding.root.context.getString(R.string.unknown_device)
            }
            binding.textDeviceAddress.text = device.address
            binding.textPairedBadge.visibility = if (device.bonded) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onDeviceClick(item) }
        }
    }

    private class EmptyViewHolder(private val binding: ItemDeviceEmptyBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DeviceListItem.Empty) {
            binding.textEmpty.text = item.message
        }
    }

    private inner class ActionViewHolder(private val binding: ItemDeviceActionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DeviceListItem.Action) {
            binding.textActionLabel.text = item.label
            binding.root.setOnClickListener { onActionClick(item) }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_DEVICE = 1
        const val TYPE_EMPTY = 2
        const val TYPE_ACTION = 3
    }
}
