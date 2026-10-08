package com.qtekfun.ultimatephone.data

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.qtekfun.ultimatephone.core.contacts.T9
import java.io.Closeable
import java.io.File

/** A business found by name. */
data class BusinessHit(val e164: String, val name: String, val category: String?, val packId: String)

/** Search of business names in the installed packs, for the dialer. Never touches the network. */
interface BusinessNameSearch {
    /** @param digits what is typed on the keypad; matched as T9 against the start of each word of the name. */
    suspend fun searchT9(digits: String, limit: Int): List<BusinessHit>
}

object NoBusinessNameSearch : BusinessNameSearch {
    override suspend fun searchT9(digits: String, limit: Int): List<BusinessHit> = emptyList()
}

/**
 * Query building for the `numbers_fts` table of business packs (FTS5 over `name`). The keypad gives digits, FTS wants
 * letters, so the first [MAX_EXPANDED_DIGITS] digits are expanded into every letter combination (at most 256 prefix
 * terms) and the rows that come back are filtered again with the full T9 code of the query.
 */
object BusinessQuery {
    /** Fewer digits would match nearly every shop. */
    const val MIN_DIGITS = 3
    const val MAX_EXPANDED_DIGITS = 4

    private val LETTERS = mapOf(
        '0' to "0",
        '1' to "1",
        '2' to "abc",
        '3' to "def",
        '4' to "ghi",
        '5' to "jkl",
        '6' to "mno",
        '7' to "pqrs",
        '8' to "tuv",
        '9' to "wxyz"
    )

    /** The FTS5 MATCH expression for [digits], or null when the query is too short or has non-digit keys. */
    fun matchForDigits(digits: String): String? {
        if (digits.length < MIN_DIGITS || !digits.all { it in '0'..'9' }) return null
        val window = digits.take(MAX_EXPANDED_DIGITS)
        var prefixes = listOf("")
        for (key in window) {
            val letters = LETTERS.getValue(key)
            prefixes = prefixes.flatMap { prefix -> letters.map { prefix + it } }
        }
        return prefixes.joinToString(" OR ") { "\"$it\"*" }
    }

    /** True when some word of [name] starts with the keypad code [digits]. */
    fun matchesT9(name: String, digits: String): Boolean = T9.words(name).any { T9.encode(it).startsWith(digits) }

    /** Rows whose first word matches come first, then shorter names. */
    fun rank(hits: List<BusinessHit>, digits: String): List<BusinessHit> = hits
        .filter { matchesT9(it.name, digits) }
        .sortedWith(compareBy({ if (T9.words(it.name).firstOrNull()?.let { w -> T9.encode(w).startsWith(digits) } == true) 0 else 1 }, { it.name.length }))
}

/** Drops business suggestions whose number belongs to a contact already listed: a contact is never replaced by a business. */
object BusinessSuggestions {
    private const val TAIL_DIGITS = 9

    fun withoutContacts(hits: List<BusinessHit>, contactNumbers: Collection<String>): List<BusinessHit> {
        val tails = contactNumbers.map { tail(it) }.filter { it.isNotEmpty() }.toSet()
        return hits.filterNot { tail(it.e164) in tails }
    }

    private fun tail(number: String) = T9.digitsOf(number).takeLast(TAIL_DIGITS)
}

/** FTS5 name search over the business packs, one read-only connection per pack. Needs Android's SQLite, so it is not unit tested. */
class SqliteBusinessNameSearch(private val files: List<Pair<String, File>>) :
    BusinessNameSearch,
    Closeable {
    private val databases: List<Pair<String, SQLiteDatabase>> by lazy {
        files.mapNotNull { (id, file) ->
            try {
                id to SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            } catch (_: SQLiteException) {
                null
            }
        }
    }

    override suspend fun searchT9(digits: String, limit: Int): List<BusinessHit> {
        val match = BusinessQuery.matchForDigits(digits) ?: return emptyList()
        val hits = databases.flatMap { (packId, db) -> query(packId, db, match, limit * OVERFETCH) }
        return BusinessQuery.rank(hits, digits).distinctBy { it.e164 }.take(limit)
    }

    private fun query(packId: String, db: SQLiteDatabase, match: String, limit: Int): List<BusinessHit> = try {
        db.rawQuery(
            "SELECT n.e164, n.name, n.category FROM numbers_fts JOIN numbers n ON n.rowid = numbers_fts.rowid WHERE numbers_fts MATCH ? LIMIT ?",
            arrayOf(match, limit.toString())
        ).use { c ->
            val out = ArrayList<BusinessHit>()
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                out += BusinessHit(c.getString(0), name, c.getString(2), packId)
            }
            out
        }
    } catch (_: SQLiteException) {
        // A pack without FTS5 or a platform SQLite built without it: no suggestions rather than a crash.
        emptyList()
    }

    override fun close() {
        if (files.isNotEmpty()) databases.forEach { it.second.close() }
    }

    private companion object {
        const val OVERFETCH = 6
    }
}
