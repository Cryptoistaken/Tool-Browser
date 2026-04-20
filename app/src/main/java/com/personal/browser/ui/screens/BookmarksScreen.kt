package com.personal.browser.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.browser.data.model.Bookmark
import com.personal.browser.data.model.HistoryEntry

@Composable
fun BookmarksSheet(
    bookmarks: List<Bookmark>,
    onItemClick: (String) -> Unit,
    onItemDelete: (Bookmark) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Bookmarks",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No bookmarks yet", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn {
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkHistoryRow(
                        title = bookmark.title.ifEmpty { bookmark.url },
                        subtitle = bookmark.url,
                        onClick = { onItemClick(bookmark.url) },
                        onDelete = { onItemDelete(bookmark) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistorySheet(
    history: List<HistoryEntry>,
    onItemClick: (String) -> Unit,
    onItemDelete: (HistoryEntry) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "History",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No history yet", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn {
                items(history, key = { it.id }) { entry ->
                    BookmarkHistoryRow(
                        title = entry.title.ifEmpty { entry.url },
                        subtitle = entry.url,
                        onClick = { onItemClick(entry.url) },
                        onDelete = { onItemDelete(entry) }
                    )
                }
            }
        }
    }
}

@Composable
fun BookmarkHistoryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}
