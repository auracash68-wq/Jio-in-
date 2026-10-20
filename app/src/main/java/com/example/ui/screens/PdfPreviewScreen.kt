package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppScreen
import com.example.data.PdfQuality
import com.example.ui.components.DocSaathiWhiteTopBar
import com.example.ui.components.OcrResultModal
import com.example.ui.theme.*
import com.example.util.ImageProcessor
import com.example.util.PdfGenerator
import com.example.viewmodel.DocSaathiViewModel
import java.io.ByteArrayOutputStream

@Composable
fun PdfPreviewScreen(
    viewModel: DocSaathiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val activePages by viewModel.activePages.collectAsState()
    val selectedPageIndex by viewModel.selectedPageIndex.collectAsState()
    val fileName by viewModel.fileNameInput.collectAsState()
    val isOcrEnabled by viewModel.isOcrEnabled.collectAsState()
    val pdfQuality by viewModel.pdfQuality.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val ocrResultText by viewModel.ocrResultText.collectAsState()
    val ocrProgress by viewModel.ocrProgress.collectAsState()

    var showQualityDialog by remember { mutableStateOf(false) }

    val importAdditionalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val bitmaps = uris.flatMap { uri ->
                ImageProcessor.loadBitmapsFromUri(context, uri)
            }
            if (bitmaps.isNotEmpty()) {
                viewModel.addImportedBitmaps(bitmaps, appendToExisting = true)
            }
        }
    }

    // Android Storage Access Framework (SAF) launcher to export PDF to device Downloads/Documents
    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            viewModel.exportPdfToExternalUri(context, uri)
        }
    }

    val currentPage = activePages.getOrNull(selectedPageIndex) ?: activePages.firstOrNull()
    val pageBitmap = remember(
        currentPage?.id,
        selectedPageIndex,
        currentPage?.rotationDegrees,
        currentPage?.filter,
        currentPage?.cropTopLeftX,
        currentPage?.cropTopLeftY,
        currentPage?.cropTopRightX,
        currentPage?.cropTopRightY,
        currentPage?.cropBottomLeftX,
        currentPage?.cropBottomLeftY,
        currentPage?.cropBottomRightX,
        currentPage?.cropBottomRightY
    ) {
        currentPage?.let { ImageProcessor.processPage(it) }
    }

    // Calculate real byte size across all active pages for the selected PdfQuality
    val realMeasuredSizeString = remember(activePages, pdfQuality) {
        if (activePages.isEmpty()) {
            "0 KB"
        } else {
            val scaleFactor = when (pdfQuality) {
                PdfQuality.HIGH -> 1.0f
                PdfQuality.MEDIUM -> 0.78f
                PdfQuality.LOW -> 0.55f
            }
            var totalBytes = 1400L // PDF header/xref overhead
            for (page in activePages) {
                val processed = ImageProcessor.processPage(page)
                val targetW = (processed.width * scaleFactor).toInt().coerceAtLeast(300)
                val targetH = (processed.height * scaleFactor).toInt().coerceAtLeast(420)
                val scaled = if (targetW != processed.width || targetH != processed.height) {
                    Bitmap.createScaledBitmap(processed, targetW, targetH, true)
                } else {
                    processed
                }
                val stream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, pdfQuality.jpegQuality, stream)
                totalBytes += stream.size().toLong() + 650L
            }
            PdfGenerator.formatFileSize(totalBytes)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PageBackground,
        topBar = {
            DocSaathiWhiteTopBar(
                title = "PDF Preview",
                onBackClick = { viewModel.navigateBack() },
                actions = {
                    if (activePages.isNotEmpty()) {
                        Surface(
                            color = LightTeal,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clickable {
                                    val safeName = if (fileName.endsWith(".pdf", true)) fileName else "$fileName.pdf"
                                    exportPdfLauncher.launch(safeName)
                                }
                                .padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Export",
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Export",
                                    color = PrimaryTeal,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = SurfaceWhite,
                shadowElevation = 10.dp,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, SubtleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. OCR Button
                    OutlinedButton(
                        onClick = { viewModel.performOcrOnCurrentDocument(context) },
                        enabled = activePages.isNotEmpty() && !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.3.dp, PrimaryTeal),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTeal),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pdf_ocr_action")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("OCR", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    // 2. Save PDF Button (Filled Teal)
                    Button(
                        onClick = {
                            viewModel.saveCurrentPdf(context) {
                                viewModel.navigateTo(AppScreen.FILES)
                            }
                        },
                        enabled = activePages.isNotEmpty() && !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        modifier = Modifier
                            .weight(1.35f)
                            .height(48.dp)
                            .testTag("pdf_save_action")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.SaveAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save PDF", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 3. Share Button
                    OutlinedButton(
                        onClick = { viewModel.shareCurrentOrDocument(context) },
                        enabled = activePages.isNotEmpty() && !isProcessing,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.3.dp, PrimaryTeal),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTeal),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("pdf_share_action")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Share", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 18.dp)
        ) {
            // 1. PDF Document Sheet Preview
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(248.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(6.dp),
                        shadowElevation = 6.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f / 1.414f)
                    ) {
                        if (pageBitmap != null) {
                            Image(
                                bitmap = pageBitmap.asImageBitmap(),
                                contentDescription = "Document Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No page available",
                                    color = SecondaryText,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // 2. Horizontal Thumbnails Strip
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(activePages) { index, page ->
                        val isSelected = index == selectedPageIndex
                        val thumb = remember(
                            page.id,
                            page.rotationDegrees,
                            page.filter,
                            page.cropTopLeftX,
                            page.cropTopLeftY,
                            page.cropTopRightX,
                            page.cropTopRightY,
                            page.cropBottomLeftX,
                            page.cropBottomLeftY,
                            page.cropBottomRightX,
                            page.cropBottomRightY
                        ) {
                            ImageProcessor.processPage(page)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .clickable { viewModel.selectPage(index) }
                        ) {
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(5.dp),
                                shadowElevation = if (isSelected) 3.dp else 1.dp,
                                border = androidx.compose.foundation.BorderStroke(
                                    if (isSelected) 2.dp else 0.8.dp,
                                    if (isSelected) PrimaryTeal else SubtleBorder
                                ),
                                modifier = Modifier.size(width = 46.dp, height = 62.dp)
                            ) {
                                Image(
                                    bitmap = thumb.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 20.dp, height = 16.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PrimaryTeal),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    color = SecondaryText,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Add page tile (+)
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .clickable {
                                    importAdditionalLauncher.launch(
                                        arrayOf("image/jpeg", "image/png", "image/webp", "image/*", "application/pdf")
                                    )
                                }
                        ) {
                            Surface(
                                color = SurfaceWhite,
                                shape = RoundedCornerShape(5.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, CardBorder),
                                modifier = Modifier.size(width = 46.dp, height = 62.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Page",
                                        tint = PrimaryTeal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(19.dp))
                        }
                    }
                }
            }

            // 3. File name Field
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "File name",
                        color = SecondaryText,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { viewModel.setFileName(it) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryTeal,
                            unfocusedBorderColor = SubtleBorder,
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("file_name_input")
                    )
                }
            }

            // 4. OCR Toggle Row
            item {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 1.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(LightTeal),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DocumentScanner,
                                contentDescription = "OCR",
                                tint = PrimaryTeal,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OCR",
                                color = PrimaryText,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Extract text from images",
                                color = SecondaryText,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isOcrEnabled,
                            onCheckedChange = {
                                viewModel.toggleOcr(it)
                                if (it) {
                                    viewModel.performOcrOnCurrentDocument(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryTeal,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFD1DCDA)
                            ),
                            modifier = Modifier.testTag("ocr_switch")
                        )
                    }
                }
            }

            // 5. PDF Quality Row
            item {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 1.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showQualityDialog = true }
                        .testTag("pdf_quality_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(LightTeal),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "HD",
                                color = PrimaryTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PDF Quality",
                                color = PrimaryText,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = pdfQuality.label,
                                color = SecondaryText,
                                fontSize = 12.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Select Quality",
                            tint = SecondaryText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // 6. Real Measured Quality & Byte Size Info Pill Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(LightTeal)
                        .padding(vertical = 9.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "• Quality: ${pdfQuality.name.lowercase().replaceFirstChar { it.uppercase() }} (${pdfQuality.jpegQuality}%)   •   Output size: $realMeasuredSizeString",
                        color = PrimaryTeal,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Choose PDF Quality", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    PdfQuality.entries.forEach { q ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.setPdfQuality(q)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = pdfQuality == q,
                                onClick = {
                                    viewModel.setPdfQuality(q)
                                    showQualityDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryTeal)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = q.label, color = PrimaryText, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Cancel", color = SecondaryText)
                }
            }
        )
    }

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
