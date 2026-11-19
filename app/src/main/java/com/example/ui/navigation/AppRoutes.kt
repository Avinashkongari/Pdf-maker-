package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data object ImageImport : AppRoute

    @Serializable
    data object ImageArrangement : AppRoute

    @Serializable
    data object PdfSettings : AppRoute

    @Serializable
    data object OcrSettings : AppRoute

    @Serializable
    data object PdfPreview : AppRoute

    @Serializable
    data object ExportProgress : AppRoute

    @Serializable
    data object ExportComplete : AppRoute

    @Serializable
    data object ExportHistory : AppRoute
}
