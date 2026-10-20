package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.DocSaathiWhiteTopBar
import com.example.ui.theme.*
import com.example.util.ExamImageEngine
import com.example.util.ImageProcessor
import com.example.viewmodel.DocSaathiViewModel
import java.io.File

@Composable
fun ExamResizerScreen(
    viewModel: DocSaathiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val vaultItems by viewModel.examVaultItems.collectAsState()
    val activePages by viewModel.activePages.collectAsState()
    val pendingToolBitmap by viewModel.pendingToolBitmap.collectAsState()

    var selectedCountry by remember { mutableStateOf(PortalCountry.BANGLADESH) }
    var showVaultTab by remember { mutableStateOf(false) }

    val availablePresets = remember(selectedCountry) {
        ExamImageEngine.builtInPresets.filter { it.country == selectedCountry }
    }

    var selectedPreset by remember {
        mutableStateOf(ExamImageEngine.builtInPresets.first())
    }

    // Custom Dimension Controls
    var customUnit by remember { mutableStateOf(DimensionUnit.PX) }
    var customDpi by remember { mutableIntStateOf(300) }
    var customWidthInput by remember { mutableStateOf("300") }
    var customHeightInput by remember { mutableStateOf("300") }
    var customMinKbInput by remember { mutableStateOf("20") }
    var customMaxKbInput by remember { mutableStateOf("50") }

    // Name & Date Strip Controls (No hardcoded fake candidate name)
    var addNameDateStrip by remember { mutableStateOf(false) }
    var candidateName by remember { mutableStateOf("") }
    var photoDate by remember { mutableStateOf(ExamImageEngine.getTodayDateFormatted()) }

    // UPSC 75% Face Guide Overlay
    var showFaceCoverageGuide by remember { mutableStateOf(true) }

    // Real User Source Bitmaps (Main + Triple Signature 3-Box Stitcher)
    var sourceBitmap by remember {
        mutableStateOf<Bitmap?>(pendingToolBitmap ?: activePages.firstOrNull()?.bitmap)
    }
    var tripleSig1 by remember { mutableStateOf<Bitmap?>(null) }
    var tripleSig2 by remember { mutableStateOf<Bitmap?>(null) }
    var tripleSig3 by remember { mutableStateOf<Bitmap?>(null) }
    var activeSignatureSlot by remember { mutableIntStateOf(0) } // 0=Main, 1=Sig1, 2=Sig2, 3=Sig3

    LaunchedEffect(pendingToolBitmap) {
        if (pendingToolBitmap != null) {
            sourceBitmap = pendingToolBitmap
            viewModel.setPendingToolBitmap(null, 0L)
        }
    }

    // Re-stitch Triple Signature whenever any of the 3 signature slots is uploaded
    LaunchedEffect(tripleSig1, tripleSig2, tripleSig3, selectedPreset.type) {
        if (selectedPreset.type == ExamPresetType.TRIPLE_SIGNATURE &&
            (tripleSig1 != null || tripleSig2 != null || tripleSig3 != null)
        ) {
            val blankSlot = Bitmap.createBitmap(360, 110, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.WHITE)
            }
            sourceBitmap = ExamImageEngine.createTripleSignatureStitchedBitmap(
                sig1 = tripleSig1 ?: blankSlot,
                sig2 = tripleSig2 ?: blankSlot,
                sig3 = tripleSig3 ?: blankSlot
            )
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val loaded = ImageProcessor.loadBitmapFromUri(context, uri)
            if (loaded != null) {
                when (activeSignatureSlot) {
                    1 -> tripleSig1 = loaded
                    2 -> tripleSig2 = loaded
                    3 -> tripleSig3 = loaded
                    else -> sourceBitmap = loaded
                }
                viewModel.showMessage("Image loaded (${loaded.width}×${loaded.height} px)")
            } else {
                viewModel.showMessage("Could not decode selected file.")
            }
        }
    }

    // Active dimensions and KB bounds
    val targetWidthPx = if (selectedCountry == PortalCountry.CUSTOM) {
        val rawW = customWidthInput.toFloatOrNull() ?: 300f
        ExamImageEngine.convertUnitToPixels(rawW, customUnit, customDpi)
    } else {
        selectedPreset.widthPx
    }

    val targetHeightPx = if (selectedCountry == PortalCountry.CUSTOM) {
        val rawH = customHeightInput.toFloatOrNull() ?: 300f
        ExamImageEngine.convertUnitToPixels(rawH, customUnit, customDpi)
    } else {
        selectedPreset.heightPx
    }

    val minKb = if (selectedCountry == PortalCountry.CUSTOM) {
        (customMinKbInput.toIntOrNull() ?: 10).coerceIn(2, 500)
    } else {
        selectedPreset.minKb
    }

    val maxKb = if (selectedCountry == PortalCountry.CUSTOM) {
        (customMaxKbInput.toIntOrNull() ?: 50).coerceAtLeast(minKb + 5)
    } else {
        selectedPreset.maxKb
    }

    val midTargetKb = if (selectedCountry == PortalCountry.CUSTOM) {
        (minKb + maxKb) / 2
    } else {
        selectedPreset.targetKb
    }

    val portalFileName = if (selectedCountry == PortalCountry.CUSTOM) {
        "custom_${targetWidthPx}x${targetHeightPx}.jpg"
    } else {
        selectedPreset.portalFileName
    }

    val processedResult = remember(
        sourceBitmap,
        targetWidthPx,
        targetHeightPx,
        minKb,
        maxKb,
        midTargetKb,
        addNameDateStrip,
        candidateName,
        photoDate,
        portalFileName
    ) {
        sourceBitmap?.let { bmp ->
            ExamImageEngine.processForExamPortal(
                sourceBitmap = bmp,
                widthPx = targetWidthPx,
                heightPx = targetHeightPx,
                minKb = minKb,
                maxKb = maxKb,
                targetKb = midTargetKb,
                addNameDateStrip = addNameDateStrip,
                candidateName = candidateName.ifBlank { "CANDIDATE NAME" },
                photoDate = photoDate,
                outputFileName = portalFileName
            )
        }
    }

    val exportJpgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        val res = processedResult
        if (uri != null && res != null) {
            viewModel.exportProcessedJpegToUri(context, res, uri)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PageBackground,
        topBar = {
            DocSaathiWhiteTopBar(
                title = "Job & Exam Resizer",
                onBackClick = { viewModel.navigateBack() },
                actions = {
                    Surface(
                        color = if (showVaultTab) PrimaryTeal else LightTeal,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable { showVaultTab = !showVaultTab }
                            .padding(end = 4.dp)
                            .testTag("exam_vault_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = "Exam Vault",
                                tint = if (showVaultTab) Color.White else PrimaryTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Vault (${vaultItems.size})",
                                color = if (showVaultTab) Color.White else PrimaryTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!showVaultTab) {
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
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val res = processedResult ?: return@OutlinedButton
                                val vaultTitle = if (selectedCountry == PortalCountry.CUSTOM) {
                                    "Custom ${targetWidthPx}×${targetHeightPx}"
                                } else {
                                    selectedPreset.title.substringBefore("•").trim()
                                }
                                viewModel.saveProcessedExamItemToVault(
                                    context = context,
                                    title = vaultTitle,
                                    category = selectedPreset.type,
                                    result = res
                                )
                            },
                            enabled = processedResult != null,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.3.dp, PrimaryTeal),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTeal),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("save_to_vault_button")
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Vault", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                processedResult?.let { exportJpgLauncher.launch(it.fileName) }
                            },
                            enabled = processedResult != null,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                            modifier = Modifier
                                .weight(1.35f)
                                .height(46.dp)
                                .testTag("export_exam_jpg_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Save JPG", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                processedResult?.let { viewModel.shareProcessedExamResult(context, it) }
                            },
                            enabled = processedResult != null,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.3.dp, PrimaryTeal),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTeal),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("share_exam_jpg_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (showVaultTab) {
            ExamCandidateVaultContent(
                vaultItems = vaultItems,
                onUseItem = { item ->
                    item.bitmap?.let { bmp ->
                        sourceBitmap = bmp
                        showVaultTab = false
                        viewModel.showMessage("Loaded \"${item.title}\" into Resizer")
                    }
                },
                onCompressItem = { item ->
                    item.bitmap?.let { bmp ->
                        val sizeBytes = if (item.filePath.isNotEmpty() && File(item.filePath).exists()) {
                            File(item.filePath).length()
                        } else 0L
                        viewModel.setPendingToolBitmap(bmp, sizeBytes)
                        viewModel.navigateTo(AppScreen.KB_COMPRESSOR)
                    }
                },
                onDeleteItem = { id -> viewModel.deleteExamVaultItem(id) },
                onBackToResizer = { showVaultTab = false },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
            ) {
                // 1. Country / Mode Selector Tabs
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceWhite)
                            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PortalCountry.entries.forEach { country ->
                            val isSelected = selectedCountry == country
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) PrimaryTeal else Color.Transparent)
                                    .clickable {
                                        selectedCountry = country
                                        val firstForCountry = ExamImageEngine.builtInPresets.firstOrNull { it.country == country }
                                        if (firstForCountry != null) {
                                            selectedPreset = firstForCountry
                                        }
                                    }
                                    .padding(vertical = 10.dp)
                                    .testTag("country_tab_${country.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${country.flagCode} • ${country.label}",
                                    color = if (isSelected) Color.White else PrimaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 2. Presets Selector OR Custom PX/CM/MM + DPI Form
                if (selectedCountry != PortalCountry.CUSTOM) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Select Official Portal Preset",
                                color = PrimaryText,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )

                            availablePresets.forEach { preset ->
                                val isSelected = selectedPreset.id == preset.id
                                Surface(
                                    color = if (isSelected) LightTeal else SurfaceWhite,
                                    shape = RoundedCornerShape(14.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (isSelected) 1.6.dp else 1.dp,
                                        if (isSelected) PrimaryTeal else CardBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPreset = preset }
                                        .testTag("preset_${preset.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = preset.title,
                                                color = if (isSelected) DarkTeal else PrimaryText,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = preset.subtitle,
                                                color = SecondaryText,
                                                fontSize = 11.5.sp
                                            )
                                        }
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedPreset = preset },
                                            colors = RadioButtonDefaults.colors(selectedColor = PrimaryTeal)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Custom Dimension & DPI Converter Card
                    item {
                        Surface(
                            color = SurfaceWhite,
                            shape = RoundedCornerShape(16.dp),
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
                                    Text(
                                        text = "Custom Dimensions & DPI",
                                        color = PrimaryText,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        DimensionUnit.entries.forEach { unit ->
                                            val sel = customUnit == unit
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (sel) PrimaryTeal else PageBackground)
                                                    .clickable { customUnit = unit }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = unit.label.uppercase(),
                                                    color = if (sel) Color.White else PrimaryText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customWidthInput,
                                        onValueChange = { customWidthInput = it },
                                        label = { Text("Width (${customUnit.label})") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = customHeightInput,
                                        onValueChange = { customHeightInput = it },
                                        label = { Text("Height (${customUnit.label})") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Print / Scan Resolution (DPI):",
                                        color = SecondaryText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(150, 200, 300).forEach { dpi ->
                                            val sel = customDpi == dpi
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (sel) LightTeal else PageBackground)
                                                    .border(
                                                        1.dp,
                                                        if (sel) PrimaryTeal else SubtleBorder,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { customDpi = dpi }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "$dpi DPI",
                                                    color = if (sel) PrimaryTeal else SecondaryText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customMinKbInput,
                                        onValueChange = { customMinKbInput = it },
                                        label = { Text("Min KB") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = customMaxKbInput,
                                        onValueChange = { customMaxKbInput = it },
                                        label = { Text("Max KB") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. UPSC 2026 Triple Signature Stitcher Controls (when TRIPLE_SIGNATURE is active)
                if (selectedPreset.type == ExamPresetType.TRIPLE_SIGNATURE) {
                    item {
                        Surface(
                            color = SurfaceWhite,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "UPSC 2026 Triple Signature 3-Box Stitcher",
                                    color = PrimaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap each slot to upload your 3 specimen signatures — auto-stitched onto a 400×400 px white canvas named signature.jpg.",
                                    color = SecondaryText,
                                    fontSize = 11.5.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        Triple(1, "Sign Slot 1", tripleSig1 != null),
                                        Triple(2, "Sign Slot 2", tripleSig2 != null),
                                        Triple(3, "Sign Slot 3", tripleSig3 != null)
                                    ).forEach { (slot, label, hasImg) ->
                                        OutlinedButton(
                                            onClick = {
                                                activeSignatureSlot = slot
                                                imagePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (hasImg) LightTeal else Color.Transparent,
                                                contentColor = PrimaryTeal
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = if (hasImg) "✓ $label" else label,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. IBPS / SBI Hand-Written Declaration Template Card (when DECLARATION is active)
                if (selectedPreset.type == ExamPresetType.DECLARATION) {
                    item {
                        Surface(
                            color = LightTeal,
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "IBPS / SBI Official Declaration Text",
                                        color = DarkTeal,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TextButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(ExamImageEngine.IBPS_DECLARATION_TEMPLATE))
                                            viewModel.showMessage("Official IBPS Declaration text copied!")
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp), tint = PrimaryTeal)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy Text", color = PrimaryTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    text = ExamImageEngine.IBPS_DECLARATION_TEMPLATE,
                                    color = PrimaryText,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }

                // 5. Name & Date on Photo Strip Generator (SSC / State PSC / Teletalk)
                item {
                    Surface(
                        color = SurfaceWhite,
                        shape = RoundedCornerShape(16.dp),
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
                                        text = "Name & Date Strip on Photo",
                                        color = PrimaryText,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Auto-draws 15% white bottom band with Name & Date (SSC/PSC)",
                                        color = SecondaryText,
                                        fontSize = 11.5.sp
                                    )
                                }
                                Switch(
                                    checked = addNameDateStrip,
                                    onCheckedChange = { addNameDateStrip = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = PrimaryTeal
                                    ),
                                    modifier = Modifier.testTag("name_date_strip_switch")
                                )
                            }

                            if (addNameDateStrip) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = candidateName,
                                        onValueChange = { candidateName = it },
                                        label = { Text("Candidate Name") },
                                        placeholder = { Text("Enter Name") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .testTag("candidate_name_input")
                                    )
                                    OutlinedTextField(
                                        value = photoDate,
                                        onValueChange = { photoDate = it },
                                        label = { Text("DOP (DD-MM-YYYY)") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("photo_date_input")
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Live Portal Verification & Output Card
                item {
                    Surface(
                        color = SurfaceWhite,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 3.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Live Portal-Verified Output",
                                        color = PrimaryText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Target: ${targetWidthPx}×${targetHeightPx} px • $minKb–$maxKb KB ($portalFileName)",
                                        color = PrimaryTeal,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Button(
                                    onClick = {
                                        activeSignatureSlot = 0
                                        imagePickerLauncher.launch(
                                            arrayOf("image/jpeg", "image/png", "image/webp", "image/*", "application/pdf")
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("exam_import_photo_button")
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (processedResult == null) {
                                // Clean Empty State Prompt when no candidate photo/signature has been imported yet
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(PageBackground)
                                        .padding(horizontal = 20.dp, vertical = 28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(LightTeal),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.VerifiedUser,
                                            contentDescription = null,
                                            tint = PrimaryTeal,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Text(
                                        text = "Select Candidate Photo or Signature",
                                        color = PrimaryText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Tap 'Import File' to select a photo, signature, thumb impression, or declaration from your phone for real-time ${targetWidthPx}×${targetHeightPx} px ($minKb–$maxKb KB) portal validation.",
                                        color = SecondaryText,
                                        fontSize = 12.5.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    if (vaultItems.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = { showVaultTab = true },
                                            shape = RoundedCornerShape(10.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.2.dp, PrimaryTeal)
                                        ) {
                                            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = PrimaryTeal, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Choose from Exam Vault (${vaultItems.size})", color = PrimaryTeal, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                // Real Processed Bitmap Preview Box + Optional UPSC 75% Face Oval Overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF141E24)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = processedResult.bitmap.asImageBitmap(),
                                        contentDescription = "Processed Portal Image",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(12.dp)
                                    )

                                    if (selectedPreset.requiresFaceCoverage75 && showFaceCoverageGuide) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val ovalW = size.height * 0.56f
                                            val ovalH = size.height * 0.75f
                                            drawOval(
                                                color = Color(0xFF34D399),
                                                topLeft = Offset((size.width - ovalW) / 2f, (size.height - ovalH) / 2f),
                                                size = Size(ovalW, ovalH),
                                                style = Stroke(
                                                    width = 2.5f * density,
                                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                                                )
                                            )
                                        }
                                    }
                                }

                                if (selectedPreset.requiresFaceCoverage75) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.clickable { showFaceCoverageGuide = !showFaceCoverageGuide }
                                    ) {
                                        Checkbox(
                                            checked = showFaceCoverageGuide,
                                            onCheckedChange = { showFaceCoverageGuide = it }
                                        )
                                        Text(
                                            text = "Show UPSC 2026 75% Face Coverage Guide",
                                            color = PrimaryTeal,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Green Portal Verification Badge
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(LightTeal)
                                        .border(1.dp, PrimaryTeal.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = processedResult.verificationBadgeText,
                                        color = DarkTeal,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
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

@Composable
private fun ExamCandidateVaultContent(
    vaultItems: List<ExamVaultItem>,
    onUseItem: (ExamVaultItem) -> Unit,
    onCompressItem: (ExamVaultItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onBackToResizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = LightTeal,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MintBorder.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Exam Candidate Vault (100% Offline)",
                        color = DarkTeal,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Reusable Photo, Signature, Thumb & Declaration assets",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = onBackToResizer) {
                    Text("Back to Resizer", color = PrimaryTeal, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (vaultItems.isEmpty()) {
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
                            imageVector = Icons.Outlined.FolderSpecial,
                            contentDescription = null,
                            tint = PrimaryTeal,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "No Saved Vault Assets Yet",
                        color = PrimaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Import & process your Photo, Signature, Left Thumb Impression, or Hand-Written Declaration in the Exam Resizer, then tap 'Vault' to store it permanently on your device.",
                        color = SecondaryText,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onBackToResizer,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Go to Exam Resizer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vaultItems, key = { it.id }) { item ->
                    Surface(
                        color = SurfaceWhite,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = PageBackground,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SubtleBorder),
                                modifier = Modifier.size(60.dp)
                            ) {
                                item.bitmap?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = PrimaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.category.label} • ${item.dimensionsText}",
                                    color = SecondaryText,
                                    fontSize = 11.5.sp
                                )
                                Text(
                                    text = "${item.fileSizeText} • ${item.dateAdded}",
                                    color = PrimaryTeal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Button(
                                    onClick = { onUseItem(item) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Resize", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onCompressItem(item) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryTeal),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Compress", fontSize = 11.sp, color = PrimaryTeal, fontWeight = FontWeight.Bold)
                                }
                            }

                            IconButton(onClick = { onDeleteItem(item.id) }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
