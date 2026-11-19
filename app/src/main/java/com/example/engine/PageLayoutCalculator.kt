package com.example.engine

import com.example.model.HorizontalAlign
import com.example.model.ImportedImage
import com.example.model.PageBackground
import com.example.model.PageNumberConfig
import com.example.model.PageNumberPosition
import com.example.model.PageOrientation
import com.example.model.PdfExportConfig
import com.example.model.PdfPageSizePreset
import com.example.model.VerticalAlign
import kotlin.math.max
import kotlin.math.min

data class PdfMatrix(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val e: Float,
    val f: Float
) {
    fun toPdfCommand(): String =
        "${fmt(a)} ${fmt(b)} ${fmt(c)} ${fmt(d)} ${fmt(e)} ${fmt(f)} cm"

    companion object {
        private fun fmt(v: Float): String = String.format(java.util.Locale.US, "%.4f", v)
    }
}

data class RectFBox(
    val left: Float,
    val bottom: Float,
    val width: Float,
    val height: Float
) {
    val right: Float get() = left + width
    val top: Float get() = bottom + height
}

data class PageNumberLayout(
    val text: String,
    val badgeRect: RectFBox,
    val textX: Float,
    val textY: Float,
    val fontSizePt: Float
)

data class PageLayoutResult(
    val pageWidthPt: Float,
    val pageHeightPt: Float,
    val imageDrawRect: RectFBox,
    val imageMatrix: PdfMatrix,
    val backgroundColor: PageBackground,
    val pageNumberLayout: PageNumberLayout?
)

object PageLayoutCalculator {

    private const val MAX_PDF_MEDIABOX_PT = 14000f
    private const val MM_TO_PT = 72f / 25.4f
    private const val A4_SHORT_PT = 595.28f
    private const val A4_LONG_PT = 841.89f

