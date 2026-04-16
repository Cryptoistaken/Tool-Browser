package com.personal.browser.data.model

import java.util.UUID

data class Tab(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Tab",
    var url: String = "",
    var faviconUrl: String? = null,
    var isLoading: Boolean = false,
    var progress: Int = 0,
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false
)
