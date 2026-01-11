package com.example.apptracker.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.apptracker.data.model.AppInfo
import com.example.apptracker.ui.SearchViewModel
import com.example.apptracker.ui.components.AppCard

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
                modifier = Modifier.weight(1f)
            ) {
                Text("Caută")
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = vm::clearResults,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Curăță")
            }
        }

        if (state.loading) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }

        state.error?.let {
            Text(
                "Eroare: $it",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        val selected = state.selectedApp

        if (selected != null) {
            AppDetailsScreen(
                app = selected,
                onBack = vm::closeDetails,
                onOpenInBrowser = { url ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    ctx.startActivity(intent)
                }
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.results) { app ->
                    AppCard(app) { selectedApp ->
                        vm.selectApp(selectedApp)
                    }
                }
            }
        }

    }
}

@Composable
fun AppDetailsScreen(
    app: AppInfo,
    onBack: () -> Unit,
    onOpenInBrowser: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        // 🔙 back button + title
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Înapoi") }

            Spacer(Modifier.width(8.dp))

            Text(
                text = app.appName,
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(Modifier.height(16.dp))

        // Icon + info basic
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = app.appName,
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column {
                Text("Package: ${app.packageName}")
                Text("Sursă: ${app.source}")
            }
        }

        Spacer(Modifier.height(16.dp))

        val versionText = "${app.versionName ?: "-"} (${app.versionCode ?: "-"})"
        Text("Versiune: $versionText")
        Text("Developer: ${app.developer ?: "-"}")
        Text("Release date: ${app.releaseDate ?: "-"}")

        Spacer(Modifier.height(12.dp))

        // 🔥 AICI VINE CODUL TĂU — INFO EXTRA APKMIRROR 🔥

        if (!app.description.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Description:",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = app.description,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        app.fileSize?.let {
            Spacer(Modifier.height(8.dp))
            Text("File size: $it")
        }

        app.minAndroidVersion?.let {
            Spacer(Modifier.height(8.dp))
            Text("Min Android: $it")
        }

        app.downloads?.let {
            Spacer(Modifier.height(8.dp))
            Text("Downloads: $it")
        }

        Spacer(Modifier.height(16.dp))

        // 🔗 open source page
        app.downloadUrl?.let { url ->
            Button(onClick = { onOpenInBrowser(url) }) {
                Text("Deschide în browser")
            }
        }
    }
}
