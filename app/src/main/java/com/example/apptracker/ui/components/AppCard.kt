package com.example.apptracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.apptracker.data.model.AppInfo

@Composable
fun AppCard(item: AppInfo, onOpen: (String) -> Unit) {

    val versionText = "${item.versionName ?: "-"} (${item.versionCode ?: "-"})"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { item.downloadUrl?.let(onOpen) }
    ) {
        Column(Modifier.padding(12.dp)) {

            // primul rând: icon + nume + pachet + sursă
            Row {
                AsyncImage(
                    model = item.iconUrl,
                    contentDescription = item.appName,
                    modifier = Modifier.size(48.dp)
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
                        text = item.packageName.ifBlank { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "Sursă: ${item.source}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // toate câmpurile din AppInfo, pe rânduri separate

            Text(
                text = "Versiune: $versionText",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "Developer: ${item.developer ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Release date: ${item.releaseDate ?: "-"}",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "Download: ${item.downloadUrl ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Icon URL: ${item.iconUrl ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
