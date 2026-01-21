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
import com.example.apptracker.ui.components.FullScreenDetailsDialog

@Composable
fun SearchScreen(vm: SearchViewModel) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

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
                AppCard(
                    item = app,
                    onClick = { vm.openDetails(app) },
                    onOpen = { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        ctx.startActivity(intent)
                    }
                )
            }
        }
    }

    // ✅ Full-screen details
    state.selected?.let { selected ->
        FullScreenDetailsDialog(
            item = selected,
            loading = state.selectedLoading,
            onDismiss = vm::closeDetails,
            onOpen = { url ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                ctx.startActivity(intent)
            }
        )
    }
}
