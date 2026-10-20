package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Compress
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppScreen
import com.example.data.ScannedPage
import com.example.ui.components.DocSaathiWhiteTopBar
import com.example.ui.theme.*
import com.example.util.ExamImageEngine
import com.example.util.ImageProcessor
import com.example.util.PdfGenerator
import com.example.viewmodel.DocSaathiViewModel
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun KbCompressorScreen(
    viewModel: DocSaathiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activePages by viewModel.activePages.collectAsState()
    val pendingToolBitmap by viewModel.pendingToolBitmap.collectAsState()
    val pendingToolFileSizeBytes by viewModel.pendingToolFileSizeBytes.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    var isPdfMode by remember { mutableStateOf(false) }
    var targetKb by remember { mutableIntStateOf(50) }
    var targetKbText by remember { mutableStateOf("50") }
    var pdfFileName by remember { mutableStateOf("Compressed_Document") }

    // Initialize from pending tool bitmap or active scanned page (never a fake sample bitmap)
    var sourceBitmap by remember {
        mutableStateOf<Bitmap?>(pendingToolBitmap ?: activePages.firstOrNull()?.bitmap)
    }
    var originalSizeBytes by remember {
        mutableLongStateOf(pendingToolFileSizeBytes)
    }
    var importedPdfPages by remember {
        mutableStateOf<List<ScannedPage>>(activePages)
    }

    LaunchedEffect(pendingToolBitmap) {
        if (pendingToolBitmap != null) {
            sourceBitmap = pendingToolBitmap
            originalSizeBytes = pendingToolFileSizeBytes
            viewModel.setPendingToolBitmap(null, 0L)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val fileSize = ImageProcessor.measureUriByteSize(context, uri)
            val bitmaps = ImageProcessor.loadBitmapsFromUri(context, uri)
            if (bitmaps.isNotEmpty()) {
                sourceBitmap = bitmaps.first()
                originalSizeBytes = fileSize
                importedPdfPages = bitmaps.map { bmp ->
                    ScannedPage(id = UUID.randomUUID().toString(), bitmap = bmp)
                }
                viewModel.showMessage("Imported ${bitmaps.size} ${if (bitmaps.size == 1) "page" else "pages"} (${PdfGenerator.formatFileSize(fileSize)})")
            } else {
                viewModel.showMessage("Unable to decode selected file.")
            }
        }
    }

    // Measure real original size if not provided by file URI
    val measuredOriginalBytes = remember(sourceBitmap, originalSizeBytes) {
        if (originalSizeBytes > 0L) {
            originalSizeBytes
        } else {
            val bmp = sourceBitmap
            if (bmp != null) {
                val stream = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 98, stream)
                stream.size().toLong()
            } else {
                0L
            }
        }
    }

    // Real binary-search JPEG compression result
    val compressedResult = remember(sourceBitmap, targetKb) {
        sourceBitmap?.let { bmp ->
            ExamImageEngine.compressImageToExactKb(bmp, targetKb)
        }
    }

    val exportJpgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        val res = compressedResult
        if (uri != null && res != null) {
            viewModel.exportProcessedJpegToUri(context, res, uri)
        }
    }

    val quickKbTargets = listOf(20, 50, 100, 200, 300, 500)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PageBackground,
        topBar = {
            DocSaathiWhiteTopBar(
                title = "Exact KB Compressor",
                onBackClick = { viewModel.navigateBack() }
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
                    OutlinedButton(
                        onClick = {
                            filePickerLauncher.launch(
                                arrayOf("image/jpeg", "image/png", "image/webp", "image/*", "application/pdf")
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.3.dp, PrimaryTeal),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTeal),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("kb_pick_image_button")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }

                    if (!isPdfMode) {
                        Button(
                            onClick = {
                                compressedResult?.let { exportJpgLauncher.launch(it.fileName) }
                            },
                            enabled = compressedResult != null,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(48.dp)
                                .testTag("kb_save_jpg_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = compressedResult?.let { "Save JPG (${it.sizeFormatted})" } ?: "Save JPG",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                compressedResult?.let { viewModel.shareProcessedExamResult(context, it) }
                            },
                            enabled = compressedResult != null,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.3.dp, PrimaryTeal),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTeal),
                            modifier = Modifier
                                .weight(0.9f)
                                .height(48.dp)
                                .testTag("kb_share_jpg_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        val pagesToUse = if (importedPdfPages.isNotEmpty()) {
                            importedPdfPages
                        } else if (sourceBitmap != null) {
                            listOf(ScannedPage(id = UUID.randomUUID().toString(), bitmap = sourceBitmap))
                        } else {
                            emptyList()
                        }

                        Button(
                            onClick = {
                                viewModel.compressAndSavePdfUnderKb(
                                    context = context,
                                    fileName = pdfFileName,
                                    pagesToCompress = pagesToUse,
                                    targetMaxKb = targetKb,
                                    onComplete = { viewModel.navigateTo(AppScreen.FILES) }
                                )
                            },
                            enabled = pagesToUse.isNotEmpty() && !isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                            modifier = Modifier
                                .weight(1.8f)
                                .height(48.dp)
                                .testTag("kb_compress_pdf_button")
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save PDF (< $targetKb KB)",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
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
            contentPadding = PaddingValues(top = 12.dp, bottom = 22.dp)
        ) {
            // 1. Mode Switcher: Image (JPG) vs Multi-Page PDF
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = if (!isPdfMode) PrimaryTeal else SurfaceWhite,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (!isPdfMode) PrimaryTeal else CardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { isPdfMode = false }
                            .testTag("kb_mode_image"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Image (JPG) Compressor",
                                color = if (!isPdfMode) Color.White else PrimaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        color = if (isPdfMode) PrimaryTeal else SurfaceWhite,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isPdfMode) PrimaryTeal else CardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { isPdfMode = true }
                            .testTag("kb_mode_pdf"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Multi-Page PDF (< KB)",
                                color = if (isPdfMode) Color.White else PrimaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. Quick Target KB Presets
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "One-Tap Portal KB Limits",
                        color = SecondaryText,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(quickKbTargets) { kb ->
                            val isSelected = targetKb == kb
                            Surface(
                                color = if (isSelected) PrimaryTeal else SurfaceWhite,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) PrimaryTeal else CardBorder
                                ),
                                modifier = Modifier
                                    .clickable {
                                        targetKb = kb
                                        targetKbText = kb.toString()
                                    }
                                    .testTag("kb_chip_$kb")
                            ) {
                                Text(
                                    text = "< $kb KB",
                                    color = if (isSelected) Color.White else PrimaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Custom Target KB Input + Slider Card
            item {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Exact Target Maximum Size",
                                    color = PrimaryText,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Type exact KB or drag slider (10 KB – 500 KB)",
                                    color = SecondaryText,
                                    fontSize = 11.5.sp
                                )
                            }

                            OutlinedTextField(
                                value = targetKbText,
                                onValueChange = { input ->
                                    targetKbText = input.filter { it.isDigit() }
                                    val parsed = targetKbText.toIntOrNull()
                                    if (parsed != null) {
                                        targetKb = parsed.coerceIn(10, 1000)
                                    }
                                },
                                suffix = { Text("KB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryTeal) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .width(105.dp)
                                    .testTag("kb_custom_input")
                            )
                        }

                        Slider(
                            value = targetKb.coerceIn(10, 500).toFloat(),
                            onValueChange = {
                                val v = it.roundToInt().coerceIn(10, 500)
                                targetKb = v
                                targetKbText = v.toString()
                            },
                            valueRange = 10f..500f,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryTeal,
                                activeTrackColor = PrimaryTeal,
                                inactiveTrackColor = LightTeal
                            ),
                            modifier = Modifier.testTag("kb_target_slider")
                        )

                        if (compressedResult != null) {
                            HorizontalDivider(color = SubtleBorder)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Original Size", color = SecondaryText, fontSize = 11.sp)
                                    Text(
                                        text = PdfGenerator.formatFileSize(measuredOriginalBytes),
                                        color = PrimaryText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Compressed Size", color = SecondaryText, fontSize = 11.sp)
                                    Text(
                                        text = compressedResult.sizeFormatted,
                                        color = PrimaryTeal,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Output Dimensions", color = SecondaryText, fontSize = 11.sp)
                                    Text(
                                        text = "${compressedResult.widthPx} × ${compressedResult.heightPx} px",
                                        color = PrimaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Preview or Import Prompt Card
            if (sourceBitmap == null) {
                item {
                    Surface(
                        color = SurfaceWhite,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(LightTeal),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Compress,
                                    contentDescription = null,
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Text(
                                text = "Select an Image or PDF to Compress",
                                color = PrimaryText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Import any photo, signature, marksheet, or PDF document from your device to compress it under your exact KB limit.",
                                color = SecondaryText,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    filePickerLauncher.launch(
                                        arrayOf("image/jpeg", "image/png", "image/webp", "image/*", "application/pdf")
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select File from Device", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (!isPdfMode && compressedResult != null) {
                item {
                    Surface(
                        color = SurfaceWhite,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF141E24)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = compressedResult.bitmap.asImageBitmap(),
                                    contentDescription = "Compressed Output Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(10.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LightTeal)
                                    .border(1.dp, PrimaryTeal.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(vertical = 9.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = compressedResult.verificationBadgeText,
                                    color = DarkTeal,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                // Multi-page PDF Compression Card
                item {
                    Surface(
                        color = SurfaceWhite,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Multi-Page PDF Exact KB Optimizer",
                                color = PrimaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Automatically scales DPI and JPEG stream quality across all ${importedPdfPages.size.coerceAtLeast(1)} page(s) so the final PDF stays under $targetKb KB.",
                                color = SecondaryText,
                                fontSize = 12.5.sp
                            )

                            OutlinedTextField(
                                value = pdfFileName,
                                onValueChange = { pdfFileName = it },
                                label = { Text("Output PDF File Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
