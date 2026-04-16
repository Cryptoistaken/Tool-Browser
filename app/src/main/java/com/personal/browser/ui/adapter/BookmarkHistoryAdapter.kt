package com.personal.browser.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.personal.browser.data.model.Bookmark
import com.personal.browser.data.model.HistoryEntry
import com.personal.browser.databinding.ItemBookmarkHistoryBinding

sealed class BookmarkHistoryItem {
    data class BookmarkItem(val bookmark: Bookmark) : BookmarkHistoryItem()
    data class HistoryItem(val entry: HistoryEntry) : BookmarkHistoryItem()
}

class BookmarkHistoryAdapter(
    private val onItemClick: (String) -> Unit,
    private val onItemDelete: (BookmarkHistoryItem) -> Unit
) : ListAdapter<BookmarkHistoryItem, BookmarkHistoryAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val binding: ItemBookmarkHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BookmarkHistoryItem) {
            when (item) {
                is BookmarkHistoryItem.BookmarkItem -> {
                    binding.itemTitle.text = item.bookmark.title
                    binding.itemUrl.text = item.bookmark.url
                    binding.root.setOnClickListener { onItemClick(item.bookmark.url) }
                }
                is BookmarkHistoryItem.HistoryItem -> {
                    binding.itemTitle.text = item.entry.title
                    binding.itemUrl.text = item.entry.url
                    binding.root.setOnClickListener { onItemClick(item.entry.url) }
                }
            }
        }
    }

    fun submitBookmarks(bookmarks: List<Bookmark>) {
        submitList(bookmarks.map { BookmarkHistoryItem.BookmarkItem(it) })
    }

    fun submitHistory(history: List<HistoryEntry>) {
        submitList(history.map { BookmarkHistoryItem.HistoryItem(it) })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBookmarkHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<BookmarkHistoryItem>() {
        override fun areItemsTheSame(oldItem: BookmarkHistoryItem, newItem: BookmarkHistoryItem): Boolean {
            return when {
                oldItem is BookmarkHistoryItem.BookmarkItem && newItem is BookmarkHistoryItem.BookmarkItem ->
                    oldItem.bookmark.id == newItem.bookmark.id
                oldItem is BookmarkHistoryItem.HistoryItem && newItem is BookmarkHistoryItem.HistoryItem ->
                    oldItem.entry.id == newItem.entry.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: BookmarkHistoryItem, newItem: BookmarkHistoryItem) =
            oldItem == newItem
    }
}
