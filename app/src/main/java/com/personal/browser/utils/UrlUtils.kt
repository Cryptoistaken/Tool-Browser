package com.personal.browser.utils

import android.util.Patterns

object UrlUtils {
    private const val GOOGLE_SEARCH = "https://www.google.com/search?q="
    private const val DEFAULT_HOME = "about:blank"

    fun processInput(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return DEFAULT_HOME

        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("about:") -> trimmed
            trimmed.startsWith("file://") -> trimmed
            isValidUrl("https://$trimmed") -> "https://$trimmed"
            else -> GOOGLE_SEARCH + android.net.Uri.encode(trimmed)
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            Patterns.WEB_URL.matcher(url).matches() &&
                    !url.contains(" ") &&
                    url.contains(".")
        } catch (e: Exception) {
            false
        }
    }

    fun getDisplayUrl(url: String): String {
        return url
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .let { if (it.endsWith("/")) it.dropLast(1) else it }
    }

    fun isSearch(url: String): Boolean =
        url.contains("google.com/search") || url.contains("google.com/search")
}
