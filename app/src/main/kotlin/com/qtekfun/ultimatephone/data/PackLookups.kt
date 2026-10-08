package com.qtekfun.ultimatephone.data

import com.qtekfun.ultimatephone.core.datapacks.PackStore
import com.qtekfun.ultimatephone.core.spam.decision.BusinessLookup
import com.qtekfun.ultimatephone.core.spam.decision.PrefixRules
import com.qtekfun.ultimatephone.core.spam.decision.SourceLookup
import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel
import com.qtekfun.ultimatephone.core.spam.prefix.BuiltInRules
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRule
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRuleSet
import com.qtekfun.ultimatephone.core.spam.prefix.PrefixRulesParser
import com.qtekfun.ultimatephone.core.spam.sources.CompositeSourceLookup
import com.qtekfun.ultimatephone.core.spam.sources.PackBusinessLookup
import com.qtekfun.ultimatephone.core.spam.sources.PackDatabase
import com.qtekfun.ultimatephone.core.spam.sources.PackSourceLookup
import com.qtekfun.ultimatephone.core.spam.sources.SqlitePackDatabase
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Opens an installed pack file for reading; null when it is damaged. Behind an interface so the reload logic can be tested. */
fun interface PackOpener {
    fun open(file: File): PackDatabase?
}

object SqlitePackOpener : PackOpener {
    override fun open(file: File): PackDatabase? = SqlitePackDatabase.open(file)
}

/** Rebuilds the in-memory view of the installed packs. Called after a pack or a custom source changed. */
fun interface PackLookupsReloader {
    suspend fun reload()
}

/**
 * The real [InstalledPackLookups]. Everything a call needs is prepared in a snapshot: prefix rules in memory, business
 * and spam packs opened read-only and answered with one indexed query (or a binary search) per number. A reload builds a
 * complete new snapshot and swaps it in one step, so a call in progress sees either the old or the new data, never a mix.
 * Replaced databases are closed after a grace period, because a reader may still hold the old snapshot.
 */
class DefaultInstalledPackLookups(
    private val store: PackStore,
    private val packsDir: File,
    private val customSources: suspend () -> List<CustomSource>,
    private val opener: PackOpener = SqlitePackOpener,
    private val builtInRules: () -> List<PrefixRule> = ::loadBuiltInRules,
    private val searchFactory: (List<Pair<String, File>>) -> BusinessNameSearch = { SqliteBusinessNameSearch(it) },
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val closeDelayMillis: Long = CLOSE_DELAY_MS
) : InstalledPackLookups,
    PackLookupsReloader,
    BusinessNameSearch {
    private class Snapshot(
        val sources: SourceLookup,
        val businesses: BusinessLookup,
        val rules: PrefixRules,
        val databases: List<PackDatabase>,
        val nameSearch: BusinessNameSearch
    )

    private val reloadMutex = Mutex()
    private val changeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Volatile
    private var snapshot: Snapshot? = null

    override val sources: SourceLookup = SourceLookup { e164 -> guarded { current().sources.find(e164) } }
    override val businesses: BusinessLookup = BusinessLookup { e164 -> guarded { current().businesses.find(e164) } }
    override val prefixRules: PrefixRules = PrefixRules { e164, region -> guarded { current().rules.match(e164, region) } }
    override val changes: Flow<Unit> = changeEvents.asSharedFlow()

    /** Builds the first snapshot in the background so the first call after process start finds it ready. */
    fun warmUp() {
        scope.launch { reload() }
    }

    override suspend fun reload() {
        reloadMutex.withLock {
            val fresh = withContext(io) { build() }
            val old = snapshot
            snapshot = fresh
            changeEvents.tryEmit(Unit)
            if (old != null) scope.launch { closeLater(old) }
        }
    }

    override suspend fun searchT9(digits: String, limit: Int): List<BusinessHit> =
        withContext(io) { guarded { current().nameSearch.searchT9(digits, limit) } ?: emptyList() }

    /**
     * The snapshot. Normally it is already there (the app warms it up at start). If a call is faster than the warm-up,
     * the caller (a screening thread, never the main one) waits for the build that is running, because [reload] holds
     * the same mutex while it builds; only when nobody is building does it build one itself. Either way it is built once.
     */
    private fun current(): Snapshot = snapshot ?: runBlocking {
        reloadMutex.withLock { snapshot ?: withContext(io) { build() }.also { snapshot = it } }
    }

    private inline fun <T> guarded(block: () -> T): T? = try {
        block()
    } catch (_: IllegalStateException) {
        // A database closed under our feet: the caller treats it as "no match" and the next call sees the new snapshot.
        null
    } catch (_: IOException) {
        null
    }

    private suspend fun closeLater(old: Snapshot) {
        delay(closeDelayMillis)
        old.databases.forEach { runCatching { it.close() } }
        (old.nameSearch as? java.io.Closeable)?.let { runCatching { it.close() } }
    }

    private suspend fun build(): Snapshot {
        val installed = store.installed()
        val databases = ArrayList<PackDatabase>()
        val businessPacks = ArrayList<Pair<String, PackDatabase>>()
        val businessFiles = ArrayList<Pair<String, File>>()
        val spam = ArrayList<SourceLookup>()
        val rules = ArrayList<PrefixRule>()

        for (id in installed.keys.sorted()) {
            val file = store.fileFor(id)
            when {
                id.startsWith(BUSINESSES_PREFIX) -> opener.open(file)?.let {
                    databases += it
                    businessPacks += id to it
                    businessFiles += id to file
                }
                id.startsWith(SPAM_PREFIX) -> opener.open(file)?.let {
                    databases += it
                    spam += PackSourceLookup(id.removePrefix(SPAM_PREFIX), it, SpamLevel.COMMUNITY)
                    rules += runCatching { it.prefixRules() }.getOrDefault(emptyList())
                }
                id.startsWith(RULES_PREFIX) -> rules += readRules(file)
            }
        }
        for (source in customSources().filter { it.enabled }) {
            NumberPackFile.open(File(packsDir, "${source.packId}${CustomSourceUpdater.EXTENSION}"), source.name)?.let {
                databases += it
                spam += PackSourceLookup(source.packId, it, source.level)
            }
        }
        // Installed rules come first, so a newer rule beats the one compiled into the app when both cover a prefix.
        val ruleSet = PrefixRuleSet(rules + runCatching(builtInRules).getOrDefault(emptyList()))
        return Snapshot(
            sources = CompositeSourceLookup(spam),
            businesses = PackBusinessLookup(businessPacks),
            rules = ruleSet,
            databases = databases,
            nameSearch = searchFactory(businessFiles)
        )
    }

    private fun readRules(file: File): List<PrefixRule> = try {
        PrefixRulesParser.parse(file.readText()).rules
    } catch (_: IOException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }

    companion object {
        const val BUSINESSES_PREFIX = "businesses-"
        const val SPAM_PREFIX = "spam-"
        const val RULES_PREFIX = "rules-"
        private const val CLOSE_DELAY_MS = 30_000L

        fun loadBuiltInRules(): List<PrefixRule> = BuiltInRules.load().rules
    }
}