    fun calculatePageLayout(
        image: ImportedImage,
        zeroBasedPageIndex: Int,
        config: PdfExportConfig
    ): PageLayoutResult {
        val (visWidthPx, visHeightPx) = image.effectiveDimensions
        val safeW = max(1, visWidthPx).toFloat()
        val safeH = max(1, visHeightPx).toFloat()

        // 1. Determine base MediaBox size in PDF points (72 pt/in)
        val (rawPageW, rawPageH) = when (config.pageSizePreset) {
            PdfPageSizePreset.ORIGINAL_IMAGE_SIZE -> {
                // Use 1px = 0.75pt (96 DPI equivalent) or 1:1 clamped safely below PDF 1.4 14,400pt limit.
                // Notice image XObject keeps 100% original pixel dimensions regardless of MediaBox pt scale.
                val maxSide = max(safeW, safeH)
                val scale = if (maxSide > MAX_PDF_MEDIABOX_PT) MAX_PDF_MEDIABOX_PT / maxSide else 1f
                (safeW * scale) to (safeH * scale)
            }
            PdfPageSizePreset.A4_PORTRAIT -> A4_SHORT_PT to A4_LONG_PT
            PdfPageSizePreset.A4_LANDSCAPE -> A4_LONG_PT to A4_SHORT_PT
            PdfPageSizePreset.FIT_TO_PAGE -> {
                if (config.orientation == PageOrientation.LANDSCAPE) {
                    A4_LONG_PT to A4_SHORT_PT
                } else {
                    A4_SHORT_PT to A4_LONG_PT
                }
            }
            PdfPageSizePreset.CUSTOM -> {
                val wPt = (config.customWidthMm.coerceIn(30f, 3000f)) * MM_TO_PT
                val hPt = (config.customHeightMm.coerceIn(30f, 3000f)) * MM_TO_PT
                if (config.orientation == PageOrientation.LANDSCAPE && wPt < hPt) {
                    hPt to wPt
                } else if (config.orientation == PageOrientation.PORTRAIT && wPt > hPt) {
                    hPt to wPt
                } else {
                    wPt to hPt
                }
            }
        }

        val numCfg = config.pageNumberConfig
        val pageLabel = numCfg.formatLabel(zeroBasedPageIndex)

        // Scale typography & margins harmoniously if page is very large (e.g. 2400px wide original)
        val scaleFactor = (min(rawPageW, rawPageH) / 595f).coerceIn(0.65f, 3.0f)
        val fontSizePt = (numCfg.fontSizeSp * scaleFactor).coerceIn(8f, 64f)
        val marginPt = (numCfg.marginDp * scaleFactor).coerceIn(4f, 120f)
        val badgePadX = fontSizePt * 0.65f
        val badgePadY = fontSizePt * 0.35f
        val estCharWidth = fontSizePt * 0.58f
        val textWidth = max(fontSizePt * 1.8f, pageLabel.length * estCharWidth)
        val badgeW = textWidth + badgePadX * 2f
        val badgeH = fontSizePt + badgePadY * 2f

        var pageW = rawPageW
        var pageH = rawPageH

        // 2. Reserve safe band when page numbering is enabled and reserveSafeBand is true
        // For ORIGINAL_IMAGE_SIZE, expand the canvas slightly rather than shrinking the image,
        // and for fixed page presets, reserve the band inside the page so art is never overlapped!
        var contentLeft = 0f
        var contentBottom = 0f
        var contentWidth = pageW
        var contentHeight = pageH

        if (numCfg.enabled && numCfg.reserveSafeBand) {
            val bandThickness = when (numCfg.position) {
                PageNumberPosition.TOP, PageNumberPosition.BOTTOM -> badgeH + marginPt * 1.4f
                PageNumberPosition.LEFT, PageNumberPosition.RIGHT -> badgeW + marginPt * 1.4f
            }
            if (config.pageSizePreset == PdfPageSizePreset.ORIGINAL_IMAGE_SIZE) {
                when (numCfg.position) {
                    PageNumberPosition.TOP -> {
                        val newH = min(MAX_PDF_MEDIABOX_PT, pageH + bandThickness)
                        val added = newH - pageH
                        pageH = newH
                        contentHeight = pageH - added
                    }
                    PageNumberPosition.BOTTOM -> {
                        val newH = min(MAX_PDF_MEDIABOX_PT, pageH + bandThickness)
                        val added = newH - pageH
                        pageH = newH
                        contentBottom = added
                        contentHeight = pageH - added
                    }
                    PageNumberPosition.LEFT -> {
                        val newW = min(MAX_PDF_MEDIABOX_PT, pageW + bandThickness)
                        val added = newW - pageW
                        pageW = newW
                        contentLeft = added
                        contentWidth = pageW - added
                    }
                    PageNumberPosition.RIGHT -> {
                        val newW = min(MAX_PDF_MEDIABOX_PT, pageW + bandThickness)
                        val added = newW - pageW
                        pageW = newW
                        contentWidth = pageW - added
                    }
                }
            } else {
                val insetPadding = if (config.pageSizePreset == PdfPageSizePreset.FIT_TO_PAGE) 8f else 12f
                contentLeft = insetPadding
                contentBottom = insetPadding
                contentWidth = max(20f, pageW - insetPadding * 2f)
                contentHeight = max(20f, pageH - insetPadding * 2f)

                when (numCfg.position) {
                    PageNumberPosition.TOP -> {
                        contentHeight = max(20f, contentHeight - bandThickness)
                    }
                    PageNumberPosition.BOTTOM -> {
                        contentBottom += bandThickness
                        contentHeight = max(20f, contentHeight - bandThickness)
                    }
                    PageNumberPosition.LEFT -> {
                        contentLeft += bandThickness
                        contentWidth = max(20f, contentWidth - bandThickness)
                    }
                    PageNumberPosition.RIGHT -> {
                        contentWidth = max(20f, contentWidth - bandThickness)
                    }
                }
            }
        } else if (config.pageSizePreset != PdfPageSizePreset.ORIGINAL_IMAGE_SIZE) {
            val inset = if (config.pageSizePreset == PdfPageSizePreset.FIT_TO_PAGE) 4f else 12f
            contentLeft = inset
            contentBottom = inset
            contentWidth = max(20f, pageW - inset * 2f)
            contentHeight = max(20f, pageH - inset * 2f)
        }

        // 3. Fit image inside content rect preserving aspect ratio with zero cropping
        val imgAspect = safeW / safeH
        val boxAspect = contentWidth / contentHeight
        val drawW: Float
        val drawH: Float
        if (imgAspect > boxAspect) {
            drawW = contentWidth
            drawH = contentWidth / imgAspect
        } else {
            drawH = contentHeight
            drawW = contentHeight * imgAspect
        }
        val drawX = contentLeft + (contentWidth - drawW) / 2f
        val drawY = contentBottom + (contentHeight - drawH) / 2f
        val imageRect = RectFBox(drawX, drawY, drawW, drawH)

        // 4. Compute exact PDF transformation matrix for EXIF orientation + manual rotation
        val imageMatrix = computeImageTransformMatrix(
            exifOrientation = image.exifOrientation,
            manualRotationDegrees = image.manualRotationDegrees,
            drawRect = imageRect
        )

        // 5. Compute Page Number badge & text layout
        val numberLayout = if (numCfg.enabled) {
            computePageNumberLayout(
                label = pageLabel,
                pageW = pageW,
                pageH = pageH,
                badgeW = badgeW,
                badgeH = badgeH,
                badgePadX = badgePadX,
                badgePadY = badgePadY,
                fontSizePt = fontSizePt,
                marginPt = marginPt,
                config = numCfg
            )
        } else null

        return PageLayoutResult(
            pageWidthPt = pageW,
            pageHeightPt = pageH,
            imageDrawRect = imageRect,
            imageMatrix = imageMatrix,
            backgroundColor = config.background,
            pageNumberLayout = numberLayout
        )
    }

