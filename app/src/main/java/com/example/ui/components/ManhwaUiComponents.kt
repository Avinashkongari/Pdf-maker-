package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.example.model.HorizontalAlign
import com.example.model.ImportedImage
import com.example.model.PageBackground
import com.example.model.PageNumberFontStyle
import com.example.model.PageNumberPosition
import com.example.model.PdfExportConfig
import com.example.model.PdfPageSizePreset
import com.example.model.VerticalAlign
import com.example.ui.theme.CharcoalOutline
import com.example.ui.theme.CharcoalSurfaceHigh
import com.example.ui.theme.VioletPrimary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

fun openPdfExternally(context: Context, uriString: String) {
    val uri = Uri.parse(uriString)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Open PDF"))
    }.onFailure {
        Toast.makeText(context, "No PDF viewer installed or URI no longer accessible.", Toast.LENGTH_LONG).show()
    }
}

fun sharePdfExternally(context: Context, uriString: String, title: String) {
    val uri = Uri.parse(uriString)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.onFailure {
        Toast.makeText(context, "Unable to share PDF file.", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun ThumbnailImage(
    image: ImportedImage,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxDecodePx: Int = 360
) {
    val context = LocalContext.current
    val modelData: Any = image.cachedPath?.let { File(it) }?.takeIf { it.exists() }
        ?: Uri.parse(image.uriString)

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(modelData)
            .size(maxDecodePx, maxDecodePx * 2)
            .scale(Scale.FIT)
            .crossfade(150)
            .build(),
        contentDescription = image.displayName,
        contentScale = contentScale,
        modifier = modifier.graphicsLayer {
            rotationZ = image.manualRotationDegrees.toFloat()
        }
    )
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VioletPrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = VioletPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color = CharcoalSurfaceHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(1.dp, CharcoalOutline.copy(alpha = 0.7f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PageNumberBadgeOverlay(
    label: String,
    config: PdfExportConfig,
    modifier: Modifier = Modifier
) {
    val numCfg = config.pageNumberConfig
    if (!numCfg.enabled) return

    val fontWeight = when (numCfg.fontStyle) {
        PageNumberFontStyle.BOLD, PageNumberFontStyle.BOLD_ITALIC -> FontWeight.Bold
        else -> FontWeight.Normal
    }
    val fontStyle = when (numCfg.fontStyle) {
        PageNumberFontStyle.ITALIC, PageNumberFontStyle.BOLD_ITALIC -> FontStyle.Italic
        else -> FontStyle.Normal
    }

    val alignment = when (numCfg.position) {
        PageNumberPosition.TOP -> when (numCfg.horizontalAlign) {
            HorizontalAlign.LEFT -> Alignment.TopStart
            HorizontalAlign.CENTER -> Alignment.TopCenter
            HorizontalAlign.RIGHT -> Alignment.TopEnd
        }
        PageNumberPosition.BOTTOM -> when (numCfg.horizontalAlign) {
            HorizontalAlign.LEFT -> Alignment.BottomStart
            HorizontalAlign.CENTER -> Alignment.BottomCenter
            HorizontalAlign.RIGHT -> Alignment.BottomEnd
        }
        PageNumberPosition.LEFT -> when (numCfg.verticalAlign) {
            VerticalAlign.TOP -> Alignment.TopStart
            VerticalAlign.CENTER -> Alignment.CenterStart
            VerticalAlign.BOTTOM -> Alignment.BottomStart
        }
        PageNumberPosition.RIGHT -> when (numCfg.verticalAlign) {
            VerticalAlign.TOP -> Alignment.TopEnd
            VerticalAlign.CENTER -> Alignment.CenterEnd
            VerticalAlign.BOTTOM -> Alignment.BottomEnd
        }
    }

    val margin = (numCfg.marginDp * 0.65f).coerceIn(4f, 28f).dp
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(margin),
        contentAlignment = alignment
    ) {
        Surface(
            color = Color(numCfg.backgroundColorHex),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = label,
                color = Color(numCfg.textColorHex),
                fontSize = (numCfg.fontSizeSp * 0.9f).coerceIn(8f, 22f).sp,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
fun LivePagePreviewCard(
    config: PdfExportConfig,
    sampleImage: ImportedImage?,
    pageIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    val pageRatio = when (config.pageSizePreset) {
        PdfPageSizePreset.ORIGINAL_IMAGE_SIZE -> {
            val dims = sampleImage?.effectiveDimensions
            if (dims != null && dims.second > 0) {
                (dims.first.toFloat() / dims.second.toFloat()).coerceIn(0.45f, 1.8f)
            } else 0.707f
        }
        PdfPageSizePreset.A4_PORTRAIT -> 595f / 842f
        PdfPageSizePreset.A4_LANDSCAPE -> 842f / 595f
        PdfPageSizePreset.FIT_TO_PAGE -> {
            if (config.orientation == com.example.model.PageOrientation.LANDSCAPE) 842f / 595f else 595f / 842f
        }
        PdfPageSizePreset.CUSTOM -> {
            val w = config.customWidthMm.coerceAtLeast(30f)
            val h = config.customHeightMm.coerceAtLeast(30f)
            val ratio = if (config.orientation == com.example.model.PageOrientation.LANDSCAPE && w < h) {
                h / w
            } else if (config.orientation == com.example.model.PageOrientation.PORTRAIT && w > h) {
                h / w
            } else w / h
            ratio.coerceIn(0.4f, 2.0f)
        }
    }

    val bgColor = if (config.background == PageBackground.WHITE) Color.White else Color(0xFF0E0D12)
    val numCfg = config.pageNumberConfig

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val maxPreviewHeight = 250.dp
        val previewBoxMod = if (pageRatio >= 1f) {
            Modifier
                .fillMaxWidth(0.86f)
                .aspectRatio(pageRatio)
        } else {
            Modifier
                .height(maxPreviewHeight)
                .aspectRatio(pageRatio)
        }

        Box(
            modifier = previewBoxMod
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .border(1.dp, CharcoalOutline, RoundedCornerShape(8.dp))
        ) {
            // Compute inner padding to represent reserved safe band visually
            val bandTop = if (numCfg.enabled && numCfg.reserveSafeBand && numCfg.position == PageNumberPosition.TOP) 28.dp else 4.dp
            val bandBottom = if (numCfg.enabled && numCfg.reserveSafeBand && numCfg.position == PageNumberPosition.BOTTOM) 28.dp else 4.dp
            val bandStart = if (numCfg.enabled && numCfg.reserveSafeBand && numCfg.position == PageNumberPosition.LEFT) 44.dp else 4.dp
            val bandEnd = if (numCfg.enabled && numCfg.reserveSafeBand && numCfg.position == PageNumberPosition.RIGHT) 44.dp else 4.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = bandStart, top = bandTop, end = bandEnd, bottom = bandBottom),
                contentAlignment = Alignment.Center
            ) {
                if (sampleImage != null) {
                    ThumbnailImage(
                        image = sampleImage,
                        contentScale = ContentScale.Fit,
                        maxDecodePx = 512,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Stylized Manhwa Sample Panel Placeholder
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.5.dp, VioletPrimary.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                            .background(Color(0xFF1F1C2C).copy(alpha = 0.85f))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(VioletPrimary.copy(alpha = 0.16f), RoundedCornerShape(3.dp))
                        )
                        Row(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(Color(0xFF2B273D), RoundedCornerShape(3.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(VioletPrimary.copy(alpha = 0.24f), RoundedCornerShape(3.dp))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(0.9f)
                                .fillMaxWidth()
                                .background(Color(0xFF2B273D), RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            PageNumberBadgeOverlay(
                label = numCfg.formatLabel(pageIndex),
                config = config
            )
        }
    }
}
