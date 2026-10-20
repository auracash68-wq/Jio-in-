package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.AppScreen
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.ImageProcessor
import com.example.viewmodel.DocSaathiViewModel

@Composable
fun HomeScreen(
    viewModel: DocSaathiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recentDocs by viewModel.recentDocuments.collectAsState()
    val activePages by viewModel.activePages.collectAsState()
    val ocrResultText by viewModel.ocrResultText.collectAsState()
    val ocrProgress by viewModel.ocrProgress.collectAsState()
    val previewingDoc by viewModel.previewingDocument.collectAsState()

    // On-Demand Camera Permission Launcher (triggered ONLY when user taps Scan Document)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.navigateTo(AppScreen.CAMERA_SCANNER)
    }

    // Real File & Image Picker supporting .jpg, .jpeg, .png, .webp, and .pdf
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val bitmaps = uris.flatMap { uri ->
                ImageProcessor.loadBitmapsFromUri(context, uri)
            }
            if (bitmaps.isNotEmpty()) {
                viewModel.addImportedBitmaps(bitmaps)
            } else {
                viewModel.showMessage("Could not decode selected file.")
            }
        }
    }

    // Direct OCR Image/PDF Picker when user taps "Offline OCR Text" with no active scan
    val ocrFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val bmp = ImageProcessor.loadBitmapFromUri(context, uri)
            if (bmp != null) {
                viewModel.performOcrOnBitmap(bmp)
            } else {
                viewModel.showMessage("Unable to load image for OCR.")
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "PulseAnimation")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CameraPulse"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PageBackground,
        topBar = {
            DocSaathiTealTopBar(
                title = "Docs Scanner",
                showOfflineBadge = true,
                onSettingsClick = { viewModel.navigateTo(AppScreen.SETTINGS) }
            )
        },
        bottomBar = {
            DocSaathiBottomNavBar(
                currentScreen = AppScreen.HOME,
                onNavigate = { screen -> viewModel.setBottomNavDestination(screen) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("home_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 22.dp)
        ) {
            // 1. Hero Scan Document Card (2026-2028 Flagship Design)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 10.dp,
                            shape = RoundedCornerShape(22.dp),
                            spotColor = PrimaryTeal.copy(alpha = 0.35f)
                        )
                        .clip(RoundedCornerShape(22.dp))
                        .background(BrandHeroGradient)
                        .clickable {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPerm) {
                                viewModel.navigateTo(AppScreen.CAMERA_SCANNER)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                        .testTag("main_scan_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 22.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Camera",
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Scan Document",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "Turn paper into PDF • 300 DPI Clarity",
                            color = Color.White.copy(alpha = 0.90f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 2. Import from Gallery Action (Supports .jpg, .jpeg, .png, .webp, .pdf)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SoftMintGradient)
                        .border(1.dp, MintBorder.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .clickable {
                            galleryLauncher.launch(
                                arrayOf("image/jpeg", "image/png", "image/webp", "image/*", "application/pdf")
                            )
                        }
                        .testTag("import_gallery_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Gallery",
                            tint = DarkTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Import from Gallery",
                            color = DarkTeal,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 3. Upgraded 2x3 Quick-Action Utility Grid
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: Job & Exam Resizer | Exact KB Compressor | Offline OCR Text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionCard(
                            icon = Icons.Outlined.VerifiedUser,
                            title = "Job & Exam\nResizer",
                            badgeText = "BD & IN",
                            modifier = Modifier.weight(1f),
                            testTag = "action_exam_resizer",
                            onClick = { viewModel.navigateTo(AppScreen.EXAM_RESIZER) }
                        )

                        QuickActionCard(
                            icon = Icons.Outlined.Compress,
                            title = "Exact KB\nCompressor",
                            badgeText = "20-500KB",
                            modifier = Modifier.weight(1f),
                            testTag = "action_kb_compressor",
                            onClick = { viewModel.navigateTo(AppScreen.KB_COMPRESSOR) }
                        )

                        QuickActionCard(
                            icon = Icons.Outlined.DocumentScanner,
                            title = "Offline OCR\nText",
                            badgeText = "4 Scripts",
                            modifier = Modifier.weight(1f),
                            testTag = "action_ocr_text",
                            onClick = {
                                if (activePages.isNotEmpty()) {
                                    viewModel.performOcrOnCurrentDocument(context)
                                } else {
                                    ocrFilePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                }
                            }
                        )
                    }

                    // Row 2: ID Card & Passport | Multi-page PDF | Share PDF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionCard(
                            icon = Icons.Outlined.Badge,
                            title = "ID Card &\nPassport",
                            badgeText = "A4 Merge",
                            modifier = Modifier.weight(1f),
                            testTag = "action_id_card",
                            onClick = { viewModel.navigateTo(AppScreen.ID_CARD_SCANNER) }
                        )

                        QuickActionCard(
                            icon = Icons.Outlined.Layers,
                            title = "Multi-page\nPDF",
                            badgeText = "Crop/Edit",
                            modifier = Modifier.weight(1f),
                            testTag = "action_multi_page",
                            onClick = {
                                if (activePages.isNotEmpty()) {
                                    viewModel.navigateTo(AppScreen.CROP_EDIT)
                                } else {
                                    galleryLauncher.launch(
                                        arrayOf("image/jpeg", "image/png", "image/webp", "image/*", "application/pdf")
                                    )
                                }
                            }
                        )

                        QuickActionCard(
                            icon = Icons.Outlined.Share,
                            title = "Share\nPDF",
                            badgeText = "Export",
                            modifier = Modifier.weight(1f),
                            testTag = "action_share_pdf",
                            onClick = { viewModel.shareCurrentOrDocument(context) }
                        )
                    }
                }
            }

            // 4. Recent Documents Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Documents",
                        color = PrimaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = LightTeal,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable { viewModel.navigateTo(AppScreen.FILES) }
                            .testTag("see_all_button")
                    ) {
                        Text(
                            text = "See all",
                            color = PrimaryTeal,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Recent Documents List Card OR Clean Empty State UI
            item {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (recentDocs.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(LightTeal),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No documents yet. Scan a document or import a file to get started.",
                                color = SecondaryText,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("home_empty_state_text")
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            recentDocs.forEachIndexed { index, doc ->
                                PdfFileItemRow(
                                    document = doc,
                                    onClick = { viewModel.openDocument(context, doc) },
                                    onShare = { viewModel.shareCurrentOrDocument(context, doc) },
                                    onDelete = { viewModel.deleteDocument(doc.id) },
                                    onRename = { newName -> viewModel.renameDocument(doc.id, newName) }
                                )
                                if (index < recentDocs.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 72.dp, end = 14.dp),
                                        color = SubtleBorder,
                                        thickness = 0.8.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Privacy Information Card
            item {
                Surface(
                    color = LightTeal,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PrimaryTeal.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Shield",
                                tint = PrimaryTeal,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Your files stay on your phone",
                                color = DarkTeal,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "No cloud. No tracking. No account.",
                                color = SecondaryText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // In-App PDF Reader Modal
    previewingDoc?.let { doc ->
        InAppPdfViewerModal(
            document = doc,
            onDismiss = { viewModel.closeDocumentPreview() },
            onShare = { viewModel.shareCurrentOrDocument(context, doc) },
            onOpenExternal = { viewModel.openDocumentInExternalApp(context, doc) },
            onRunOcr = { viewModel.performOcrOnCurrentDocument(context, doc) }
        )
    }

    // OCR Studio Modal
    ocrResultText?.let { text ->
        OcrResultModal(
            text = text,
            ocrProgress = ocrProgress,
            onSaveAsTxt = { edited -> viewModel.saveOcrTextAsTxt(context, edited) },
            onSaveAsPdf = { edited -> viewModel.saveOcrTextAsPdf(context, edited) },
            onDismiss = { viewModel.clearOcrResult() }
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    badgeText: String,
    modifier: Modifier = Modifier,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = SurfaceWhite,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        modifier = modifier
            .height(104.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(LightTeal)
                    .padding(horizontal = 6.dp, vertical = 1.5.dp)
            ) {
                Text(
                    text = badgeText,
                    color = PrimaryTeal,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LightTeal),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PrimaryTeal,
                    modifier = Modifier.size(19.dp)
                )
            }

            Text(
                text = title,
                color = PrimaryText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 13.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
