package com.qtekfun.ultimatephone.settings

/** Stores the "SIM per number" map in one string: `number<TAB>simKey` lines. Numbers are E.164 and keys never hold a tab. */
object SimMemoryCodec {
    private const val SEPARATOR = '\t'

    fun encode(map: Map<String, String>): String = map.entries.joinToString("\n") { "${it.key}$SEPARATOR${it.value}" }

    fun decode(text: String): Map<String, String> = text.lineSequence().mapNotNull { line ->
        val parts = line.split(SEPARATOR, limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0] to parts[1] else null
    }.toMap()
}
