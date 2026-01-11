package com.example.apptracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.apptracker.data.model.AppInfo
import com.example.apptracker.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<AppInfo> = emptyList(),
    val error: String? = null,
    val selectedApp: AppInfo? = null   // 🔹 nou: app-ul selectat pentru pagina de detalii
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: AppRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state

    fun updateQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return

        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { repo.search(q) }
                .onSuccess { newResults ->
                    // 🔹 momentan păstrăm comportamentul tău de "append"
                    val combined = _state.value.results + newResults
                    _state.value = _state.value.copy(
                        loading = false,
                        results = combined
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Eroare necunoscută"
                    )
                }
        }
    }

    fun clearResults() {
        _state.value = _state.value.copy(
            results = emptyList(),
            selectedApp = null
        )
    }

    // 🔹 selectăm un app pentru pagina de detaliu
    fun selectApp(app: AppInfo) {
        _state.value = _state.value.copy(selectedApp = app)
    }

    // 🔹 închidem pagina de detaliu
    fun closeDetails() {
        _state.value = _state.value.copy(selectedApp = null)
    }
}
