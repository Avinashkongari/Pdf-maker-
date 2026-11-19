package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ExportProgressState
import com.example.model.ExportStatus
import com.example.model.ImportedImage
import com.example.model.OcrSettings
import com.example.model.PdfExportConfig
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.example.ui.theme.CharcoalOutline
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CharcoalSurfaceHigh
import com.example.ui.theme.ErrorContainerDark
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.VioletPrimary
import kotlin.math.roundToInt

@Composable
fun ExportProgressScreen(
    images: List<ImportedImage>,
    pdfConfig: PdfExportConfig,
    ocrSettings: OcrSettings,
    exportState: ExportProgressState,
    onFilenameChange: (String) -> Unit,
    onStartExportUri: (Uri) -> Unit,
    onCancelExport: () -> Unit,
    onResetExportState: () -> Unit,
    onNavigateComplete: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(exportState.status) {
        if (exportState.status == ExportStatus.COMPLETED) {
            onNavigateComplete()
        }
    }

    val safCreatePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            onStartExportUri(uri)
        }
    }

    val isRunning = exportState.status == ExportStatus.RUNNING || exportState.status == ExportStatus.VALIDATING

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, CharcoalOutline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SectionHeader(
                        title = "Export Single PDF",
                        subtitle = "Streaming lossless writer • SAF verified",
                        icon = Icons.Default.PictureAsPdf
                    )

                    OutlinedTextField(
                        value = pdfConfig.outputFileName,
                        onValueChange = { raw ->
                            val clean = raw.replace(Regex("[\\\\/:*?\"<>|]"), "")
                            onFilenameChange(clean)
                        },
                        label = { Text("PDF File Name") },
                        suffix = { Text(".pdf") },
                        enabled = !isRunning,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_filename_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusPill(text = "${images.size} Pages")
                        StatusPill(text = pdfConfig.imageHandlingMode.title)
                        StatusPill(text = if (ocrSettings.enabled) "OCR ON" else "OCR OFF")
                    }

                    Text(
                        text = "Raw Input Size: ${formatFileSize(images.sumOf { it.fileSizeBytes })} • Page Size: ${pdfConfig.pageSizePreset.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isRunning) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                    border = BorderStroke(1.dp, VioletPrimary)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val pct = (exportState.progressFraction * 100f).roundToInt().coerceIn(0, 100)
                        CircularProgressIndicator(
                            progress = { exportState.progressFraction },
                            modifier = Modifier.size(72.dp),
                            strokeWidth = 6.dp
                        )
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = VioletPrimary
                        )
                        Text(
                            text = exportState.phaseLabel,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (exportState.totalPages > 0) {
                            Text(
                                text = "Processing page ${exportState.currentPage} of ${exportState.totalPages}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { exportState.progressFraction },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedButton(
                            onClick = onCancelExport,
                            border = BorderStroke(1.dp, ErrorCoral.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cancel_export_button")
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, tint = ErrorCoral, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cancel Export", color = ErrorCoral)
                        }
                    }
                }
            }
        } else if (exportState.status == ExportStatus.FAILED || exportState.status == ExportStatus.CANCELLED) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorContainerDark),
                    border = BorderStroke(1.dp, ErrorCoral)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorCoral)
                            Text(
                                text = if (exportState.status == ExportStatus.CANCELLED) "Export Cancelled" else "Export Failed",
                                style = MaterialTheme.typography.titleMedium,
                                color = ErrorCoral
                            )
                        }
                        Text(
                            text = exportState.errorMessage ?: "Unable to finish export.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onResetExportState()
                                    onNavigateBack()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Back")
                            }
                            Button(
                                onClick = {
                                    onResetExportState()
                                    val fileName = "${pdfConfig.outputFileName.ifBlank { "Manhwa_PDF" }}.pdf"
                                    safCreatePdfLauncher.launch(fileName)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Retry Export")
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceHigh),
                    border = BorderStroke(1.dp, CharcoalOutline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Ready to Generate",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "You will pick the destination folder via Android Storage Access Framework. The PDF is verified page-by-page before reporting success.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                val fileName = "${pdfConfig.outputFileName.ifBlank { "Manhwa_PDF" }}.pdf"
                                safCreatePdfLauncher.launch(fileName)
                            },
                            enabled = images.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("choose_saf_export_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose Save Location & Export")
                        }
                    }
                }
            }
        }
    }
}
