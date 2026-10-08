package com.qtekfun.ultimatephone.data

import com.qtekfun.ultimatephone.core.spam.decision.BusinessLookup
import com.qtekfun.ultimatephone.core.spam.decision.PrefixRules
import com.qtekfun.ultimatephone.core.spam.decision.SourceLookup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Read access to the packs installed on the device (downloaded data packs and the user's custom sources), as the lookups
 * the decision engine wants. The call screening service reads these on every call, so implementations keep them in memory
 * or behind one indexed query and never touch the network.
 */
interface InstalledPackLookups {
    val sources: SourceLookup
    val businesses: BusinessLookup
    val prefixRules: PrefixRules

    /** Emits whenever a pack is installed, updated or removed, so holders of cached state can reload. */
    val changes: Flow<Unit>
}

/** Placeholder until the data feature binds the real implementation. */
object EmptyInstalledPackLookups : InstalledPackLookups {
    override val sources = SourceLookup { null }
    override val businesses = BusinessLookup { null }
    override val prefixRules = PrefixRules { _, _ -> null }
    override val changes: Flow<Unit> = emptyFlow()
}
