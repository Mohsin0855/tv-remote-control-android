package com.example.tvremotetest.ui.brandlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tvremotetest.data.entity.BrandDevice
import com.example.tvremotetest.databinding.ItemBrandBinding

class BrandAdapter(
    private val onItemClick: (BrandDevice) -> Unit
) : ListAdapter<BrandDevice, BrandAdapter.BrandViewHolder>(BrandDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BrandViewHolder {
        val binding = ItemBrandBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BrandViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BrandViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BrandViewHolder(
        private val binding: ItemBrandBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(item: BrandDevice) {
            binding.tvBrandName.text = item.brandModel
            binding.tvDeviceCategory.text = item.deviceCategory
        }
    }

    class BrandDiffCallback : DiffUtil.ItemCallback<BrandDevice>() {
        override fun areItemsTheSame(oldItem: BrandDevice, newItem: BrandDevice): Boolean {
            return oldItem.brandModel == newItem.brandModel &&
                    oldItem.deviceCategory == newItem.deviceCategory
        }

        override fun areContentsTheSame(oldItem: BrandDevice, newItem: BrandDevice): Boolean {
            return oldItem == newItem
        }
    }
}
