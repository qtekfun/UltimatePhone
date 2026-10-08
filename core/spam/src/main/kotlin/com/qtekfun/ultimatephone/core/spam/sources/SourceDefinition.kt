package com.qtekfun.ultimatephone.core.spam.sources

import com.qtekfun.ultimatephone.core.spam.decision.SpamLevel

enum class SourceFormat { TXT, CSV, JSONL, PACK }

/** Whether the data repo may repackage a source. A source that does not allow it is fetched by the app directly. */
enum class RedistributionPolicy { REDISTRIBUTABLE, DIRECT_DOWNLOAD_ONLY }

/** A spam source, from the built-in catalogue or added by the user by URL. */
data class SourceDefinition(
    val id: String,
    val name: String,
    val url: String,
    val format: SourceFormat,
    val license: String,
    val redistribution: RedistributionPolicy,
    val level: SpamLevel = SpamLevel.COMMUNITY,
    val enabled: Boolean = true
)
