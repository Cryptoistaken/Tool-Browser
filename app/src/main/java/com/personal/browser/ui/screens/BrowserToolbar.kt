package com.personal.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrowserToolbar(
    url: String,
    title: String,
    isLoading: Boolean,
    progress: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isBookmarked: Boolean,
    tabCount: Int,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onUrlSubmit: (String) -> Unit,
    onBookmarkToggle: () -> Unit,
    onTabsClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewTab: () -> Unit,
) {
    var urlText by remember(url) { mutableStateOf(url) }
    var isFocused by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Column {
        Surface(tonalElevation = 3.dp) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack, enabled = canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(onClick = onNavigateForward, enabled = canGoForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }

                    TextField(
                        value = if (isFocused) urlText else (if (url.isEmpty()) "" else url),
                        onValueChange = { urlText = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .onFocusChanged { state ->
                                isFocused = state.isFocused
                                if (state.isFocused) urlText = url
                            },
                        placeholder = {
                            Text(
                                text = if (title.isNotEmpty() && url.isNotEmpty()) title else "Search or enter URL",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            onUrlSubmit(urlText)
                            isFocused = false
                        }),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )

                    IconButton(onClick = if (isLoading) onStop else onRefresh) {
                        Icon(
                            if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                            contentDescription = if (isLoading) "Stop" else "Refresh"
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Tab") },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = { onNewTab(); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Tabs") },
                                leadingIcon = {
                                    BadgedBox(badge = {
                                        if (tabCount > 1) Badge { Text("$tabCount") }
                                    }) { Icon(Icons.Default.Tab, null) }
                                },
                                onClick = { onTabsClick(); showMenu = false }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (isBookmarked) "Remove Bookmark" else "Bookmark") },
                                leadingIcon = {
                                    Icon(
                                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        null
                                    )
                                },
                                onClick = { onBookmarkToggle(); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Bookmarks") },
                                leadingIcon = { Icon(Icons.Default.CollectionsBookmark, null) },
                                onClick = { onBookmarksClick(); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("History") },
                                leadingIcon = { Icon(Icons.Default.History, null) },
                                onClick = { onHistoryClick(); showMenu = false }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { onSettingsClick(); showMenu = false }
                            )
                        }
                    }
                }

                if (isLoading && progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp)
                    )
                }
            }
        }
    }
}