    private fun computePageNumberLayout(
        label: String,
        pageW: Float,
        pageH: Float,
        badgeW: Float,
        badgeH: Float,
        badgePadX: Float,
        badgePadY: Float,
        fontSizePt: Float,
        marginPt: Float,
        config: PageNumberConfig
    ): PageNumberLayout {
        val safeMarginX = min(marginPt, max(2f, (pageW - badgeW) / 2f))
        val safeMarginY = min(marginPt, max(2f, (pageH - badgeH) / 2f))

        val badgeX: Float
        val badgeY: Float

        when (config.position) {
            PageNumberPosition.TOP -> {
                badgeY = pageH - safeMarginY - badgeH
                badgeX = when (config.horizontalAlign) {
                    HorizontalAlign.LEFT -> safeMarginX
                    HorizontalAlign.CENTER -> (pageW - badgeW) / 2f
                    HorizontalAlign.RIGHT -> pageW - safeMarginX - badgeW
                }
            }
            PageNumberPosition.BOTTOM -> {
                badgeY = safeMarginY
                badgeX = when (config.horizontalAlign) {
                    HorizontalAlign.LEFT -> safeMarginX
                    HorizontalAlign.CENTER -> (pageW - badgeW) / 2f
                    HorizontalAlign.RIGHT -> pageW - safeMarginX - badgeW
                }
            }
            PageNumberPosition.LEFT -> {
                badgeX = safeMarginX
                badgeY = when (config.verticalAlign) {
                    VerticalAlign.TOP -> pageH - safeMarginY - badgeH
                    VerticalAlign.CENTER -> (pageH - badgeH) / 2f
                    VerticalAlign.BOTTOM -> safeMarginY
                }
            }
            PageNumberPosition.RIGHT -> {
                badgeX = pageW - safeMarginX - badgeW
                badgeY = when (config.verticalAlign) {
                    VerticalAlign.TOP -> pageH - safeMarginY - badgeH
                    VerticalAlign.CENTER -> (pageH - badgeH) / 2f
                    VerticalAlign.BOTTOM -> safeMarginY
                }
            }
        }

        val textX = badgeX + badgePadX
        val textY = badgeY + badgePadY + fontSizePt * 0.18f
        return PageNumberLayout(
            text = label,
            badgeRect = RectFBox(badgeX, badgeY, badgeW, badgeH),
            textX = textX,
            textY = textY,
            fontSizePt = fontSizePt
        )
    }

