package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import kotlinx.coroutines.launch

@Composable
fun ImageArrangementScreen(
    images: List<ImportedImage>,
    pageNumberConfig: PageNumberConfig,
    onMoveImage: (from: Int, to: Int) -> Unit,
    onRotateImage: (String) -> Unit,
    onRemoveImage: (String) -> Unit,
    onSortByName: () -> Unit,
    onReverseOrder: () -> Unit,
    onNavigateImport: () -> Unit,
    onNavigatePreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        Card(
            shape = RoundedCornerShape(18.dp),
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
                    Column {
                        Text(
                            text = "Arrange Page Order",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Long-press & drag any card, or use arrows",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill(text = "${images.size} pages")
                }

                if (images.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSortByName,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sort_filename_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sort Name")
                        }
                        OutlinedButton(
                            onClick = onReverseOrder,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reverse_order_button")
                        ) {
                            Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reverse")
                        }
                    }
                }
            }
        }

        if (images.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("No images to arrange", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onNavigateImport) {
                        Text("Go to Image Import")
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                itemsIndexed(images, key = { _, img -> img.id }) { index, img ->
                    val isDragging = draggedItemId == img.id
                    val dims = img.effectiveDimensions

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDragging) CharcoalSurfaceHigh else CharcoalSurface
                        ),
                        border = BorderStroke(
                            width = if (isDragging) 2.dp else 1.dp,
                            color = if (isDragging) VioletPrimary else CharcoalOutline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragDeltaY else 0f
                                scaleX = if (isDragging) 1.02f else 1f
                                scaleY = if (isDragging) 1.02f else 1f
                            }
                            .pointerInput(img.id, images.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedItemId = img.id
                                        dragDeltaY = 0f
                                    },
                                    onDragEnd = {
                                        draggedItemId = null
                                        dragDeltaY = 0f
                                    },
                                    onDragCancel = {
                                        draggedItemId = null
                                        dragDeltaY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragDeltaY += dragAmount.y

                                        val currentIndex = images.indexOfFirst { it.id == img.id }
                                        if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                                        val currentInfo = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.key == img.id } ?: return@detectDragGesturesAfterLongPress

                                        val itemCenterY = currentInfo.offset + currentInfo.size / 2f + dragDeltaY

                                        val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { other ->
                                            other.key != img.id &&
                                                itemCenterY >= other.offset &&
                                                itemCenterY <= (other.offset + other.size)
                                        }
                                        if (targetItem != null) {
                                            val targetIndex = targetItem.index
                                            if (targetIndex != currentIndex && targetIndex in images.indices) {
                                                val offsetDelta = currentInfo.offset - targetItem.offset
                                                onMoveImage(currentIndex, targetIndex)
                                                dragDeltaY += offsetDelta
                                            }
                                        }

                                        // Edge auto-scroll during drag
                                        val viewportH = listState.layoutInfo.viewportEndOffset
                                        if (itemCenterY > viewportH - 120f) {
                                            scope.launch { listState.scrollBy(18f) }
                                        } else if (itemCenterY < 120f) {
                                            scope.launch { listState.scrollBy(-18f) }
                                        }
                                    }
                                )
                            }
                            .testTag("arrange_item_$index")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Long press to drag ${img.displayName}",
                                tint = if (isDragging) VioletPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .size(width = 52.dp, height = 72.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CharcoalDeep)
                            ) {
                                ThumbnailImage(
                                    image = img,
                                    contentScale = ContentScale.Crop,
                                    maxDecodePx = 180,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPill(
                                        text = pageNumberConfig.formatLabel(index),
                                        containerColor = VioletPrimary.copy(alpha = 0.2f),
                                        contentColor = VioletPrimary
                                    )
                                    StatusPill(text = img.formatLabel)
                                }
                                Text(
                                    text = img.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${dims.first}×${dims.second}px • ${formatFileSize(img.fileSizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row {
                                    IconButton(
                                        onClick = { if (index > 0) onMoveImage(index, index - 1) },
                                        enabled = index > 0,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("move_up_$index")
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            contentDescription = "Move page up",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { if (index < images.lastIndex) onMoveImage(index, index + 1) },
                                        enabled = index < images.lastIndex,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("move_down_$index")
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowDownward,
                                            contentDescription = "Move page down",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Row {
                                    IconButton(
                                        onClick = { onRotateImage(img.id) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("rotate_item_$index")
                                    ) {
                                        Icon(
                                            Icons.Default.RotateRight,
                                            contentDescription = "Rotate 90 degrees",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveImage(img.id) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("delete_arrange_$index")
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove page",
                                            tint = ErrorCoral,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onNavigatePreview,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("arrange_to_preview_button")
            ) {
                Icon(Icons.Default.Preview, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Preview ${images.size} Pages")
            }
        }
    }
}
