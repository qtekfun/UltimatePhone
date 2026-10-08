package com.qtekfun.ultimatephone.core.contacts

/** Finds which cards of a file are already in the address book. Pure. */
object ImportDuplicates {
    /**
     * Indices (into [imported]) of the cards that [detector] groups with at least one stored contact. Cards that only
     * resemble other cards of the same file are not reported: importing them is the user's call.
     *
     * @param existing stored contacts, with positive ids ([VCardContact.toDuplicateContact] gives the imported ones negative ids).
     */
    fun of(detector: DuplicateDetector, existing: List<DuplicateContact>, imported: List<VCardContact>): Set<Int> {
        if (existing.isEmpty() || imported.isEmpty()) return emptySet()
        val cards = imported.mapIndexed { index, card -> card.toDuplicateContact(index) }
        val found = HashSet<Int>()
        for (cluster in detector.detect(existing.filter { it.contactId > 0 } + cards)) {
            if (cluster.members.none { it.contactId > 0 }) continue
            for (member in cluster.members) if (member.contactId < 0) found += (-member.contactId - 1).toInt()
        }
        return found
    }
}
