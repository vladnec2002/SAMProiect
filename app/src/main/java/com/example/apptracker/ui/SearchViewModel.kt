package com.example.apptracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.apptracker.data.model.AppInfo
import com.example.apptracker.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<AppInfo> = emptyList(),
    val error: String? = null,
    val loadingKeys: Set<String> = emptySet(),
    val expandedKeys: Set<String> = emptySet() // ✅ pentru expand/collapse
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: AppRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state

    private var searchJob: Job? = null

    fun updateQuery(q: String) {
        _state.update { it.copy(query = q, error = null) }
    }

    private fun keyOf(item: AppInfo): String =
        "${item.source}:${if (item.packageName.isNotBlank()) item.packageName else (item.downloadUrl ?: item.appName)}"

    fun clearResults() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                loading = false,
                results = emptyList(),
                error = null,
                loadingKeys = emptySet(),
                expandedKeys = emptySet()
            )
        }
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) {
            _state.update { it.copy(error = "Introdu un termen de căutare.") }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, expandedKeys = emptySet()) }

            runCatching { repo.searchLite(q, limit = 20) }
                .onSuccess { list ->
                    _state.update { it.copy(loading = false, results = list, error = null) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Eroare necunoscută") }
                }
        }
    }

    /**
     * Click pe card:
     * - toggle expand/collapse
     * - dacă se expandează și nu avem încă detalii heavy (package/releaseDate),
     *   pornește loadDetails pentru acel item.
     */
    fun toggleExpandAndMaybeLoad(item: AppInfo) {
        val k = keyOf(item)
        val isExpanded = _state.value.expandedKeys.contains(k)
        val newExpanded = if (isExpanded) _state.value.expandedKeys - k else _state.value.expandedKeys + k
        _state.update { it.copy(expandedKeys = newExpanded, error = null) }

        // dacă am colapsat, nu încărcăm nimic
        if (isExpanded) return

        // already heavy-loaded? (ai deja package sau releaseDate -> considerăm că avem “more info”)
        val heavyLoaded = item.packageName.isNotBlank() || item.releaseDate != null
        if (heavyLoaded) return

        loadDetails(item)
    }

    private fun loadDetails(item: AppInfo) {
        val k = keyOf(item)
        if (_state.value.loadingKeys.contains(k)) return

        viewModelScope.launch {
            _state.update { it.copy(loadingKeys = it.loadingKeys + k, error = null) }

            runCatching { repo.loadDetails(item) }
                .onSuccess { detailed ->
                    _state.update { st ->
                        val newList = st.results.map { if (keyOf(it) == k) detailed else it }
                        st.copy(results = newList, loadingKeys = st.loadingKeys - k)
                    }
                }
                .onFailure { e ->
                    _state.update { st ->
                        st.copy(
                            loadingKeys = st.loadingKeys - k,
                            error = e.message ?: "Nu am putut încărca detaliile."
                        )
                    }
                }
        }
    }
}
