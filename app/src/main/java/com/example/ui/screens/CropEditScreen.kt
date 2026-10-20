package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppScreen
import com.example.data.PageFilter
import com.example.ui.components.DocSaathiWhiteTopBar
import com.example.ui.theme.*
import com.example.util.ImageProcessor
import com.example.viewmodel.DocSaathiViewModel
import kotlin.math.hypot

@Composable
fun CropEditScreen(
    viewModel: DocSaathiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activePages by viewModel.activePages.collectAsState()
    val selectedPageIndex by viewModel.selectedPageIndex.collectAsState()
    val currentPage = activePages.getOrNull(selectedPageIndex) ?: activePages.firstOrNull()

    var previewFlattened by remember { mutableStateOf(false) }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val bitmaps = uris.flatMap { uri ->
                ImageProcessor.loadBitmapsFromUri(context, uri)
            }
            if (bitmaps.isNotEmpty()) {
                viewModel.addImportedBitmaps(bitmaps, appendToExisting = activePages.isNotEmpty())
            }
        }
    }

    // Base rotated + filtered bitmap (for positioning 4-corner handles)
    val uncroppedFilteredBitmap = remember(
        currentPage?.id,
        selectedPageIndex,
        currentPage?.rotationDegrees,
        currentPage?.filter
    ) {
        currentPage?.let { ImageProcessor.getRotatedFilteredBitmap(it) }
    }

    // Perspective-deskewed + cropped + filtered bitmap
    val flattenedBitmap = remember(
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

    // 4-Corner Normalized Crop Handles (0f..1f)
    var tlX by remember(currentPage?.id, currentPage?.cropTopLeftX) {
        mutableFloatStateOf(currentPage?.cropTopLeftX ?: 0.06f)
    }
    var tlY by remember(currentPage?.id, currentPage?.cropTopLeftY) {
        mutableFloatStateOf(currentPage?.cropTopLeftY ?: 0.06f)
    }
    var trX by remember(currentPage?.id, currentPage?.cropTopRightX) {
        mutableFloatStateOf(currentPage?.cropTopRightX ?: 0.94f)
    }
    var trY by remember(currentPage?.id, currentPage?.cropTopRightY) {
        mutableFloatStateOf(currentPage?.cropTopRightY ?: 0.06f)
    }
    var blX by remember(currentPage?.id, currentPage?.cropBottomLeftX) {
        mutableFloatStateOf(currentPage?.cropBottomLeftX ?: 0.06f)
    }
    var blY by remember(currentPage?.id, currentPage?.cropBottomLeftY) {
        mutableFloatStateOf(currentPage?.cropBottomLeftY ?: 0.94f)
    }
    var brX by remember(currentPage?.id, currentPage?.cropBottomRightX) {
        mutableFloatStateOf(currentPage?.cropBottomRightX ?: 0.94f)
    }
    var brY by remember(currentPage?.id, currentPage?.cropBottomRightY) {
        mutableFloatStateOf(currentPage?.cropBottomRightY ?: 0.94f)
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableIntStateOf(-1) } // 0:TL, 1:TR, 2:BL, 3:BR

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PageBackground,
        topBar = {
            DocSaathiWhiteTopBar(
                title = "Crop & Edit",
                onBackClick = { viewModel.navigateBack() },
                actions = {
                    if (currentPage != null) {
                        Surface(
                            color = if (previewFlattened) PrimaryTeal else LightTeal,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clickable { previewFlattened = !previewFlattened }
                                .padding(end = 4.dp)
                                .testTag("toggle_deskew_preview")
                        ) {
                            Text(
                                text = if (previewFlattened) "Edit Handles" else "Deskew Preview",
                                color = if (previewFlattened) Color.White else PrimaryTeal,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (currentPage == null || uncroppedFilteredBitmap == null) {
            // Clean Empty State when no user pages are loaded
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(LightTeal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CropRotate,
                            contentDescription = null,
                            tint = PrimaryTeal,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = "No page selected for Crop & Edit",
                        color = PrimaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scan a document with your camera or import an image/PDF from your device.",
                        color = SecondaryText,
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.navigateTo(AppScreen.CAMERA_SCANNER) },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Page", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                importFileLauncher.launch(
                                    arrayOf("image/jpeg", "image/png", "image/webp", "image/*", "application/pdf")
                                )
                            },
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, PrimaryTeal),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, tint = PrimaryTeal, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import File", color = PrimaryTeal, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page Indicator Chip + Add Page Action
                if (activePages.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (selectedPageIndex > 0) viewModel.selectPage(selectedPageIndex - 1)
                            },
                            enabled = selectedPageIndex > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page", tint = PrimaryTeal)
                        }
                        Text(
                            text = "Page ${selectedPageIndex + 1} of ${activePages.size}",
                            color = PrimaryTeal,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                if (selectedPageIndex < activePages.size - 1) viewModel.selectPage(selectedPageIndex + 1)
                            },
                            enabled = selectedPageIndex < activePages.size - 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Page", tint = PrimaryTeal)
                        }
                    }
                }

                // 1. Document Canvas Area with 4-Corner Perspective Handles
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFE6ECEB))
                        .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmapToShow = if (previewFlattened && flattenedBitmap != null) {
                        flattenedBitmap
                    } else {
                        uncroppedFilteredBitmap
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(
                                (bitmapToShow.width.toFloat() / bitmapToShow.height.toFloat()).coerceIn(0.45f, 1.8f)
                            )
                            .onSizeChanged { boxSize = it }
                            .pointerInput(currentPage.id, previewFlattened) {
                                if (previewFlattened) return@pointerInput
                                detectDragGestures(
                                    onDragStart = { touch ->
                                        val w = boxSize.width.toFloat().coerceAtLeast(1f)
                                        val h = boxSize.height.toFloat().coerceAtLeast(1f)
                                        val handles = listOf(
                                            Offset(tlX * w, tlY * h), // 0: TL
                                            Offset(trX * w, trY * h), // 1: TR
                                            Offset(blX * w, blY * h), // 2: BL
                                            Offset(brX * w, brY * h)  // 3: BR
                                        )
                                        var closestIdx = 0
                                        var minDistance = Float.MAX_VALUE
                                        handles.forEachIndexed { idx, pt ->
                                            val d = hypot(touch.x - pt.x, touch.y - pt.y)
                                            if (d < minDistance) {
                                                minDistance = d
                                                closestIdx = idx
                                            }
                                        }
                                        activeHandle = closestIdx
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val w = boxSize.width.toFloat().coerceAtLeast(1f)
                                        val h = boxSize.height.toFloat().coerceAtLeast(1f)
                                        val dx = dragAmount.x / w
                                        val dy = dragAmount.y / h

                                        when (activeHandle) {
                                            0 -> {
                                                tlX = (tlX + dx).coerceIn(0.0f, 0.48f)
                                                tlY = (tlY + dy).coerceIn(0.0f, 0.48f)
                                            }
                                            1 -> {
                                                trX = (trX + dx).coerceIn(0.52f, 1.0f)
                                                trY = (trY + dy).coerceIn(0.0f, 0.48f)
                                            }
                                            2 -> {
                                                blX = (blX + dx).coerceIn(0.0f, 0.48f)
                                                blY = (blY + dy).coerceIn(0.52f, 1.0f)
                                            }
                                            3 -> {
                                                brX = (brX + dx).coerceIn(0.52f, 1.0f)
                                                brY = (brY + dy).coerceIn(0.52f, 1.0f)
                                            }
                                        }
                                        viewModel.updateCropCoordinates(tlX, tlY, trX, trY, blX, blY, brX, brY)
                                    },
                                    onDragEnd = { activeHandle = -1 }
                                )
                            }
                    ) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(6.dp),
                            shadowElevation = 6.dp,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Image(
                                bitmap = bitmapToShow.asImageBitmap(),
                                contentDescription = "Scanned Document Page",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 4-Corner Polygon & Draggable Handles Overlay (when not in flattened preview mode)
                        if (!previewFlattened) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height

                                val pTL = Offset(tlX * w, tlY * h)
                                val pTR = Offset(trX * w, trY * h)
                                val pBR = Offset(brX * w, brY * h)
                                val pBL = Offset(blX * w, blY * h)

                                // Semi-transparent tinted polygon inside 4 handles
                                val quadPath = Path().apply {
                                    moveTo(pTL.x, pTL.y)
                                    lineTo(pTR.x, pTR.y)
                                    lineTo(pBR.x, pBR.y)
                                    lineTo(pBL.x, pBL.y)
                                    close()
                                }
                                drawPath(
                                    path = quadPath,
                                    color = PrimaryTeal.copy(alpha = 0.08f)
                                )
                                drawPath(
                                    path = quadPath,
                                    color = PrimaryTeal,
                                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                                )

                                // Rule-of-thirds perspective guide lines inside polygon
                                for (i in 1..2) {
                                    val t = i / 3f
                                    val leftPt = Offset(
                                        pTL.x + (pBL.x - pTL.x) * t,
                                        pTL.y + (pBL.y - pTL.y) * t
                                    )
                                    val rightPt = Offset(
                                        pTR.x + (pBR.x - pTR.x) * t,
                                        pTR.y + (pBR.y - pTR.y) * t
                                    )
                                    drawLine(
                                        color = PrimaryTeal.copy(alpha = 0.35f),
                                        start = leftPt,
                                        end = rightPt,
                                        strokeWidth = 1.dp.toPx()
                                    )

                                    val topPt = Offset(
                                        pTL.x + (pTR.x - pTL.x) * t,
                                        pTL.y + (pTR.y - pTL.y) * t
                                    )
                                    val bottomPt = Offset(
                                        pBL.x + (pBR.x - pBL.x) * t,
                                        pBL.y + (pBR.y - pBL.y) * t
                                    )
                                    drawLine(
                                        color = PrimaryTeal.copy(alpha = 0.35f),
                                        start = topPt,
                                        end = bottomPt,
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }

                                // 4 Corner Handles
                                val handles = listOf(pTL, pTR, pBL, pBR)
                                val outerR = 11.dp.toPx()
                                val innerR = 7.dp.toPx()

                                handles.forEachIndexed { idx, center ->
                                    val isDragging = idx == activeHandle
                                    drawCircle(
                                        color = Color.White,
                                        radius = if (isDragging) outerR * 1.25f else outerR,
                                        center = center
                                    )
                                    drawCircle(
                                        color = if (isDragging) Color(0xFF10B981) else PrimaryTeal,
                                        radius = if (isDragging) innerR * 1.25f else innerR,
                                        center = center
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Real Pixel Filter Selector Strip
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(PageFilter.entries) { filter ->
                        val isSelected = currentPage.filter == filter
                        Surface(
                            color = if (isSelected) PrimaryTeal else SurfaceWhite,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) PrimaryTeal else CardBorder
                            ),
                            modifier = Modifier
                                .clickable { viewModel.setFilterCurrentPage(filter) }
                                .testTag("filter_chip_${filter.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (filter) {
                                        PageFilter.ORIGINAL -> Icons.Outlined.Image
                                        PageFilter.MAGIC_COLOR -> Icons.Outlined.AutoAwesome
                                        PageFilter.BLACK_AND_WHITE -> Icons.Outlined.Contrast
                                        PageFilter.GRAYSCALE -> Icons.Outlined.FilterBAndW
                                        PageFilter.OCR_CLEAR -> Icons.Outlined.AutoFixHigh
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else PrimaryTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = filter.displayName,
                                    color = if (isSelected) Color.White else PrimaryText,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Tool Strip Card (Rotate, Filter, Auto Fit, Retake, Delete)
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
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CropToolItem(
                            icon = Icons.Outlined.RotateRight,
                            label = "Rotate",
                            testTag = "tool_rotate",
                            onClick = { viewModel.rotateCurrentPage() }
                        )
                        CropToolItem(
                            icon = Icons.Outlined.AutoFixHigh,
                            label = "Filter",
                            testTag = "tool_filter",
                            onClick = { viewModel.cycleFilterCurrentPage() }
                        )
                        CropToolItem(
                            icon = Icons.Outlined.CropFree,
                            label = "Auto Fit",
                            testTag = "tool_auto_fit",
                            onClick = { viewModel.resetCropCurrentPage() }
                        )
                        CropToolItem(
                            icon = Icons.Outlined.Replay,
                            label = "Retake",
                            testTag = "tool_retake",
                            onClick = { viewModel.retakeCurrentPage() }
                        )
                        CropToolItem(
                            icon = Icons.Outlined.DeleteOutline,
                            label = "Delete",
                            testTag = "tool_delete",
                            onClick = { viewModel.deleteCurrentPage() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Primary Action Row (Add Page + Next)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            importFileLauncher.launch(
                                arrayOf("image/jpeg", "image/png", "image/webp", "image/*", "application/pdf")
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.3.dp, PrimaryTeal),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTeal),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(52.dp)
                            .testTag("crop_add_page_button")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Page", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.navigateTo(AppScreen.PDF_PREVIEW) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(52.dp)
                            .testTag("crop_next_button")
                    ) {
                        Text(
                            text = "Next (${activePages.size} ${if (activePages.size == 1) "Page" else "Pages"})",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CropToolItem(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(LightTeal),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = PrimaryTeal,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = PrimaryText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
