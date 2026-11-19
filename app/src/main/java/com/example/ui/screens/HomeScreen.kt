package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ExportHistoryEntity
import com.example.model.ImportedImage
import com.example.model.OcrSettings
import com.example.model.PdfExportConfig
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.CharcoalOutline
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CharcoalSurfaceHigh
import com.example.ui.theme.ErrorContainerDark
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TealTertiary
import com.example.ui.theme.VioletContainer
import com.example.ui.theme.VioletPrimary
import com.example.ui.theme.WarningAmber

@Composable
fun HomeScreen(
    images: List<ImportedImage>,
    pdfConfig: PdfExportConfig,
    ocrSettings: OcrSettings,
    history: List<ExportHistoryEntity>,
    interruptedBanner: String?,
    onDismissInterruptedBanner: () -> Unit,
    onNavigateImport: () -> Unit,
    onNavigateArrange: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateOcr: () -> Unit,
    onNavigatePreview: () -> Unit,
    onNavigateExport: () -> Unit,
    onNavigateHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (interruptedBanner != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ErrorContainerDark),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Recovery Notice",
                            tint = WarningAmber
                        )
                        Text(
                            text = interruptedBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissInterruptedBanner) {
                            Text("Dismiss", color = WarningAmber)
                        }
                    }
                }
            }
        }

        // Hero Banner Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, CharcoalOutline),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(215.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_manhwa_hero),
                        contentDescription = "Manhwa PDF Studio Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        CharcoalDeep.copy(alpha = 0.25f),
                                        CharcoalDeep.copy(alpha = 0.78f),
                                        CharcoalDeep.copy(alpha = 0.96f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                text = "100% OFFLINE",
                                containerColor = CharcoalDeep.copy(alpha = 0.85f),
                                contentColor = TealTertiary
                            )
                            StatusPill(
                                text = pdfConfig.imageHandlingMode.title.uppercase(),
                                containerColor = VioletContainer.copy(alpha = 0.85f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Manhwa PDF Maker",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Build lossless 200-page manhwa PDFs with automatic numbering and offline multi-language OCR.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = onNavigateImport,
                                    modifier = Modifier.testTag("home_import_cta"),
                                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (images.isEmpty()) "Import Pages" else "Add Pages (${images.size})")
                                }
                                if (images.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = onNavigateExport,
                                        modifier = Modifier.testTag("home_export_cta"),
                                        border = BorderStroke(1.dp, VioletPrimary.copy(alpha = 0.7f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Export PDF")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Workspace Status Card
        item {
            val totalBytes = images.sumOf { it.fileSizeBytes }
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, CharcoalOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Project Workspace",
                            style = MaterialTheme.typography.titleMedium
                        )
                        StatusPill(
                            text = if (images.isEmpty()) "EMPTY" else "${images.size} PAGES",
                            containerColor = if (images.isEmpty()) CharcoalSurfaceHigh else VioletContainer,
                            contentColor = if (images.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatTile(
                            label = "Raw Input",
                            value = formatFileSize(totalBytes),
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "Numbering",
                            value = if (pdfConfig.pageNumberConfig.enabled) pdfConfig.pageNumberConfig.formatLabel(0) else "OFF",
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "Offline OCR",
                            value = if (ocrSettings.enabled) "ON (${ocrSettings.selectedLanguages.size})" else "OFF",
                            valueColor = if (ocrSettings.enabled) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (images.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onNavigatePreview,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_preview_button")
                            ) {
                                Icon(Icons.Default.Preview, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Preview (${images.size})")
                            }
                            Button(
                                onClick = onNavigateArrange,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_arrange_button")
                            ) {
                                Icon(Icons.Default.DragIndicator, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reorder")
                            }
                        }
                    }
                }
            }
        }

        // Studio Navigation Grid
        item {
            Text(
                text = "Studio Tools",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioToolCard(
                        title = "Image Import",
                        subtitle = "Multi-select up to 200+ JPG/PNG/WEBP",
                        icon = Icons.Default.AddPhotoAlternate,
                        badgeText = "${images.size}",
                        onClick = onNavigateImport,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tool_card_import")
                    )
                    StudioToolCard(
                        title = "Arrangement",
                        subtitle = "Drag & drop reorder, sort & delete",
                        icon = Icons.Default.DragIndicator,
                        badgeText = "Order",
                        onClick = onNavigateArrange,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tool_card_arrange")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioToolCard(
                        title = "PDF & Numbering",
                        subtitle = pdfConfig.pageSizePreset.label,
                        icon = Icons.Default.Tune,
                        badgeText = if (pdfConfig.pageNumberConfig.enabled) pdfConfig.pageNumberConfig.prefix.token else "No #",
                        onClick = onNavigateSettings,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tool_card_settings")
                    )
                    StudioToolCard(
                        title = "OCR Reader",
                        subtitle = "EN, HI, JA, ZH offline engine",
                        icon = Icons.Default.DocumentScanner,
                        badgeText = if (ocrSettings.enabled) "ON" else "OFF",
                        onClick = onNavigateOcr,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tool_card_ocr")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioToolCard(
                        title = "PDF Preview",
                        subtitle = "Zoom, swipe, rotate & inspect",
                        icon = Icons.Default.Preview,
                        badgeText = "${images.size} pg",
                        onClick = onNavigatePreview,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tool_card_preview")
                    )
                    StudioToolCard(
                        title = "Export History",
                        subtitle = "${history.size} saved PDFs locally",
                        icon = Icons.Default.History,
                        badgeText = "${history.size}",
                        onClick = onNavigateHistory,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tool_card_history")
                    )
                }
            }
        }

        // Privacy Footer
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CharcoalSurfaceHigh.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, CharcoalOutline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = TealTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Private & Offline: Zero cloud uploads, zero analytics, zero storage permissions needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = CharcoalSurfaceHigh
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StudioToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        border = BorderStroke(1.dp, CharcoalOutline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VioletPrimary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = VioletPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                StatusPill(text = badgeText)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
