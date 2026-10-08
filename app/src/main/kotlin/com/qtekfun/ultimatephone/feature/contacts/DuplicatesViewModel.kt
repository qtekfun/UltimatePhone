package com.qtekfun.ultimatephone.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatephone.core.contacts.ContactMerger
import com.qtekfun.ultimatephone.core.contacts.ContactsManager
import com.qtekfun.ultimatephone.core.contacts.DuplicateCluster
import com.qtekfun.ultimatephone.core.contacts.DuplicateDetector
import com.qtekfun.ultimatephone.core.contacts.DuplicatePairs
import com.qtekfun.ultimatephone.core.contacts.MergeSource
import com.qtekfun.ultimatephone.core.contacts.MergedContact
import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The merge being reviewed: every contact of the cluster, which of them the user keeps in the merge, and what results. */
data class ReviewState(val cluster: DuplicateCluster, val sources: List<MergeSource>, val included: Set<String>, val merged: MergedContact?) {
    val canMerge: Boolean get() = merged != null && included.size >= 2
}

/** One-off outcome shown as a message. */
enum class DuplicatesMessage { MERGED, MERGE_FAILED, IGNORED, LOAD_FAILED }

data class DuplicatesUiState(
    val loading: Boolean = true,
    val clusters: List<DuplicateCluster> = emptyList(),
    val review: ReviewState? = null,
    val reviewLoading: Boolean = false,
    val busy: Boolean = false,
    val message: DuplicatesMessage? = null
)

@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val contacts: ContactsManager,
    private val ignored: IgnoredDuplicates,
    private val normalizer: PhoneNormalizer,
    private val regionProvider: RegionProvider
) : ViewModel() {
    private val _state = MutableStateFlow(DuplicatesUiState())
    val state: StateFlow<DuplicatesUiState> = _state

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val candidates = contacts.duplicateCandidates()
            val pairs = ignored.pairs.first()
            val region = regionProvider.defaultRegion()
            val clusters = withContext(Dispatchers.Default) { DuplicateDetector(normalizer, region).detect(candidates, pairs) }
            _state.update { it.copy(loading = false, clusters = clusters) }
        }
    }

    fun open(cluster: DuplicateCluster) {
        viewModelScope.launch {
            _state.update { it.copy(reviewLoading = true) }
            val sources = contacts.mergeSources(cluster.members.map { it.lookupKey })
            if (sources.size < 2) {
                _state.update { it.copy(reviewLoading = false, message = DuplicatesMessage.LOAD_FAILED) }
                return@launch
            }
            val included = sources.map { it.lookupKey }.toSet()
            _state.update { it.copy(reviewLoading = false, review = ReviewState(cluster, sources, included, mergeOf(sources, included))) }
        }
    }

    private fun mergeOf(sources: List<MergeSource>, included: Set<String>): MergedContact? {
        val chosen = sources.filter { it.lookupKey in included }
        if (chosen.isEmpty()) return null
        return ContactMerger(normalizer, regionProvider.defaultRegion()).merge(chosen)
    }

    /** Includes or leaves out one contact of the cluster; the preview follows. */
    fun toggle(lookupKey: String) {
        _state.update { current ->
            val review = current.review ?: return@update current
            val included = if (lookupKey in review.included) review.included - lookupKey else review.included + lookupKey
            current.copy(review = review.copy(included = included, merged = mergeOf(review.sources, included)))
        }
    }

    fun closeReview() {
        _state.update { it.copy(review = null) }
    }

    /** Called only after the user confirmed in the dialog: merges the included contacts. */
    fun confirmMerge() {
        val review = _state.value.review ?: return
        val merged = review.merged ?: return
        if (!review.canMerge || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val key = contacts.mergeContacts(merged.toPlan())
            if (key == null) {
                _state.update { it.copy(busy = false, message = DuplicatesMessage.MERGE_FAILED) }
            } else {
                _state.update { it.copy(busy = false, review = null, message = DuplicatesMessage.MERGED) }
                reload()
            }
        }
    }

    /** "Not duplicates": remembers every pair of the cluster so it is not suggested again. */
    fun notDuplicates(cluster: DuplicateCluster) {
        viewModelScope.launch {
            ignored.ignore(DuplicatePairs.keysOf(cluster.members.map { it.lookupKey }))
            _state.update { it.copy(review = null, clusters = it.clusters.filter { c -> c.id != cluster.id }, message = DuplicatesMessage.IGNORED) }
        }
    }

    fun ignoreAll() {
        viewModelScope.launch {
            val all = _state.value.clusters.flatMapTo(HashSet()) { DuplicatePairs.keysOf(it.members.map { m -> m.lookupKey }) }
            ignored.ignore(all)
            _state.update { it.copy(clusters = emptyList(), message = DuplicatesMessage.IGNORED) }
        }
    }

    fun messageShown() {
        _state.update { it.copy(message = null) }
    }
}
