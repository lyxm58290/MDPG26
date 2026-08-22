package com.example.mdpg26.ui.terminal

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mdpg26.bluetooth.MessageDirection
import com.example.mdpg26.bluetooth.TerminalMessage
import com.example.mdpg26.databinding.ItemMessageReceivedBinding
import com.example.mdpg26.databinding.ItemMessageSentBinding
import com.example.mdpg26.databinding.ItemMessageSystemBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<TerminalMessage> = emptyList()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /** Appends efficiently when possible, otherwise falls back to a full refresh. */
    fun submitList(newItems: List<TerminalMessage>) {
        val old = items
        items = newItems
        when {
            newItems.size == old.size + 1 && newItems.dropLast(1) == old -> {
                notifyItemInserted(old.size)
            }
            newItems.isEmpty() && old.isNotEmpty() -> notifyDataSetChanged()
            newItems != old -> notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position].direction) {
        MessageDirection.SENT -> TYPE_SENT
        MessageDirection.RECEIVED -> TYPE_RECEIVED
        MessageDirection.SYSTEM -> TYPE_SYSTEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SENT -> SentViewHolder(ItemMessageSentBinding.inflate(inflater, parent, false))
            TYPE_RECEIVED -> ReceivedViewHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false))
            else -> SystemViewHolder(ItemMessageSystemBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = items[position]
        when (holder) {
            is SentViewHolder -> holder.bind(message)
            is ReceivedViewHolder -> holder.bind(message)
            is SystemViewHolder -> holder.bind(message)
        }
    }

    private inner class SentViewHolder(private val binding: ItemMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: TerminalMessage) {
            binding.textMessage.text = message.text
            binding.textTimestamp.text = timeFormat.format(Date(message.timestamp))
        }
    }

    private inner class ReceivedViewHolder(private val binding: ItemMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: TerminalMessage) {
            binding.textMessage.text = message.text
            binding.textTimestamp.text = timeFormat.format(Date(message.timestamp))
        }
    }

    private inner class SystemViewHolder(private val binding: ItemMessageSystemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: TerminalMessage) {
            binding.textMessage.text = message.text
        }
    }

    private companion object {
        const val TYPE_SENT = 0
        const val TYPE_RECEIVED = 1
        const val TYPE_SYSTEM = 2
    }
}
