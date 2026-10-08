package com.qtekfun.ultimatephone.core.spam.sources

import android.database.sqlite.SQLiteDatabase
import com.qtekfun.ultimatephone.core.spam.decision.BusinessLookup
import com.qtekfun.ultimatephone.core.spam.decision.BusinessMatch
import com.qtekfun.ultimatephone.core.spam.decision.SourceLookup
import com.qtekfun.ultimatephone.core.spam.decision.SourceMatch
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRule
import java.io.Closeable
import java.io.File

/** One row of a pack's `numbers` table. Packs fill the columns that make sense for their type. */
data class PackEntry(val e164: String, val name: String? = null, val category: String? = null, val label: String? = null)

/**
 * A read-only pack: `numbers(e164 PRIMARY KEY, name, category, label, ...)` for exact lookup and an optional
 * `prefix_rules(id, country, prefix, level, informational, source)` table. Installing or updating a pack is replacing
 * its file, so a [PackDatabase] is closed and reopened rather than modified.
 */
interface PackDatabase : Closeable {
    /** Exact lookup by primary key; no scans. */
    fun lookup(e164: String): PackEntry?

    /** All prefix rules, loaded once. Empty when the pack has none. */
    fun prefixRules(): List<PrefixRule>
}

/**
 * SQLite pack opened read-only with the framework driver. Columns are read by name, so a pack may carry extra
 * columns, and missing optional ones read as null. Not exercised by JVM unit tests (needs Android).
 */
class SqlitePackDatabase private constructor(private val db: SQLiteDatabase) : PackDatabase {
    override fun lookup(e164: String): PackEntry? = db.rawQuery("SELECT * FROM numbers WHERE e164 = ? LIMIT 1", arrayOf(e164)).use { c ->
        if (!c.moveToFirst()) return null
        fun str(column: String): String? = c.getColumnIndex(column).takeIf { it >= 0 }?.let { c.getString(it) }
        PackEntry(e164, str("name"), str("category"), str("label"))
    }

    override fun prefixRules(): List<PrefixRule> {
        if (!hasTable("prefix_rules")) return emptyList()
        return db.rawQuery("SELECT * FROM prefix_rules", null).use { c ->
            fun str(column: String): String? = c.getColumnIndex(column).takeIf { it >= 0 }?.let { c.getString(it) }
            val rules = ArrayList<PrefixRule>()
            while (c.moveToNext()) {
                val id = str("id")
                val prefix = str("prefix")
                val level = str("level")?.let { n -> SpamLevel.entries.firstOrNull { it.name.equals(n, true) } } ?: SpamLevel.RULE
                if (id.isNullOrBlank() || prefix.isNullOrBlank()) continue
                val informational = c.getColumnIndex("informational").takeIf { it >= 0 }?.let { c.getInt(it) != 0 } ?: false
                rules += PrefixRule(id, str("country")?.uppercase(), prefix, level, informational, str("source"))
            }
            rules
        }
    }

    override fun close() = db.close()

    private fun hasTable(name: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use { it.moveToFirst() }

    companion object {
        /** Opens [file] read-only; null when it is missing or not a usable SQLite file. */
        fun open(file: File): SqlitePackDatabase? {
            if (!file.isFile) return null
            return try {
                SqlitePackDatabase(SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY))
            } catch (_: android.database.sqlite.SQLiteException) {
                null
            }
        }
    }
}

/** Spam source backed by one pack: a hit is [level] unless the row says otherwise. */
class PackSourceLookup(private val sourceId: String, private val pack: PackDatabase, private val level: SpamLevel = SpamLevel.COMMUNITY) : SourceLookup {
    override fun find(e164: String): SourceMatch? = pack.lookup(e164)?.let { SourceMatch(sourceId, level, it.label ?: it.name) }
}

/** Business identification backed by one or more packs, first hit wins. */
class PackBusinessLookup(private val packs: List<Pair<String, PackDatabase>>) : BusinessLookup {
    override fun find(e164: String): BusinessMatch? = packs.firstNotNullOfOrNull { (packId, pack) ->
        pack.lookup(e164)?.let { entry -> entry.name?.let { BusinessMatch(it, entry.category, packId) } }
    }
}

/** Several spam sources checked in order; the first hit wins. */
class CompositeSourceLookup(private val lookups: List<SourceLookup>) : SourceLookup {
    override fun find(e164: String): SourceMatch? = lookups.firstNotNullOfOrNull { it.find(e164) }
}
