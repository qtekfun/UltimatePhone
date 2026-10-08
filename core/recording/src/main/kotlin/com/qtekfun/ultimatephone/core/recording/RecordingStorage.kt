package com.qtekfun.ultimatephone.core.recording

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract

/** A recording found in the chosen folder. */
data class StoredRecording(val uri: Uri, val name: String, val sizeBytes: Long, val lastModifiedMillis: Long)

/**
 * The recordings folder, chosen by the user with the system folder picker (Storage Access Framework). All access goes
 * through [DocumentsContract] and the content resolver, so it works with any documents provider (local storage, SD card,
 * Nextcloud's provider...). Every call catches the failures a provider can throw and reports them as null or false.
 */
class RecordingStorage(private val context: Context) {
    private val resolver get() = context.contentResolver

    private fun treeRoot(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    /** Keeps read and write access to [tree] across restarts. False if the system refuses. */
    fun persist(tree: Uri): Boolean = runCatching {
        // Providers throw SecurityException, IllegalArgumentException and others.
        resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }.isSuccess

    /** True while the app still holds write permission for [tree] and the folder exists. */
    fun isUsable(tree: Uri): Boolean {
        val granted = resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        return granted && runCatching {
            resolver.query(treeRoot(tree), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)
    }

    /** Name of the folder for display, or null. */
    fun folderName(tree: Uri): String? = runCatching {
        resolver.query(treeRoot(tree), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

    /**
     * Creates an empty file called [name] in [tree]. The generic type makes providers keep the name as given instead of
     * adding an extension of their own; the file is still opened by players by content.
     */
    fun create(tree: Uri, name: String): Uri? = runCatching { DocumentsContract.createDocument(resolver, treeRoot(tree), GENERIC_MIME, name) }.getOrNull()

    /** Opens [uri] for reading and writing (the MPEG-4 muxer needs both). */
    fun openForRecording(uri: Uri): ParcelFileDescriptor? = runCatching { resolver.openFileDescriptor(uri, "rw") }.getOrNull()

    fun delete(uri: Uri): Boolean = runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false)

    /** The new URI, or null when the provider refused. */
    fun rename(uri: Uri, newName: String): Uri? = runCatching { DocumentsContract.renameDocument(resolver, uri, newName) }.getOrNull()

    /** Audio files (m4a, ogg) directly inside [tree], newest first. */
    fun list(tree: Uri): List<StoredRecording> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val found = ArrayList<StoredRecording>()
        val listed = runCatching {
            resolver.query(children, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1).orEmpty()
                    val isFolder = cursor.getString(4) == DocumentsContract.Document.MIME_TYPE_DIR
                    if (!isFolder && isRecordingName(name)) {
                        found += StoredRecording(
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)),
                            name = name,
                            sizeBytes = if (cursor.isNull(2)) 0 else cursor.getLong(2),
                            lastModifiedMillis = if (cursor.isNull(3)) 0 else cursor.getLong(3)
                        )
                    }
                }
            }
        }.isSuccess
        if (!listed) return emptyList()
        return found.sortedByDescending { it.lastModifiedMillis }
    }

    companion object {
        private const val GENERIC_MIME = "application/octet-stream"

        fun isRecordingName(name: String): Boolean = name.lowercase().let { it.endsWith(".m4a") || it.endsWith(".ogg") }

        /** MIME type to give another app when sharing [name]. */
        fun mimeOf(name: String): String = if (name.lowercase().endsWith(".ogg")) RecordingFormat.OGG_OPUS.mimeType else RecordingFormat.M4A_AAC.mimeType
    }
}
