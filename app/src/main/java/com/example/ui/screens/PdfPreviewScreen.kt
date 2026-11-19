package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ImportedImage
import com.example.model.PageBackground
import com.example.model.PageNumberPosition
import com.example.model.PdfExportConfig
import com.example.ui.components.PageNumberBadgeOverlay
import com.example.ui.components.StatusPill
import com.example.ui.components.ThumbnailImage
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.CharcoalOutline
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.VioletPrimary
import kotlinx.coroutines.launch

@Composable
fun PdfPreviewScreen(
    images: List<ImportedImage>,
    pdfConfig: PdfExportConfig,
    onRotateImage: (String) -> Unit,
    onDeleteImage: (String) -> Unit,
    onBackToEditing: () -> Unit,
    onProceedToExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (images.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("No pages to preview", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onBackToEditing) {
                    Text("Return to Import")
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { images.size })
    val scope = rememberCoroutineScope()
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Reset zoom smoothly when swiping to a different page
    LaunchedEffect(pagerState.currentPage) {
        zoomScale = 1f
        panOffset = Offset.Zero
    }

    val currentIndex = pagerState.currentPage.coerceIn(0, images.lastIndex)
    val currentImage = images[currentIndex]
    val numCfg = pdfConfig.pageNumberConfig
    val pageBg = if (pdfConfig.background == PageBackground.WHITE) Color.White else CharcoalDeep

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Inspector Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                StatusPill(
                    text = "${currentIndex + 1} / ${images.size}",
                    containerColor = VioletPrimary.copy(alpha = 0.2f),
                    contentColor = VioletPrimary
                )
                Text(
                    text = currentImage.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        zoomScale = (zoomScale - 0.5f).coerceIn(1f, 4f)
                        if (zoomScale == 1f) panOffset = Offset.Zero
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
                }
                IconButton(
                    onClick = { zoomScale = (zoomScale + 0.5f).coerceIn(1f, 4f) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
                }
                IconButton(
                    onClick = { onRotateImage(currentImage.id) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("preview_rotate_button")
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate page 90 degrees")
                }
                IconButton(
                    onClick = { onDeleteImage(currentImage.id) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("preview_delete_button")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete page", tint = ErrorCoral)
                }
            }
        }

        // Interactive Pager Canvas (Swipe between pages, pinch/double-tap zoom)
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = zoomScale <= 1.05f,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { pageIdx ->
            val pageImg = images[pageIdx]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CharcoalSurface)
                    .border(1.dp, CharcoalOutline, RoundedCornerShape(16.dp))
                    .pointerInput(pageIdx) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (zoomScale > 1.2f) {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    zoomScale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(pageIdx, zoomScale) {
                        if (zoomScale > 1.05f) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextZoom = (zoomScale * zoom).coerceIn(1f, 4f)
                                zoomScale = nextZoom
                                panOffset = if (nextZoom <= 1.02f) Offset.Zero else panOffset + pan
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(pageBg)
                ) {
                    val padTop = if (numCfg.enabled && numCfg.reserveSafeBand && numCfg.position == PageNumberPosition.TOP) 34.dp else 4.dp
                    val padBottom = if (numCfg.enabled && numCfg.reserveSafeBand && numCfg.position == PageNumberPosition.BOTTOM) 34.dp else 4.dp
                    val padStart = if (numCfg.enabled && numCfg.reserveSafeBand && numCfg.position == PageNumberPosition.LEFT) 56.dp else 4.dp
                    val padEnd = if (numCfg.enabled && numCfg.reserveSafeBand && numCfg.position == PageNumberPosition.RIGHT) 56.dp else 4.dp

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = padStart, top = padTop, end = padEnd, bottom = padBottom),
                        contentAlignment = Alignment.Center
                    ) {
                        ThumbnailImage(
                            image = pageImg,
                            contentScale = ContentScale.Fit,
                            maxDecodePx = 1440,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    PageNumberBadgeOverlay(
                        label = numCfg.formatLabel(pageIdx),
                        config = pdfConfig
                    )
                }

                if (zoomScale > 1.05f) {
                    Surface(
                        color = CharcoalDeep.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = String.format(java.util.Locale.US, "%.1fx (double-tap reset)", zoomScale),
                            style = MaterialTheme.typography.labelSmall,
                            color = VioletPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Quick Scrubber for 100–200 page chapters
        if (images.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Page", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = currentIndex.toFloat(),
                    onValueChange = { pos ->
                        val target = pos.toInt().coerceIn(0, images.lastIndex)
                        if (target != pagerState.currentPage) {
                            scope.launch { pagerState.scrollToPage(target) }
                        }
                    },
                    valueRange = 0f..images.lastIndex.toFloat(),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = numCfg.formatLabel(currentIndex),
                    style = MaterialTheme.typography.labelMedium,
                    color = VioletPrimary
                )
            }
        }

        // Bottom Actions: Return to Editing or Export
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            border = BorderStroke(1.dp, CharcoalOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onBackToEditing,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preview_back_edit_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Order")
                }
                Button(
                    onClick = onProceedToExport,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("preview_export_button")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export PDF")
                }
            }
        }
    }
}
