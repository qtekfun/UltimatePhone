package com.qtekfun.ultimatephone.feature.recents

import android.telecom.PhoneAccountHandle
import com.qtekfun.ultimatephone.core.calllog.CallLogEntry
import com.qtekfun.ultimatephone.core.calllog.CallLogFilter
import com.qtekfun.ultimatephone.core.calllog.CallLogRepository
import com.qtekfun.ultimatephone.core.calllog.CallType
import com.qtekfun.ultimatephone.core.calllog.filtered
import com.qtekfun.ultimatephone.core.contacts.ContactMatch
import com.qtekfun.ultimatephone.core.contacts.ContactSummary
import com.qtekfun.ultimatephone.core.contacts.ContactsRepository
import com.qtekfun.ultimatephone.core.contacts.DialerSuggestion
import com.qtekfun.ultimatephone.core.phonenumber.NormalizedNumber
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.telecom.CallPlacer
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.core.telecom.SimAccount
import com.qtekfun.ultimatephone.core.telecom.SimRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

fun entry(
    id: Long,
    number: String = "600111222",
    type: CallType = CallType.INCOMING,
    date: Long = 1_760_000_000_000L + id * 60_000,
    duration: Long = 0,
    cachedName: String? = null,
    isNew: Boolean = false
) = CallLogEntry(id, number, type, date, duration, null, null, null, cachedName, isNew)

class FakeCallLog(initial: List<CallLogEntry> = emptyList()) : CallLogRepository {
    val entries = MutableStateFlow(initial)
    val deleted = mutableListOf<Long>()
    var cleared = 0
    var markedRead = 0

    override fun observe(filter: CallLogFilter): Flow<List<CallLogEntry>> = entries.map { it.filtered(filter) }

    override suspend fun delete(ids: Collection<Long>): Int {
        deleted += ids
        entries.value = entries.value.filterNot { it.id in ids }
        return ids.size
    }

    override suspend fun clearAll(): Int {
        cleared++
        entries.value = emptyList()
        return 0
    }

    override suspend fun markAllRead() {
        markedRead++
    }
}

class FakeContacts(var byNumber: Map<String, ContactMatch> = emptyMap()) : ContactsRepository {
    var lookups = 0

    override fun observeContacts(): Flow<List<ContactSummary>> = flowOf(emptyList())

    override suspend fun lookupByNumber(number: String): ContactMatch? {
        lookups++
        return byNumber[number.filter { it.isDigit() }.takeLast(9)]
    }

    override suspend fun suggest(query: String, limit: Int): List<DialerSuggestion> = emptyList()
}

class FakeSims(private val sims: List<SimAccount> = emptyList()) : SimRepository {
    override fun accounts(): List<SimAccount> = sims

    override fun find(handle: PhoneAccountHandle?): SimAccount? = null

    override fun find(componentName: String?, id: String?): SimAccount? = null
}

class FakePlacer(var succeeds: Boolean = true) : CallPlacer {
    val placed = mutableListOf<String>()

    override fun placeCall(number: String, sim: SimAccount?): Boolean {
        placed += number
        return succeeds
    }
}

/** 9-digit numbers (with or without +34) are valid Spanish numbers; everything else is unknown. */
class FakeNormalizer : PhoneNormalizer {
    override fun normalize(raw: String?, defaultRegion: String?): NormalizedNumber {
        val digits = raw.orEmpty().filter { it.isDigit() }.removePrefix("34").takeIf { it.length == 9 }
        return if (digits != null) NormalizedNumber.Valid("+34$digits", "ES") else NormalizedNumber.Unknown
    }

    override fun formatForDisplay(raw: String?, defaultRegion: String?): String = "fmt:" + raw.orEmpty()
}

class FakeRegion : RegionProvider {
    override fun defaultRegion(): String = "ES"
}

fun match(name: String, key: String = "key-$name") = ContactMatch(key, name, null)
