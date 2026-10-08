package com.qtekfun.ultimatephone.data

import java.net.URI
import java.net.URISyntaxException

/** Validation of the URLs the user types for custom sources. Only HTTPS is ever fetched. */
object SourceUrls {
    fun isValid(url: String): Boolean {
        val text = url.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return false
        val uri = try {
            URI(text)
        } catch (_: URISyntaxException) {
            return false
        }
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }
}
