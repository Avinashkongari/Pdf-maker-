package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ExportProgressState
import com.example.model.PdfExportConfig
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.example.ui.components.openPdfExternally
import com.example.ui.components.sharePdfExternally
import com.example.ui.theme.CharcoalOutline
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CharcoalSurfaceHigh
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TealTertiary
import com.example.ui.theme.VioletPrimary

@Composable
fun ExportCompleteScreen(
    exportState: ExportProgressState,
    pdfConfig: PdfExportConfig,
    onOpenOcrResults: () -> Unit,
    onDoneHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriString = exportState.outputUriString

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Export Complete",
                            tint = SuccessGreen,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Text(
                        text = "PDF Export Verified!",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    Text(
                        text = "${exportState.outputFileName.ifBlank { pdfConfig.outputFileName }}.pdf",
                        style = MaterialTheme.typography.titleMedium,
                        color = VioletPrimary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(text = "${exportState.totalPages} Pages")
                        StatusPill(text = formatFileSize(exportState.outputSizeBytes))
                        StatusPill(
                            text = "VERIFIED",
                            containerColor = CharcoalSurfaceHigh,
                            contentColor = TealTertiary
                        )
                    }

                    if (!exportState.validationSummary.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = TealTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = exportState.validationSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (uriString != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { openPdfExternally(context, uriString) },
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("complete_open_pdf_button")
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            sharePdfExternally(context, uriString, exportState.outputFileName)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("complete_share_pdf_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share PDF")
                    }
                }
            }
        }

        if (exportState.ocrPagesCompleted > 0) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                    border = BorderStroke(1.dp, CharcoalOutline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Offline OCR Complete (${exportState.ocrPagesCompleted} pages)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Extracted text is ready to view, search, copy, or export as a .TXT file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onOpenOcrResults,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Extracted OCR Panel")
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onDoneHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("complete_done_home_button")
            ) {
                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back to Home")
            }
        }
    }
}
