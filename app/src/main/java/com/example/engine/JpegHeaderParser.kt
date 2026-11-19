package com.example.engine

import java.io.ByteArrayOutputStream

data class JpegInfo(
    val width: Int,
    val height: Int,
    val components: Int,
    val isStandardBaselineOrProgressive: Boolean
)

object JpegHeaderParser {

    /**
     * Inspects JPEG bytes for SOF0 (baseline) or SOF2 (progressive) frame markers
     * and checks component count (1 = Grayscale, 3 = YCbCr/RGB).
     */
    fun inspect(bytes: ByteArray): JpegInfo? {
        if (bytes.size < 4 || (bytes[0].toInt() and 0xFF) != 0xFF || (bytes[1].toInt() and 0xFF) != 0xD8) {
            return null
        }
        var offset = 2
        var hasAdobeRgb = false
        while (offset + 3 < bytes.size) {
            if ((bytes[offset].toInt() and 0xFF) != 0xFF) {
                offset++
                continue
            }
            while (offset < bytes.size && (bytes[offset].toInt() and 0xFF) == 0xFF) {
                offset++
            }
            if (offset >= bytes.size) break
            val marker = bytes[offset].toInt() and 0xFF
            offset++

            if (marker == 0xD8 || marker == 0x01 || (marker in 0xD0..0xD7)) continue
            if (marker == 0xD9 || marker == 0xDA) break // EOI or SOS

            if (offset + 1 >= bytes.size) break
            val segLen = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
            if (segLen < 2 || offset + segLen > bytes.size) break

            if (marker == 0xEE && segLen >= 14) {
                // APP14 Adobe marker check
                if (bytes[offset + 2] == 'A'.code.toByte() &&
                    bytes[offset + 3] == 'd'.code.toByte() &&
                    bytes[offset + 4] == 'o'.code.toByte() &&
                    bytes[offset + 5] == 'b'.code.toByte() &&
                    bytes[offset + 6] == 'e'.code.toByte()
                ) {
                    val transform = bytes[offset + 13].toInt() and 0xFF
                    if (transform == 0) hasAdobeRgb = true
                }
            }

            if (marker == 0xC0 || marker == 0xC1 || marker == 0xC2) {
                if (segLen >= 8) {
                    val precision = bytes[offset + 2].toInt() and 0xFF
                    val height = ((bytes[offset + 3].toInt() and 0xFF) shl 8) or (bytes[offset + 4].toInt() and 0xFF)
                    val width = ((bytes[offset + 5].toInt() and 0xFF) shl 8) or (bytes[offset + 6].toInt() and 0xFF)
                    val components = bytes[offset + 7].toInt() and 0xFF
                    val supported = precision == 8 && (components == 1 || components == 3) && !hasAdobeRgb
                    return JpegInfo(width, height, components, supported)
                }
            }
            offset += segLen
        }
        return null
    }

    /**
     * Losslessly strips APP1..APP13, APP15, and COM metadata segments (EXIF, XMP, thumbnails, comments)
     * without re-encoding or touching any Huffman/DCT image scan data.
     */
    fun stripMetadataLosslessly(bytes: ByteArray): ByteArray {
        if (bytes.size < 4 || (bytes[0].toInt() and 0xFF) != 0xFF || (bytes[1].toInt() and 0xFF) != 0xD8) {
            return bytes
        }
        val out = ByteArrayOutputStream(bytes.size)
        out.write(0xFF)
        out.write(0xD8)

        var offset = 2
        while (offset < bytes.size) {
            if ((bytes[offset].toInt() and 0xFF) != 0xFF) {
                // Unexpected byte outside marker; fallback to original untouched bytes
                return bytes
            }
            while (offset < bytes.size && (bytes[offset].toInt() and 0xFF) == 0xFF) {
                offset++
            }
            if (offset >= bytes.size) break
            val marker = bytes[offset].toInt() and 0xFF
            offset++

            if (marker == 0xDA) {
                // Start of Scan: copy remaining bytes verbatim through EOI
                out.write(0xFF)
                out.write(marker)
                out.write(bytes, offset, bytes.size - offset)
                return out.toByteArray()
            }
            if (marker == 0xD9) {
                out.write(0xFF)
                out.write(0xD9)
                return out.toByteArray()
            }
            if (marker == 0x01 || (marker in 0xD0..0xD7)) {
                out.write(0xFF)
                out.write(marker)
                continue
            }

            if (offset + 1 >= bytes.size) return bytes
            val segLen = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
            if (segLen < 2 || offset + segLen > bytes.size) return bytes

            // Strip APP1..APP13, APP15, and COM (0xFE). Keep APP0 (JFIF) and APP14 (Adobe).
            val shouldStrip = (marker in 0xE1..0xED) || marker == 0xEF || marker == 0xFE
            if (!shouldStrip) {
                out.write(0xFF)
                out.write(marker)
                out.write(bytes, offset, segLen)
            }
            offset += segLen
        }
        return bytes
    }
}
