package com.example.ui.screens

import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ImportedImage
import com.example.model.PageNumberConfig
import com.example.ui.components.StatusPill
import com.example.ui.components.ThumbnailImage
import com.example.ui.components.formatFileSize
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.CharcoalOutline
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CharcoalSurfaceHigh
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.VioletPrimary

@Composable
fun ImageImportScreen(
    images: List<ImportedImage>,
    isImporting: Boolean,
    pageNumberConfig: PageNumberConfig,
    onImportUris: (List<Uri>) -> Unit,
    onRemoveImage: (String) -> Unit,
    onClearAll: () -> Unit,
    onNavigateArrange: () -> Unit,
    onNavigatePreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxPickerItems = remember {
        val sysLimit = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            runCatching { MediaStore.getPickImagesMaxLimit() }.getOrDefault(200)
        } else 200
        sysLimit.coerceIn(2, 200)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxPickerItems)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportUris(uris)
        }
    }

    val safDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportUris(uris)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Import Action Header Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            border = BorderStroke(1.dp, CharcoalOutline),
            modifier = Modifier.fillMaxWidth()
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
                    Column {
                        Text(
                            text = "Import Manhwa Pages",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Supports JPG, JPEG, PNG, WEBP • Lossless pipeline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill(
                        text = "${images.size} selected",
                        containerColor = if (images.isNotEmpty()) VioletPrimary.copy(alpha = 0.2f) else CharcoalSurfaceHigh,
                        contentColor = if (images.isNotEmpty()) VioletPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_photo_picker_button")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (images.isEmpty()) "Photo Picker" else "Add Photos")
                    }

                    OutlinedButton(
                        onClick = {
                            safDocumentLauncher.launch(arrayOf("image/*"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_saf_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Files (200+)")
                    }
                }

                if (isImporting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Inspecting image dimensions & EXIF metadata without loading full bitmaps...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (images.isNotEmpty()) {
            // Breakdown Bar
            val totalSize = images.sumOf { it.fileSizeBytes }
            val pngCount = images.count { it.formatLabel == "PNG" }
            val jpgCount = images.count { it.formatLabel == "JPG" }
            val webpCount = images.count { it.formatLabel == "WEBP" }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(text = formatFileSize(totalSize))
                    if (pngCount > 0) StatusPill(text = "$pngCount PNG")
                    if (jpgCount > 0) StatusPill(text = "$jpgCount JPG")
                    if (webpCount > 0) StatusPill(text = "$webpCount WEBP")
                }
                TextButton(
                    onClick = onClearAll,
                    modifier = Modifier.testTag("clear_all_images_button")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = ErrorCoral, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", color = ErrorCoral)
                }
            }

            // Thumbnail Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                itemsIndexed(images, key = { _, img -> img.id }) { index, img ->
                    val dims = img.effectiveDimensions
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                        border = BorderStroke(1.dp, CharcoalOutline),
                        modifier = Modifier.testTag("imported_thumb_${index}")
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.72f)
                                    .background(CharcoalDeep)
                            ) {
                                ThumbnailImage(
                                    image = img,
                                    contentScale = ContentScale.Crop,
                                    maxDecodePx = 300,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Page number badge
                                Surface(
                                    color = CharcoalDeep.copy(alpha = 0.86f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = pageNumberConfig.formatLabel(index),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VioletPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                // Format badge
                                Surface(
                                    color = CharcoalDeep.copy(alpha = 0.86f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = img.formatLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }

                                // Delete button (48dp touch target)
                                IconButton(
                                    onClick = { onRemoveImage(img.id) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(48.dp)
                                        .testTag("remove_image_$index")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(CharcoalDeep.copy(alpha = 0.85f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove ${img.displayName}",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = img.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${dims.first}×${dims.second}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigatePreview,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("import_to_preview_button")
                ) {
                    Text("Preview PDF")
                }
                Button(
                    onClick = onNavigateArrange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("import_to_arrange_button")
                ) {
                    Text("Arrange Order")
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            // Empty State
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(VioletPrimary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = VioletPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = "No Pages Imported Yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Select up to 200+ manhwa chapters at once using Photo Picker or Files (SAF). Only lightweight thumbnails are rendered while full quality stays untouched.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
