package com.example.apptracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.apptracker.data.model.AppInfo

@Composable
fun AppCard(
    item: AppInfo,
    loading: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpen: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onToggleExpand() }
    ) {
        Column(Modifier.padding(12.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
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
                }

                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }

                item.downloadUrl?.let { url ->
                    TextButton(
                        onClick = { onOpen(url) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Open")
                    }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))

                // ✅ Source-aware date labels:
                if (item.source == "APKMirror") {
                    Text(
                        text = "Last updated: ${item.lastUpdated ?: "-"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Uploaded: ${item.releaseDate ?: "-"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (item.source == "F-Droid") {
                    Text(
                        text = "Release date: ${item.releaseDate ?: "-"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    // fallback for other sources
                    Text(
                        text = "Date: ${item.releaseDate ?: item.lastUpdated ?: "-"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(
                    text = "Versiune: ${item.versionName ?: "-"} (${item.versionCode ?: "-"})",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Developer: ${item.developer ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Only meaningful for APKMirror details
                if (item.source == "APKMirror") {
                    Text(
                        text = "Min Android: ${item.minAndroid ?: "-"}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "Architecture: ${item.architecture ?: "-"}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "Package: ${item.packageName.ifBlank { "-" }}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "Package: ${item.packageName.ifBlank { "-" }}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
