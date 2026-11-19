package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ExportHistoryEntity
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimestamp
import com.example.ui.components.openPdfExternally
import com.example.ui.components.sharePdfExternally
import com.example.ui.theme.CharcoalOutline
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.TealTertiary
import com.example.ui.theme.VioletPrimary

@Composable
fun ExportHistoryScreen(
    history: List<ExportHistoryEntity>,
    onDeleteEntry: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(
                title = "Export History",
                subtitle = "${history.size} PDFs recorded locally",
                icon = Icons.Default.History,
                modifier = Modifier.weight(1f)
            )
            if (history.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = ErrorCoral, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", color = ErrorCoral)
                }
            }
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = VioletPrimary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text("No Exported PDFs Yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Completed PDFs will appear here with quick Open and Share shortcuts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(history, key = { it.id }) { entry ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                        border = BorderStroke(1.dp, CharcoalOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${entry.fileName}.pdf",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = formatTimestamp(entry.createdAtMillis),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteEntry(entry.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete history item",
                                        tint = ErrorCoral,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(text = "${entry.pageCount} pg")
                                StatusPill(text = formatFileSize(entry.fileSizeBytes))
                                StatusPill(text = entry.imageModeName)
                                if (entry.ocrEnabled) {
                                    StatusPill(text = "OCR", contentColor = TealTertiary)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { openPdfExternally(context, entry.outputUriString) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open")
                                }
                                OutlinedButton(
                                    onClick = { sharePdfExternally(context, entry.outputUriString, entry.fileName) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
