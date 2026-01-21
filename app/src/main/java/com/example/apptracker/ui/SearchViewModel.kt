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

    // ✅ full screen details
    val selected: AppInfo? = null,
    val selectedLoading: Boolean = false
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

    fun clearResults() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                loading = false,
                results = emptyList(),
                error = null,
                selected = null,
                selectedLoading = false
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
            _state.update { it.copy(loading = true, error = null, selected = null, selectedLoading = false) }

            runCatching { repo.searchLite(q, limit = 20) }
                .onSuccess { list ->
                    _state.update { it.copy(loading = false, results = list, error = null) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Eroare necunoscută") }
                }
        }
    }

    fun openDetails(item: AppInfo) {
        _state.update { it.copy(selected = item, selectedLoading = true, error = null) }

        viewModelScope.launch {
            runCatching { repo.loadDetails(item) }
                .onSuccess { detailed ->
                    _state.update { st ->
                        // also update list item if present
                        val updatedList = st.results.map { if (sameItem(it, item)) detailed else it }
                        st.copy(
                            results = updatedList,
                            selected = detailed,
                            selectedLoading = false
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { st ->
                        st.copy(
                            selectedLoading = false,
                            error = e.message ?: "Nu am putut încărca detaliile."
                        )
                    }
                }
        }
    }

    fun closeDetails() {
        _state.update { it.copy(selected = null, selectedLoading = false) }
    }

    private fun sameItem(a: AppInfo, b: AppInfo): Boolean {
        // stable identity:
        // for F-Droid: packageName
        // for APKMirror: downloadUrl
        return (a.source == b.source) && when (a.source) {
            "F-Droid" -> a.packageName.isNotBlank() && a.packageName == b.packageName
            "APKMirror" -> !a.downloadUrl.isNullOrBlank() && a.downloadUrl == b.downloadUrl
            else -> a.appName == b.appName
        }
    }
}
