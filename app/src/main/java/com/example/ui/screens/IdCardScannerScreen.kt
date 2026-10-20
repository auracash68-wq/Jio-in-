package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Badge
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.DocSaathiWhiteTopBar
import com.example.ui.theme.*
import com.example.util.ExamImageEngine
import com.example.util.ImageProcessor
import com.example.viewmodel.DocSaathiViewModel

@Composable
fun IdCardScannerScreen(
    viewModel: DocSaathiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var frontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var pickingFrontSide by remember { mutableStateOf(true) }
    var applyBwPhotocopyFilter by remember { mutableStateOf(false) }
    var enableWatermark by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("FOR OFFICIAL VERIFICATION ONLY") }
    var outputPdfName by remember { mutableStateOf("ID_Card_Merged_A4") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            ImageProcessor.loadBitmapFromUri(context, uri)?.let { bmp ->
                if (pickingFrontSide) {
                    frontBitmap = bmp
                    viewModel.showMessage("ID Card Front Side loaded (${bmp.width}×${bmp.height})")
                } else {
                    backBitmap = bmp
                    viewModel.showMessage("ID Card Back Side loaded (${bmp.width}×${bmp.height})")
                }
            }
        }
    }

    val hasAnySide = frontBitmap != null || backBitmap != null

    val mergedA4Bitmap = remember(
        frontBitmap,
        backBitmap,
        applyBwPhotocopyFilter,
        enableWatermark,
        watermarkText
    ) {
        if (!hasAnySide) {
            null
        } else {
            val front = frontBitmap ?: createBlankCardSlotBitmap("FRONT SIDE NOT SELECTED")
            val back = backBitmap ?: createBlankCardSlotBitmap("BACK SIDE NOT SELECTED")
            ExamImageEngine.mergeIdCardFrontAndBackOnA4(
                frontBitmap = front,
                backBitmap = back,
                applyBwFilter = applyBwPhotocopyFilter,
                crossWatermarkText = if (enableWatermark) watermarkText else ""
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PageBackground,
        topBar = {
            DocSaathiWhiteTopBar(
                title = "ID Card & Passport A4 Merger",
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
                    Button(
                        onClick = {
                            mergedA4Bitmap?.let { merged ->
                                viewModel.saveProcessedBitmapAsDocument(
                                    context = context,
                                    title = outputPdfName.ifBlank { "ID_Card_Merged_A4" },
                                    bitmap = merged,
                                    navigateToFiles = true
                                )
                            }
                        },
                        enabled = mergedA4Bitmap != null,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("id_save_a4_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Merged A4 Sheet as PDF",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
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
            // 1. Front + Back Side Selector Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Front Side Card
                    Surface(
                        color = SurfaceWhite,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (frontBitmap != null) PrimaryTeal else CardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                pickingFrontSide = true
                                imagePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                            }
                            .testTag("id_pick_front_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "1. FRONT SIDE",
                                color = PrimaryTeal,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(82.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PageBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                val bmp = frontBitmap
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Front Side",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = PrimaryTeal,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = "Select Front",
                                            color = SecondaryText,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (frontBitmap != null) "Replace Front" else "Import Front",
                                    color = PrimaryTeal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Back Side Card
                    Surface(
                        color = SurfaceWhite,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (backBitmap != null) PrimaryTeal else CardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                pickingFrontSide = false
                                imagePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                            }
                            .testTag("id_pick_back_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "2. BACK SIDE",
                                color = PrimaryTeal,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(82.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PageBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                val bmp = backBitmap
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Back Side",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = PrimaryTeal,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = "Select Back",
                                            color = SecondaryText,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (backBitmap != null) "Replace Back" else "Import Back",
                                    color = PrimaryTeal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 2. Photocopy B&W Mode + Anti-Misuse Cross-Watermark Controls
            item {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "300 DPI B&W Photocopy Filter",
                                    color = PrimaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "High-contrast laser-printer look for official submission",
                                    color = SecondaryText,
                                    fontSize = 11.5.sp
                                )
                            }
                            Switch(
                                checked = applyBwPhotocopyFilter,
                                onCheckedChange = { applyBwPhotocopyFilter = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PrimaryTeal
                                ),
                                modifier = Modifier.testTag("id_bw_filter_switch")
                            )
                        }

                        HorizontalDivider(color = SubtleBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Anti-Misuse Cross-Watermark",
                                    color = PrimaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Protects NID / Aadhaar / Passport copies from identity theft",
                                    color = SecondaryText,
                                    fontSize = 11.5.sp
                                )
                            }
                            Switch(
                                checked = enableWatermark,
                                onCheckedChange = { enableWatermark = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PrimaryTeal
                                ),
                                modifier = Modifier.testTag("id_watermark_switch")
                            )
                        }

                        if (enableWatermark) {
                            OutlinedTextField(
                                value = watermarkText,
                                onValueChange = { watermarkText = it },
                                label = { Text("Watermark Text") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("id_watermark_input")
                            )
                        }

                        OutlinedTextField(
                            value = outputPdfName,
                            onValueChange = { outputPdfName = it },
                            label = { Text("Output PDF File Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 3. Live Merged Single A4 Sheet Preview or Empty State Prompt
            item {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (mergedA4Bitmap == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(LightTeal),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Badge,
                                    contentDescription = null,
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = "Import Front & Back ID Card Images",
                                color = PrimaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap '1. FRONT SIDE' and '2. BACK SIDE' above to select your real ID card or passport photos and preview the merged A4 sheet.",
                                color = SecondaryText,
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Live Single-Page A4 Print Preview (Front + Back)",
                                color = PrimaryText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(290.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF141E24))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(6.dp),
                                    shadowElevation = 6.dp,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(1f / 1.414f)
                                ) {
                                    Image(
                                        bitmap = mergedA4Bitmap.asImageBitmap(),
                                        contentDescription = "Merged A4 Sheet Preview",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun createBlankCardSlotBitmap(label: String): Bitmap {
    val w = 720
    val h = 450
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.rgb(248, 250, 252))
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(203, 213, 225)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawRect(8f, 8f, w - 8f, h - 8f, borderPaint)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(148, 163, 184)
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    canvas.drawText(label, w / 2f, h / 2f + 8f, textPaint)
    return bmp
}
