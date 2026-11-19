package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.HorizontalAlign
import com.example.model.ImageHandlingMode
import com.example.model.ImportedImage
import com.example.model.PageBackground
import com.example.model.PageNumberFontStyle
import com.example.model.PageNumberPosition
import com.example.model.PageNumberPrefix
import com.example.model.PageOrientation
import com.example.model.PdfExportConfig
import com.example.model.PdfPageSizePreset
import com.example.model.VerticalAlign
import com.example.ui.components.LivePagePreviewCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.CharcoalOutline
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CharcoalSurfaceHigh
import com.example.ui.theme.VioletPrimary
import kotlin.math.roundToInt

private val textSwatchColors = listOf(
    0xFFFFFFFFL to "White",
    0xFF0E0D12L to "Charcoal",
    0xFFA875FFL to "Purple",
    0xFFFBBF24L to "Amber",
    0xFF5EEAD4L to "Teal"
)

private val badgeBgSwatchColors = listOf(
    0xCC1B1924L to "Dark Pill",
    0x00000000L to "None",
    0xE6FFFFFFL to "White Pill",
    0xCC472585L to "Violet Pill",
    0xE6000000L to "Black Pill"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfSettingsScreen(
    config: PdfExportConfig,
    sampleImage: ImportedImage?,
    onUpdateConfig: ((PdfExportConfig) -> PdfExportConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("pdf_settings_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Live Page Preview
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, CharcoalOutline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Live Page Preview",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LivePagePreviewCard(
                        config = config,
                        sampleImage = sampleImage,
                        pageIndex = 0
                    )
                }
            }
        }

        // Image Quality Mode
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
                    SectionHeader(
                        title = "Image Quality Mode",
                        subtitle = "Lossless preservation — zero resolution downgrade",
                        icon = Icons.Default.HighQuality
                    )

                    ImageHandlingMode.entries.forEach { mode ->
                        val selected = config.imageHandlingMode == mode
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) VioletPrimary.copy(alpha = 0.14f) else CharcoalSurfaceHigh
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (selected) VioletPrimary else CharcoalOutline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onUpdateConfig { it.copy(imageHandlingMode = mode) } }
                                .testTag("quality_mode_${mode.name}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { onUpdateConfig { it.copy(imageHandlingMode = mode) } }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (mode == ImageHandlingMode.ORIGINAL_QUALITY) "${mode.title} (Default)" else mode.title,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = mode.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Page Dimensions & Background
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
                        title = "PDF Page Layout",
                        subtitle = "Single image per page • Aspect ratio locked",
                        icon = Icons.Default.AspectRatio
                    )

                    Text("Page Size Preset", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PdfPageSizePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = config.pageSizePreset == preset,
                                onClick = { onUpdateConfig { it.copy(pageSizePreset = preset) } },
                                label = { Text(preset.label) },
                                modifier = Modifier.testTag("page_size_${preset.name}")
                            )
                        }
                    }

                    if (config.pageSizePreset == PdfPageSizePreset.FIT_TO_PAGE || config.pageSizePreset == PdfPageSizePreset.CUSTOM) {
                        Text("Orientation", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PageOrientation.entries.forEach { orient ->
                                FilterChip(
                                    selected = config.orientation == orient,
                                    onClick = { onUpdateConfig { it.copy(orientation = orient) } },
                                    label = { Text(orient.label) }
                                )
                            }
                        }
                    }

                    if (config.pageSizePreset == PdfPageSizePreset.CUSTOM) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = config.customWidthMm.roundToInt().toString(),
                                onValueChange = { txt ->
                                    val v = txt.toFloatOrNull()
                                    if (v != null) onUpdateConfig { it.copy(customWidthMm = v.coerceIn(30f, 3000f)) }
                                },
                                label = { Text("Width (mm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = config.customHeightMm.roundToInt().toString(),
                                onValueChange = { txt ->
                                    val v = txt.toFloatOrNull()
                                    if (v != null) onUpdateConfig { it.copy(customHeightMm = v.coerceIn(30f, 3000f)) }
                                },
                                label = { Text("Height (mm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("Page Canvas Background", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PageBackground.entries.forEach { bg ->
                            FilterChip(
                                selected = config.background == bg,
                                onClick = { onUpdateConfig { it.copy(background = bg) } },
                                label = { Text(bg.label) },
                                modifier = Modifier.testTag("page_bg_${bg.name}")
                            )
                        }
                    }
                }
            }
        }

        // Automatic Page Numbering
        item {
            val numCfg = config.pageNumberConfig
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                border = BorderStroke(1.dp, CharcoalOutline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(
                            title = "Automatic Page Numbering",
                            subtitle = if (numCfg.enabled) "Format: ${numCfg.formatLabel(0)}, ${numCfg.formatLabel(1)}..." else "Disabled",
                            icon = Icons.Default.FormatListNumbered,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = numCfg.enabled,
                            onCheckedChange = { enabled ->
                                onUpdateConfig { it.copy(pageNumberConfig = numCfg.copy(enabled = enabled)) }
                            },
                            modifier = Modifier.testTag("page_number_switch")
                        )
                    }

                    if (numCfg.enabled) {
                        Text("Prefix", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PageNumberPrefix.entries.forEach { prefix ->
                                FilterChip(
                                    selected = numCfg.prefix == prefix,
                                    onClick = {
                                        onUpdateConfig { it.copy(pageNumberConfig = numCfg.copy(prefix = prefix)) }
                                    },
                                    label = { Text(prefix.token) },
                                    modifier = Modifier.testTag("prefix_${prefix.name}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Starting Number", style = MaterialTheme.typography.labelLarge)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        onUpdateConfig {
                                            it.copy(pageNumberConfig = numCfg.copy(startNumber = (numCfg.startNumber - 1).coerceAtLeast(0)))
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease start number")
                                }
                                Text(
                                    text = numCfg.startNumber.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                IconButton(
                                    onClick = {
                                        onUpdateConfig {
                                            it.copy(pageNumberConfig = numCfg.copy(startNumber = numCfg.startNumber + 1))
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase start number")
                                }
                            }
                        }

                        // Font Size
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Font Size", style = MaterialTheme.typography.labelLarge)
                                Text("${numCfg.fontSizeSp.roundToInt()} sp", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = numCfg.fontSizeSp,
                                onValueChange = { size ->
                                    onUpdateConfig { it.copy(pageNumberConfig = numCfg.copy(fontSizeSp = size)) }
                                },
                                valueRange = 8f..24f
                            )
                        }

                        // Font Style
                        Text("Font Style", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PageNumberFontStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = numCfg.fontStyle == style,
                                    onClick = {
                                        onUpdateConfig { it.copy(pageNumberConfig = numCfg.copy(fontStyle = style)) }
                                    },
                                    label = { Text(style.label) }
                                )
                            }
                        }

                        // Text & Badge Colors
                        Text("Text Color", style = MaterialTheme.typography.labelLarge)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            textSwatchColors.forEach { (hex, name) ->
                                val selected = numCfg.textColorHex == hex
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) VioletPrimary else CharcoalOutline,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onUpdateConfig { it.copy(pageNumberConfig = numCfg.copy(textColorHex = hex)) }
                                        }
                                )
                            }
                        }

                        Text("Badge Background", style = MaterialTheme.typography.labelLarge)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            badgeBgSwatchColors.forEach { (hex, name) ->
                                FilterChip(
                                    selected = numCfg.backgroundColorHex == hex,
                                    onClick = {
                                        onUpdateConfig { it.copy(pageNumberConfig = numCfg.copy(backgroundColorHex = hex)) }
                                    },
                                    label = { Text(name) }
                                )
                            }
                        }

                        // Position & Alignment
                        Text("Position Edge", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PageNumberPosition.entries.forEach { pos ->
                                FilterChip(
                                    selected = numCfg.position == pos,
                                    onClick = {
                                        onUpdateConfig { it.copy(pageNumberConfig = numCfg.copy(position = pos)) }
                                    },
                                    label = { Text(pos.label) }
                                )
                            }
                        }

                        if (numCfg.position == PageNumberPosition.TOP || numCfg.position == PageNumberPosition.BOTTOM) {
                            Text("Horizontal Alignment", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HorizontalAlign.entries.forEach { align ->
                                    FilterChip(
                                        selected = numCfg.horizontalAlign == align,
                                        onClick = {
                                            onUpdateConfig { it.copy(pageNumberConfig = numCfg.copy(horizontalAlign = align)) }
                                        },
                                        label = { Text(align.label) }
                                    )
                                }
                            }
                        } else {
                            Text("Vertical Alignment", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                VerticalAlign.entries.forEach { align ->
                                    FilterChip(
                                        selected = numCfg.verticalAlign == align,
                                        onClick = {
                                            onUpdateConfig { it.copy(pageNumberConfig = numCfg.copy(verticalAlign = align)) }
                                        },
                                        label = { Text(align.label) }
                                    )
                                }
                            }
                        }

                        // Margin
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Edge Margin", style = MaterialTheme.typography.labelLarge)
                                Text("${numCfg.marginDp.roundToInt()} pt", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = numCfg.marginDp,
                                onValueChange = { m ->
                                    onUpdateConfig { it.copy(pageNumberConfig = numCfg.copy(marginDp = m)) }
                                },
                                valueRange = 4f..36f
                            )
                        }

                        // Safe Band Reservation Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Reserve Safe Margin Band", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Ensures page numbers never cover manhwa artwork or speech bubbles.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = numCfg.reserveSafeBand,
                                onCheckedChange = { safe ->
                                    onUpdateConfig { it.copy(pageNumberConfig = numCfg.copy(reserveSafeBand = safe)) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
