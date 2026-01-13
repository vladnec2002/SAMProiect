package com.example.apptracker.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.apptracker.ui.SearchViewModel
import com.example.apptracker.ui.components.AppCard

@Composable
fun SearchScreen(vm: SearchViewModel) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

    fun keyOf(source: String, pkg: String, url: String?, name: String): String =
        "$source:${if (pkg.isNotBlank()) pkg else (url ?: name)}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp
            )
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::updateQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Caută aplicație (ex: Firefox sau org.mozilla.firefox)") }
        )

        Spacer(Modifier.height(8.dp))

        Row {
            Button(
                onClick = vm::search,
                modifier = Modifier.weight(1f),
                enabled = !state.loading
            ) { Text("Caută") }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = vm::clearResults,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                enabled = !state.loading
            ) { Text("Curăță") }
        }

        if (state.loading) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        state.error?.let {
            Text(
                "Eroare: $it",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(top = 12.dp)
        ) {
            items(state.results) { app ->
                val k = keyOf(app.source, app.packageName, app.downloadUrl, app.appName)
                val itemLoading = state.loadingKeys.contains(k)
                val expanded = state.expandedKeys.contains(k)

                AppCard(
                    item = app,
                    loading = itemLoading,
                    expanded = expanded,
                    onToggleExpand = { vm.toggleExpandAndMaybeLoad(app) },
                    onOpen = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        ctx.startActivity(intent)
                    }
                )
            }
        }
    }
}
