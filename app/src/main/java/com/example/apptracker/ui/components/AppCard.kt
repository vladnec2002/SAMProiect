package com.example.apptracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.apptracker.data.model.AppInfo

@Composable
fun AppCard(
    item: AppInfo,
    onClick: () -> Unit,
    onOpen: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() }
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.iconUrl,
                contentDescription = item.appName,
                modifier = Modifier.size(40.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Sursă: ${item.source}",
                    style = MaterialTheme.typography.bodySmall
                )

                val hintDate = when (item.source) {
                    "APKMirror" -> item.lastUpdated
                    "F-Droid" -> item.releaseDate
                    else -> item.releaseDate ?: item.lastUpdated
                }
                if (!hintDate.isNullOrBlank()) {
                    Text(hintDate, style = MaterialTheme.typography.bodySmall)
                }
            }

            item.downloadUrl?.let { url ->
                TextButton(onClick = { onOpen(url) }) { Text("Open") }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    Text(
        text = "$label: ${value?.takeIf { it.isNotBlank() } ?: "-"}",
        style = MaterialTheme.typography.bodyMedium
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenDetailsDialog(
    item: AppInfo,
    loading: Boolean,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {

                TopAppBar(
                    title = {
                        Text(
                            item.appName.ifBlank { item.packageName.ifBlank { "Details" } },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        item.downloadUrl?.let { url ->
                            TextButton(onClick = { onOpen(url) }) { Text("Open") }
                        }
                    }
                )

                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                val scroll = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(scroll)
                ) {

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = item.iconUrl,
                            contentDescription = item.appName,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(item.appName, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Source: ${item.source}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    DetailLine("Package", item.packageName)
                    DetailLine("Developer", item.developer)
                    DetailLine("Version", item.versionName)
                    DetailLine("VersionCode", item.versionCode?.toString())

                    if (item.source == "APKMirror") {
                        DetailLine("Uploaded", item.releaseDate)
                        DetailLine("Uploaded UTC", item.uploadedUtc)
                        DetailLine("File size", item.fileSize)
                        DetailLine("Downloads", item.downloads)

                        DetailLine("Min Android", item.minAndroid)
                        DetailLine("Architecture", item.architecture)
                        DetailLine("DPI", item.dpi)

                        if (item.badges.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Badges:", style = MaterialTheme.typography.titleMedium)
                            Text(item.badges.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                        }

                        if (item.variants.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Variants:", style = MaterialTheme.typography.titleMedium)

                            item.variants.forEach { v ->
                                Spacer(Modifier.height(8.dp))
                                Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                                    Column(Modifier.padding(12.dp)) {
                                        DetailLine("Variant", v.variantName)
                                        if (v.badges.isNotEmpty()) DetailLine("Badges", v.badges.joinToString(", "))
                                        DetailLine("Arch", v.architecture)
                                        DetailLine("Min Android", v.minAndroid)
                                        DetailLine("DPI", v.dpi)

                                        v.variantPageUrl?.let { u ->
                                            Spacer(Modifier.height(6.dp))
                                            TextButton(onClick = { onOpen(u) }) { Text("Open variant page") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else if (item.source == "F-Droid") {
                        DetailLine("Release date", item.releaseDate)
                        DetailLine("Summary", item.summary)
                        DetailLine("License", item.license)
                        DetailLine("Source code", item.sourceCodeUrl)
                    } else {
                        DetailLine("Date", item.releaseDate ?: item.lastUpdated)
                    }
                }
            }
        }
    }
}
