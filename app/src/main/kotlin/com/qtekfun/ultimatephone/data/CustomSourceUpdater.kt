package com.qtekfun.ultimatephone.data

import com.qtekfun.ultimatephone.core.datapacks.Hashing
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.spam.sources.NumberSourceParser
import com.qtekfun.ultimatephone.core.spam.sources.SourceFormat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What a trial download of a source found, for the "test" button. */
sealed interface SourceTestResult {
    data class Ok(val accepted: Int, val invalid: Int, val duplicates: Int) : SourceTestResult

    data class Failed(val error: String) : SourceTestResult
}

/**
 * Turns a custom source into a local pack: conditional download (ETag / If-Modified-Since), content hash check, parsing
 * with [NumberSourceParser] and an atomic write of the pack file into [packsDir]. Nothing here throws: the outcome is
 * the new state of the source, with [CustomSource.lastError] set when something went wrong. A failed update never
 * touches the pack that is already installed.
 */
class CustomSourceUpdater(
    private val fetcher: SourceFetcher,
    private val normalizer: PhoneNormalizer,
    private val region: () -> String,
    private val packsDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val maxBytes: Long = MAX_SOURCE_BYTES,
    private val attempts: Int = ATTEMPTS
) {
    fun packFile(source: CustomSource): File = File(packsDir, "${source.packId}$EXTENSION")

    fun deletePack(source: CustomSource) {
        packFile(source).delete()
    }

    suspend fun update(source: CustomSource): CustomSource = withContext(io) {
        if (!SourceUrls.isValid(source.url)) return@withContext source.failed(UpdateErrors.INVALID_URL)
        packsDir.mkdirs()
        val download = File.createTempFile("src-", ".part", packsDir)
        try {
            val target = packFile(source)
            when (val response = fetchWithRetry(source.url, source.etag.takeIf { target.exists() }, source.lastModified.takeIf { target.exists() }, download)) {
                is Attempt.Failed -> source.failed(response.error)
                is Attempt.Done -> when (val result = response.response) {
                    SourceResponse.NotModified -> source.copy(lastUpdateMillis = clock(), lastError = null)
                    is SourceResponse.Downloaded -> build(source, result, download, target)
                }
            }
        } finally {
            download.delete()
        }
    }

    /** Downloads and parses [url] without keeping anything. */
    suspend fun test(url: String, format: SourceFormat): SourceTestResult = withContext(io) {
        if (!SourceUrls.isValid(url)) return@withContext SourceTestResult.Failed(UpdateErrors.INVALID_URL)
        packsDir.mkdirs()
        val download = File.createTempFile("test-", ".part", packsDir)
        try {
            when (val attempt = fetchWithRetry(url, null, null, download, tries = 1)) {
                is Attempt.Failed -> SourceTestResult.Failed(attempt.error)
                is Attempt.Done -> {
                    val stats = parse(format, download) { }
                    if (stats.accepted ==
                        0
                    ) {
                        SourceTestResult.Failed(UpdateErrors.NO_NUMBERS)
                    } else {
                        SourceTestResult.Ok(stats.accepted, stats.invalid, stats.duplicates)
                    }
                }
            }
        } finally {
            download.delete()
        }
    }

    private fun build(source: CustomSource, result: SourceResponse.Downloaded, download: File, target: File): CustomSource {
        val sha = Hashing.sha256Hex(download)
        // The server ignored our conditional request but the content is the same: keep the pack, remember the new validators.
        if (sha == source.contentSha256 && target.exists()) {
            return source.copy(etag = result.etag, lastModified = result.lastModified, lastUpdateMillis = clock(), lastError = null)
        }
        val writer = NumberPackFile.Writer()
        val stats = parse(source.format, download, writer::add)
        if (stats.accepted == 0) return source.failed(UpdateErrors.NO_NUMBERS)
        return try {
            val written = writer.writeTo(target)
            source.copy(
                etag = result.etag,
                lastModified = result.lastModified,
                contentSha256 = sha,
                lastUpdateMillis = clock(),
                lastError = null,
                entries = written
            )
        } catch (_: IOException) {
            source.failed(UpdateErrors.STORAGE)
        }
    }

    private fun parse(format: SourceFormat, file: File, onNumber: (String) -> Unit) = NumberSourceParser(normalizer, region()).let { parser ->
        file.reader().use { reader -> parser.parse(format, reader) { onNumber(it.e164) } }
    }

    private sealed interface Attempt {
        data class Done(val response: SourceResponse) : Attempt

        data class Failed(val error: String) : Attempt
    }

    private suspend fun fetchWithRetry(url: String, etag: String?, lastModified: String?, target: File, tries: Int = attempts): Attempt {
        var wait = FIRST_RETRY_MS
        var last = UpdateErrors.NETWORK
        repeat(tries) { attempt ->
            try {
                return Attempt.Done(fetcher.fetch(url, etag, lastModified, target, maxBytes))
            } catch (e: SourceHttpException) {
                last = UpdateErrors.http(e.status)
            } catch (_: SourceTooLargeException) {
                return Attempt.Failed(UpdateErrors.TOO_LARGE)
            } catch (_: IOException) {
                last = UpdateErrors.NETWORK
            }
            if (!UpdateErrors.isTransient(last)) return Attempt.Failed(last)
            if (attempt < tries - 1) {
                pause(wait)
                wait *= 2
            }
        }
        return Attempt.Failed(last)
    }

    private fun CustomSource.failed(error: String) = copy(lastError = error)

    companion object {
        const val EXTENSION = ".nums"
        const val MAX_SOURCE_BYTES = 64L * 1024 * 1024
        private const val ATTEMPTS = 3
        private const val FIRST_RETRY_MS = 2_000L
    }
}
