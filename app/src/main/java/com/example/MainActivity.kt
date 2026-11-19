package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.ManhwaViewModel
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.ExportCompleteScreen
import com.example.ui.screens.ExportHistoryScreen
import com.example.ui.screens.ExportProgressScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageArrangementScreen
import com.example.ui.screens.ImageImportScreen
import com.example.ui.screens.OcrSettingsScreen
import com.example.ui.screens.PdfPreviewScreen
import com.example.ui.screens.PdfSettingsScreen
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.CharcoalSurfaceLow
import com.example.ui.theme.ManhwaPdfMakerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ManhwaPdfMakerTheme {
                ManhwaPdfMakerApp()
            }
        }
    }
}

private data class TopLevelDestination(
    val route: AppRoute,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private val topLevelDestinations = listOf(
    TopLevelDestination(AppRoute.Home, "Home", Icons.Default.Home, "nav_home"),
    TopLevelDestination(AppRoute.ImageImport, "Import", Icons.Default.AddPhotoAlternate, "nav_import"),
    TopLevelDestination(AppRoute.PdfSettings, "Settings", Icons.Default.Tune, "nav_settings"),
    TopLevelDestination(AppRoute.OcrSettings, "OCR", Icons.Default.DocumentScanner, "nav_ocr"),
    TopLevelDestination(AppRoute.ExportProgress, "Export", Icons.Default.PictureAsPdf, "nav_export")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManhwaPdfMakerApp(
    viewModel: ManhwaViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val context = LocalContext.current

    val images by viewModel.images.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val pdfConfig by viewModel.pdfConfig.collectAsStateWithLifecycle()
    val ocrSettings by viewModel.ocrSettings.collectAsStateWithLifecycle()
    val languageStatuses by viewModel.languageStatuses.collectAsStateWithLifecycle()
    val filteredOcrResults by viewModel.filteredOcrResults.collectAsStateWithLifecycle()
    val ocrSearchQuery by viewModel.ocrSearchQuery.collectAsStateWithLifecycle()
    val isOcrScanning by viewModel.isStandaloneOcrRunning.collectAsStateWithLifecycle()
    val ocrScanProgress by viewModel.standaloneOcrProgress.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val history by viewModel.exportHistory.collectAsStateWithLifecycle()
    val interruptedBanner by viewModel.interruptedExportBanner.collectAsStateWithLifecycle()

    val isTopLevel = currentDest?.let { dest ->
        topLevelDestinations.any { dest.hasRoute(it.route::class) }
    } ?: true

    val screenTitle = when {
        currentDest?.hasRoute<AppRoute.Home>() == true -> "Manhwa PDF Maker"
        currentDest?.hasRoute<AppRoute.ImageImport>() == true -> "Image Import"
        currentDest?.hasRoute<AppRoute.ImageArrangement>() == true -> "Arrange Pages"
        currentDest?.hasRoute<AppRoute.PdfSettings>() == true -> "PDF & Numbering"
        currentDest?.hasRoute<AppRoute.OcrSettings>() == true -> "OCR Settings"
        currentDest?.hasRoute<AppRoute.PdfPreview>() == true -> "PDF Preview"
        currentDest?.hasRoute<AppRoute.ExportProgress>() == true -> "Export PDF"
        currentDest?.hasRoute<AppRoute.ExportComplete>() == true -> "Export Complete"
        currentDest?.hasRoute<AppRoute.ExportHistory>() == true -> "Export History"
        else -> "Manhwa PDF Maker"
    }

    fun navigateTopLevel(route: AppRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = CharcoalDeep,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = screenTitle,
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        if (!isTopLevel) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Navigate back"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = CharcoalSurfaceLow
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(containerColor = CharcoalSurfaceLow) {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentDest?.hasRoute(dest.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = CharcoalSurfaceLow,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentDest?.hasRoute(dest.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Home,
                        enterTransition = { fadeIn(animationSpec = tween(220)) },
                        exitTransition = { fadeOut(animationSpec = tween(180)) },
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 920.dp)
                    ) {
                        composable<AppRoute.Home> {
                            HomeScreen(
                                images = images,
                                pdfConfig = pdfConfig,
                                ocrSettings = ocrSettings,
                                history = history,
                                interruptedBanner = interruptedBanner,
                                onDismissInterruptedBanner = viewModel::dismissInterruptedBanner,
                                onNavigateImport = { navigateTopLevel(AppRoute.ImageImport) },
                                onNavigateArrange = { navController.navigate(AppRoute.ImageArrangement) },
                                onNavigateSettings = { navigateTopLevel(AppRoute.PdfSettings) },
                                onNavigateOcr = { navigateTopLevel(AppRoute.OcrSettings) },
                                onNavigatePreview = { navController.navigate(AppRoute.PdfPreview) },
                                onNavigateExport = { navigateTopLevel(AppRoute.ExportProgress) },
                                onNavigateHistory = { navController.navigate(AppRoute.ExportHistory) }
                            )
                        }

                        composable<AppRoute.ImageImport> {
                            ImageImportScreen(
                                images = images,
                                isImporting = isImporting,
                                pageNumberConfig = pdfConfig.pageNumberConfig,
                                onImportUris = viewModel::importImages,
                                onRemoveImage = viewModel::removeImage,
                                onClearAll = viewModel::clearAllImages,
                                onNavigateArrange = { navController.navigate(AppRoute.ImageArrangement) },
                                onNavigatePreview = { navController.navigate(AppRoute.PdfPreview) }
                            )
                        }

                        composable<AppRoute.ImageArrangement> {
                            ImageArrangementScreen(
                                images = images,
                                pageNumberConfig = pdfConfig.pageNumberConfig,
                                onMoveImage = viewModel::moveImage,
                                onRotateImage = viewModel::rotateImage,
                                onRemoveImage = viewModel::removeImage,
                                onSortByName = viewModel::sortImagesByFilename,
                                onReverseOrder = viewModel::reverseImageOrder,
                                onNavigateImport = { navigateTopLevel(AppRoute.ImageImport) },
                                onNavigatePreview = { navController.navigate(AppRoute.PdfPreview) }
                            )
                        }

                        composable<AppRoute.PdfSettings> {
                            PdfSettingsScreen(
                                config = pdfConfig,
                                sampleImage = images.firstOrNull(),
                                onUpdateConfig = viewModel::updatePdfConfig
                            )
                        }

                        composable<AppRoute.OcrSettings> {
                            OcrSettingsScreen(
                                ocrSettings = ocrSettings,
                                languageStatuses = languageStatuses,
                                ocrResults = filteredOcrResults,
                                searchQuery = ocrSearchQuery,
                                isScanning = isOcrScanning,
                                scanProgress = ocrScanProgress,
                                imageCount = images.size,
                                onUpdateSettings = viewModel::updateOcrSettings,
                                onRunOcr = viewModel::runStandaloneOcr,
                                onCancelOcr = viewModel::cancelStandaloneOcr,
                                onSearchQueryChange = viewModel::updateOcrSearchQuery,
                                onExportTxtUri = { uri ->
                                    viewModel.exportOcrTextToUri(uri) { ok ->
                                        val msg = if (ok) "Exported OCR text to .TXT" else "Failed to export .TXT"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        composable<AppRoute.PdfPreview> {
                            PdfPreviewScreen(
                                images = images,
                                pdfConfig = pdfConfig,
                                onRotateImage = viewModel::rotateImage,
                                onDeleteImage = viewModel::removeImage,
                                onBackToEditing = { navController.popBackStack() },
                                onProceedToExport = { navigateTopLevel(AppRoute.ExportProgress) }
                            )
                        }

                        composable<AppRoute.ExportProgress> {
                            ExportProgressScreen(
                                images = images,
                                pdfConfig = pdfConfig,
                                ocrSettings = ocrSettings,
                                exportState = exportState,
                                onFilenameChange = { name ->
                                    viewModel.updatePdfConfig { it.copy(outputFileName = name) }
                                },
                                onStartExportUri = viewModel::startPdfExport,
                                onCancelExport = viewModel::cancelPdfExport,
                                onResetExportState = viewModel::resetExportState,
                                onNavigateComplete = {
                                    navController.navigate(AppRoute.ExportComplete) {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateBack = { navigateTopLevel(AppRoute.Home) }
                            )
                        }

                        composable<AppRoute.ExportComplete> {
                            ExportCompleteScreen(
                                exportState = exportState,
                                pdfConfig = pdfConfig,
                                onOpenOcrResults = { navigateTopLevel(AppRoute.OcrSettings) },
                                onDoneHome = {
                                    viewModel.resetExportState()
                                    navigateTopLevel(AppRoute.Home)
                                }
                            )
                        }

                        composable<AppRoute.ExportHistory> {
                            ExportHistoryScreen(
                                history = history,
                                onDeleteEntry = viewModel::deleteHistoryEntry,
                                onClearAll = viewModel::clearHistory
                            )
                        }
                    }
                }
            }
        }
    }
}
