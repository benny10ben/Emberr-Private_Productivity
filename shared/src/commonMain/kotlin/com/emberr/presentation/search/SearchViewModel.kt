package com.emberr.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emberr.domain.model.NoteSearchResult
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.space.ActiveSpaceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

private const val QUERY_SETTLE_MILLIS = 90L

/**
 * Backs the cross-note search screen.
 *
 * [query] is updated on every keystroke (so the UI can highlight matches instantly), while
 * [results] is derived from a *debounced* copy of that same value - re-querying Room on every
 * character would mean firing (and mostly discarding) a query per keystroke while the user is
 * still typing. flatMapLatest cancels any in-flight search the moment a newer query arrives,
 * so a slow search for "meet" never race-overwrites the result of a fast-typed "meeting".
 *
 * Each search emits twice: the metadata-only hits land almost immediately, then the full list
 * (metadata + block-content hits) replaces them once the slower content scan finishes. The debounce
 * is therefore kept short - it exists to skip mid-word queries, not to hide a slow search.
 */
class SearchViewModel(
    private val repository: NoteRepository,
    activeSpaceStore: ActiveSpaceStore
) : ViewModel() {

    private data class SearchRequest(val query: String, val spaceId: String)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<NoteSearchResult>> = combine(
        _query.debounce(QUERY_SETTLE_MILLIS).distinctUntilChanged(),
        activeSpaceStore.activeSpaceId
    ) { query, spaceId -> SearchRequest(query, spaceId) }
        .distinctUntilChanged()
        .flatMapLatest { request ->
            if (request.query.isBlank()) flowOf(emptyList())
            else flow {
                val quickMatches = repository.searchNoteTitlesAndSnippets(request.query)
                if (quickMatches.isNotEmpty()) emit(quickMatches)
                emit(repository.searchNotes(request.query))
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }
}
