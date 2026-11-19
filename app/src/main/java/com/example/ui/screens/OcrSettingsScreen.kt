package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.model.OcrLanguage
import com.example.model.OcrLanguageStatus
import com.example.model.OcrModelStatus
import com.example.model.OcrPageResult
import com.example.model.OcrSettings
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.CharcoalOutline
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CharcoalSurfaceHigh
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TealTertiary
import com.example.ui.theme.VioletPrimary

@Composable
fun OcrSettingsScreen(
    ocrSettings: OcrSettings,
    languageStatuses: List<OcrLanguageStatus>,
    ocrResults: List<OcrPageResult>,
    searchQuery: String,
    isScanning: Boolean,
    scanProgress: Pair<Int, Int>,
    imageCount: Int,
    onUpdateSettings: ((OcrSettings) -> OcrSettings) -> Unit,
    onRunOcr: () -> Unit,
    onCancelOcr: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onExportTxtUri: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val txtSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            onExportTxtUri(uri)
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Master ENABLE OCR Toggle Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(
                    1.dp,
                    if (ocrSettings.enabled) VioletPrimary else CharcoalOutline
                )
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
                        SectionHeader(
                            title = "ENABLE OCR",
                            subtitle = if (ocrSettings.enabled) "ON — Extracts text offline without altering PDF images" else "OFF — Zero OCR runs in background",
                            icon = Icons.Default.DocumentScanner,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = ocrSettings.enabled,
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(enabled = enabled) }
                            },
                            modifier = Modifier.testTag("enable_ocr_switch")
                        )
                    }

                    StatusPill(
                        text = if (ocrSettings.enabled) {
                            "On-device ML Kit Engine • Original images & PDF pixels remain untouched"
                        } else {
                            "OCR Disabled • Normal fast lossless PDF generation"
                        },
                        containerColor = CharcoalSurfaceHigh,
                        contentColor = if (ocrSettings.enabled) TealTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (ocrSettings.enabled) {
            // Language Selection & Offline Model Status
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
                        Text("Offline OCR Languages & Model Status", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Select one or more scripts. Models are bundled locally inside the app for 100% offline use.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        languageStatuses.forEach { itemStatus ->
                            val lang = itemStatus.language
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (itemStatus.isSelected) VioletPrimary.copy(alpha = 0.14f) else CharcoalSurfaceHigh
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (itemStatus.isSelected) VioletPrimary else CharcoalOutline
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = itemStatus.isSelected,
                                        onCheckedChange = { checked ->
                                            onUpdateSettings { prev ->
                                                val nextSet = if (checked) {
                                                    prev.selectedLanguages + lang
                                                } else {
                                                    (prev.selectedLanguages - lang).ifEmpty { setOf(lang) }
                                                }
                                                prev.copy(selectedLanguages = nextSet)
                                            }
                                        },
                                        modifier = Modifier.testTag("ocr_lang_${lang.name}")
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${lang.displayName} (${lang.scriptName})", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "Local Model: ${itemStatus.status.label}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (itemStatus.status == OcrModelStatus.BUNDLED_READY) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = itemStatus.status.label,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Manual Scan Trigger + Progress
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isScanning) {
                                OutlinedButton(
                                    onClick = onCancelOcr,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cancel OCR Scan (${scanProgress.first}/${scanProgress.second})")
                                }
                            } else {
                                Button(
                                    onClick = onRunOcr,
                                    enabled = imageCount > 0,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("run_ocr_scan_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (imageCount > 0) "Scan $imageCount Pages Offline"
                                        else "Import Images First to Scan"
                                    )
                                }
                            }
                        }

                        if (isScanning && scanProgress.second > 0) {
                            val frac = (scanProgress.first.toFloat() / scanProgress.second.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            // Extracted Text Panel
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Extracted Text Panel", style = MaterialTheme.typography.titleMedium)
                            StatusPill(text = "${ocrResults.size} pages")
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            placeholder = { Text("Search extracted text...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ocr_search_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val all = ocrResults.joinToString("\n\n") { page ->
                                        "=== ${page.pageLabel} (${page.displayName}) ===\n${page.extractedText}"
                                    }
                                    copyToClipboard("Manhwa OCR Text", all)
                                },
                                enabled = ocrResults.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("copy_all_ocr_button")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy All")
                            }
                            Button(
                                onClick = { txtSaveLauncher.launch("Manhwa_OCR_Text.txt") },
                                enabled = ocrResults.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_txt_ocr_button")
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export .TXT")
                            }
                        }
                    }
                }
            }

            if (ocrResults.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceHigh),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No extracted text yet. Tap 'Scan Pages Offline' above or export your PDF with OCR enabled to populate page-by-page dialogue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(ocrResults, key = { it.imageId }) { page ->
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
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    StatusPill(
                                        text = page.pageLabel,
                                        containerColor = VioletPrimary.copy(alpha = 0.2f),
                                        contentColor = VioletPrimary
                                    )
                                    Text(
                                        text = page.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { copyToClipboard(page.pageLabel, page.extractedText) },
                                    enabled = page.extractedText.isNotBlank(),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy ${page.pageLabel} text",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = page.extractedText.ifBlank { "(No text detected on this page)" },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = if (page.extractedText.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
