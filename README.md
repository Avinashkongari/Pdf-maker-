# Manhwa PDF Maker

A 100% offline, lossless Android studio application built with Kotlin, Jetpack Compose, Material 3, Room, DataStore, WorkManager, and on-device ML Kit OCR for converting high-resolution manhwa/webtoon chapters (1–200+ pages) into single, numbered, verified PDFs.

- **Package Name (`applicationId`)**: `com.axion.manhwapdfmaker`
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 36
- **Architecture**: MVVM + Repository + Streaming PDF Engine + WorkManager

## Key Capabilities

1. **Multi-Image Import (100–200+ Pages)**
   - Supports Android Photo Picker (`PickMultipleVisualMedia`) and Storage Access Framework (`OpenMultipleDocuments`) with zero broad storage permissions.
   - Supports JPG, JPEG, PNG, and WEBP.
   - Memory-safe thumbnail rendering (`Coil` downsampled previews) while inspecting original bounds & EXIF orientation via `inJustDecodeBounds = true`.
   - Drag-and-drop reordering, sort by filename, reverse order, 90° rotation, and individual deletion.

2. **Original Image Quality & Streaming Lossless PDF Engine**
   - **Original Quality (Default)**: Raw baseline/progressive JPEG passthrough (`/DCTDecode`) with zero recompression; PNG & WEBP encoded losslessly via `/FlateDecode` (never converted to JPEG).
   - **Optimized Lossless**: Losslessly strips non-essential JPEG APP metadata, detects grayscale PNG/WEBP (`R == G == B`) for `/DeviceGray`, and applies PNG Up predictor (`/Predictor 12`) with maximum Deflate compression.
   - Processes one page at a time, immediately recycling temporary bitmaps, and streams directly to disk with byte-offset `xref` generation.
   - Preserves EXIF orientation and manual rotation via exact PDF `cm` transformation matrices.

3. **Automatic Page Numbering & Page Presets**
   - Presets: `Original Image Size`, `A4 Portrait`, `A4 Landscape`, `Fit to Page`, and `Custom Dimensions` (mm).
   - White or Black page background, Portrait/Landscape support, and real-time **Live Page Preview**.
   - Customizable numbering (`PG`, `PAGE`, `CHAPTER`), starting index, font size, font style, text/badge colors, edge position (`Top`, `Bottom`, `Left`, `Right`), horizontal/vertical alignments, margins, and **Safe Margin Band Reservation** so numbers never cover artwork.

4. **On-Device Offline OCR (`ENABLE OCR: ON/OFF`)**
   - Uses bundled Google ML Kit Text Recognition models for **English (Latin)**, **Hindi (Devanagari)**, **Japanese**, and **Chinese** — works offline with zero cloud uploads.
   - Never alters the original images or PDF output.
   - Searchable extracted text panel with page-by-page references, Copy, and Export as `.TXT`.

5. **Resilient Background Export & Validation**
   - Runs via `WorkManager` (`PdfExportWorker`) with progress percentage, current page indicator, cancellation, insufficient-storage protection, and interrupted-export recovery.
   - Validates every PDF (`%PDF-` header, `startxref`, `%%EOF`, page count, and `PdfRenderer` reopening) before copying to the user's SAF destination and recording in local Room **Export History**.

## Build Instructions

### Prerequisites
- JDK 17+
- Android SDK (API 36)

### Build Debug APK
```bash
gradle :app:assembleDebug
```
Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### Build Release APK (when upload keystore env vars are configured)
```bash
export KEYSTORE_PATH=/path/to/upload.jks
export STORE_PASSWORD=...
export KEY_PASSWORD=...
gradle :app:assembleRelease
```

### Run Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```
