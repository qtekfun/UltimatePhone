package com.qtekfun.ultimatephone.core.calllog

import com.qtekfun.ultimatephone.core.phonenumber.NormalizedNumber
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

val Utc: ZoneId = ZoneOffset.UTC

fun millis(iso: String): Long = Instant.parse(iso).toEpochMilli()

fun clockAt(iso: String): Clock = Clock.fixed(Instant.parse(iso), Utc)

fun entry(
    id: Long,
    number: String = "600111222",
    type: CallType = CallType.INCOMING,
    date: Long = id * 1000,
    duration: Long = 0,
    simComponent: String? = null,
    simId: String? = null,
    cachedName: String? = null,
    isNew: Boolean = false
) = CallLogEntry(id, number, type, date, duration, simComponent, simId, null, cachedName, isNew)

/** Treats "+34" prefixed and bare 9-digit numbers as the same Spanish number; anything else is unknown. */
class FakeNormalizer : PhoneNormalizer {
    override fun normalize(raw: String?, defaultRegion: String?): NormalizedNumber {
        val digits = raw.orEmpty().filter { it.isDigit() }
        return when {
            digits.length == 9 -> NormalizedNumber.Valid("+34$digits", "ES")
            digits.length == 11 && digits.startsWith("34") -> NormalizedNumber.Valid("+$digits", "ES")
            else -> NormalizedNumber.Unknown
        }
    }

    override fun formatForDisplay(raw: String?, defaultRegion: String?): String = raw.orEmpty()
}

class FakeRegion(private val region: String = "ES") : RegionProvider {
    override fun defaultRegion(): String = region
}
