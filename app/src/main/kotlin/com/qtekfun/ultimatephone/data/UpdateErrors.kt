package com.qtekfun.ultimatephone.data

/**
 * Failure codes stored in the data settings. The UI turns them into text, so changing the wording never touches stored
 * data. A code may carry a detail after a colon (`http:404`, `install:CHECKSUM_MISMATCH`).
 */
object UpdateErrors {
    const val NETWORK = "network"
    const val SIGNATURE = "signature"
    const val MANIFEST = "manifest"
    const val INVALID_URL = "invalid_url"
    const val TOO_LARGE = "too_large"
    const val NO_NUMBERS = "no_numbers"
    const val WIFI_ONLY = "wifi_only"
    const val STORAGE = "storage"
    const val HTTP = "http"
    const val INSTALL = "install"
    private const val SERVER_ERROR_FROM = 500
    private const val TOO_MANY_REQUESTS = 429

    fun http(status: Int) = "$HTTP:$status"

    fun install(reason: String) = "$INSTALL:$reason"

    fun kind(code: String): String = code.substringBefore(':')

    fun detail(code: String): String? = code.substringAfter(':', "").takeIf { it.isNotEmpty() }

    /** True when trying again later may work: no network, a busy server or a download that was cut. */
    fun isTransient(code: String): Boolean = when (kind(code)) {
        NETWORK -> true
        HTTP -> detail(code)?.toIntOrNull()?.let { it >= SERVER_ERROR_FROM || it == TOO_MANY_REQUESTS } ?: false
        INSTALL -> detail(code) == "NETWORK"
        else -> false
    }
}
