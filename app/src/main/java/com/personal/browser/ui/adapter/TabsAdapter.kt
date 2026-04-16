package com.personal.browser.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.personal.browser.data.model.Tab
import com.personal.browser.databinding.ItemTabBinding

class TabsAdapter(
    private val onTabClick: (Int) -> Unit,
    private val onTabClose: (Int) -> Unit
) : ListAdapter<Tab, TabsAdapter.TabViewHolder>(TabDiffCallback()) {

    inner class TabViewHolder(private val binding: ItemTabBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(tab: Tab, position: Int) {
            binding.tabTitle.text = tab.title.ifEmpty { "New Tab" }
            binding.tabUrl.text = tab.url.ifEmpty { "about:blank" }

            binding.root.setOnClickListener { onTabClick(position) }
            binding.btnCloseTab.setOnClickListener { onTabClose(position) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    class TabDiffCallback : DiffUtil.ItemCallback<Tab>() {
        override fun areItemsTheSame(oldItem: Tab, newItem: Tab) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Tab, newItem: Tab) = oldItem == newItem
    }
}