    /**
     * Composes EXIF orientation and manual clockwise rotation in normalized PDF unit-square coordinates
     * and maps into [drawRect] in page coordinates.
     *
     * Raw unit square in PDF Image XObject:
     *   u in [0,1] (left -> right), v in [0,1] (bottom -> top).
     */
    fun computeImageTransformMatrix(
        exifOrientation: Int,
        manualRotationDegrees: Int,
        drawRect: RectFBox
    ): PdfMatrix {
        // Represent 2D affine matrix in normalized [0,1]^2 space as (a, b, c, d, e, f)
        // where x' = a*x + c*y + e, y' = b*x + d*y + f
        val exifMatrix = when (exifOrientation) {
            2 -> floatArrayOf(-1f, 0f, 0f, 1f, 1f, 0f)   // Flip horizontal
            3 -> floatArrayOf(-1f, 0f, 0f, -1f, 1f, 1f)  // Rotate 180
            4 -> floatArrayOf(1f, 0f, 0f, -1f, 0f, 1f)   // Flip vertical
            5 -> floatArrayOf(0f, -1f, -1f, 0f, 1f, 1f)  // Transpose (flip H + rot 90 CW -> top-left <-> bottom-right? Wait: Exif 5 swaps x & y around top-left/bottom-right axis!)
            6 -> floatArrayOf(0f, -1f, 1f, 0f, 0f, 1f)   // Rotate 90 CW
            7 -> floatArrayOf(0f, 1f, 1f, 0f, 0f, 0f)    // Transverse (flip H + rot 270 CW)
            8 -> floatArrayOf(0f, 1f, -1f, 0f, 1f, 0f)   // Rotate 270 CW
            else -> floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f) // Normal (1 or 0)
        }

        val normalizedManual = ((manualRotationDegrees % 360) + 360) % 360
        val manualMatrix = when (normalizedManual) {
            90 -> floatArrayOf(0f, -1f, 1f, 0f, 0f, 1f)
            180 -> floatArrayOf(-1f, 0f, 0f, -1f, 1f, 1f)
            270 -> floatArrayOf(0f, 1f, -1f, 0f, 1f, 0f)
            else -> floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
        }

        // First apply EXIF, then manual rotation: M = manual * exif
        val combined = multiplyAffine(manualMatrix, exifMatrix)

        // Finally scale by (drawW, drawH) and translate by (drawX, drawY)
        val scaleTranslate = floatArrayOf(
            drawRect.width, 0f,
            0f, drawRect.height,
            drawRect.left, drawRect.bottom
        )
        val finalMat = multiplyAffine(scaleTranslate, combined)
        return PdfMatrix(
            a = finalMat[0],
            b = finalMat[1],
            c = finalMat[2],
            d = finalMat[3],
            e = finalMat[4],
            f = finalMat[5]
        )
    }

    private fun multiplyAffine(m2: FloatArray, m1: FloatArray): FloatArray {
        // m = [a, b, c, d, e, f]
        val a = m2[0] * m1[0] + m2[2] * m1[1]
        val b = m2[1] * m1[0] + m2[3] * m1[1]
        val c = m2[0] * m1[2] + m2[2] * m1[3]
        val d = m2[1] * m1[2] + m2[3] * m1[3]
        val e = m2[0] * m1[4] + m2[2] * m1[5] + m2[4]
        val f = m2[1] * m1[4] + m2[3] * m1[5] + m2[5]
        return floatArrayOf(a, b, c, d, e, f)
    }
}
