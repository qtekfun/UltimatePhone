package com.qtekfun.ultimatephone.feature.contacts

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qtekfun.ultimatephone.core.contacts.ContactsManager
import com.qtekfun.ultimatephone.core.contacts.VCardParseResult
import com.qtekfun.ultimatephone.core.contacts.VCardParser
import com.qtekfun.ultimatephone.core.contacts.VCardWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.ignoredDuplicatesStore: DataStore<Preferences> by preferencesDataStore(name = "ignored_duplicates")

/**
 * Pairs of contacts the user marked "not duplicates", so they are not suggested again. Kept in the `ignored_duplicates`
 * DataStore file as unordered lookup-key pairs (see `DuplicatePairs`); no names or numbers are stored.
 */
@Singleton
class IgnoredDuplicates @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.applicationContext.ignoredDuplicatesStore
    private val key = stringSetPreferencesKey("pairs")

    val pairs: Flow<Set<String>> = store.data.map { it[key].orEmpty() }

    suspend fun ignore(newPairs: Set<String>) {
        if (newPairs.isEmpty()) return
        store.edit { it[key] = it[key].orEmpty() + newPairs }
    }
}

/** Reads and writes `.vcf` files chosen with the Storage Access Framework. */
@Singleton
class VCardTransfer @Inject constructor(@ApplicationContext context: Context, private val contacts: ContactsManager) {
    private val resolver = context.applicationContext.contentResolver

    /** Writes the contacts ([lookupKeys] null means all) to [uri]; returns how many, or -1 when the file could not be written. */
    suspend fun export(lookupKeys: List<String>?, uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val stream = resolver.openOutputStream(uri, "wt") ?: return@withContext -1
            OutputStreamWriter(stream, Charsets.UTF_8).buffered().use { writer ->
                contacts.readForExport(lookupKeys) { VCardWriter.write(it, writer) }
            }
        } catch (_: IOException) {
            -1
        } catch (_: SecurityException) {
            -1
        }
    }

    /** Reads the cards of [uri]; null when the file cannot be read or is too big to hold in memory. */
    suspend fun read(uri: Uri): VCardParseResult? = withContext(Dispatchers.IO) {
        try {
            val bytes = resolver.openInputStream(uri)?.use { stream ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_FILE_BYTES) return@withContext null
                }
                out.toByteArray()
            } ?: return@withContext null
            VCardParser.parse(bytes)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val MAX_FILE_BYTES = 128 * 1024 * 1024
    }
}

/** Suggested file name for an export. */
fun exportFileName(single: Boolean): String = if (single) "contact.vcf" else "contacts.vcf"

/** The MIME type exports are created with. */
const val VCARD_MIME = "text/x-vcard"

/** What the file picker accepts for an import: vCard types, and generic ones because many file managers label `.vcf` loosely. */
val VCARD_PICKER_TYPES = arrayOf("text/x-vcard", "text/vcard", "text/directory", "text/*", "application/octet-stream")
