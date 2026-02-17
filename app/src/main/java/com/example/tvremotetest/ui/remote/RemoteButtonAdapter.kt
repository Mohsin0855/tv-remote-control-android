package com.example.tvremotetest.ui.remote

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tvremotetest.data.entity.RemoteButton
import com.example.tvremotetest.databinding.ItemRemoteButtonBinding

class RemoteButtonAdapter(
    private val onButtonClick: (RemoteButton) -> Unit
) : ListAdapter<RemoteButton, RemoteButtonAdapter.ButtonViewHolder>(ButtonDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
        val binding = ItemRemoteButtonBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ButtonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ButtonViewHolder(
        private val binding: ItemRemoteButtonBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onButtonClick(getItem(position))
                }
            }
        }

        fun bind(item: RemoteButton) {
            binding.tvButtonName.text = formatButtonName(item.buttonName ?: "")
        }

        private fun formatButtonName(name: String): String {
            return name.replace("_", " ")
        }
    }

    class ButtonDiffCallback : DiffUtil.ItemCallback<RemoteButton>() {
        override fun areItemsTheSame(oldItem: RemoteButton, newItem: RemoteButton): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RemoteButton, newItem: RemoteButton): Boolean {
            return oldItem == newItem
        }
    }
}
