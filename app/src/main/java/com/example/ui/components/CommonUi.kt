package com.example.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AppScreen
import com.example.data.DocumentItem
import com.example.ui.theme.*
import com.example.util.ImageProcessor
import java.io.File
import java.io.FileInputStream

@Composable
fun DocSaathiTealTopBar(
    title: String = "Docs Scanner",
    showOfflineBadge: Boolean = true,
    onSettingsClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TopBarGradient)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
            }

            if (showOfflineBadge) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF044E47).copy(alpha = 0.85f))
                        .border(1.dp, Color(0xFF28B5A7).copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF34D399))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "100% Offline",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .testTag("settings_top_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
fun DocSaathiWhiteTopBar(
    title: String,
    onBackClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        color = SurfaceWhite,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, SubtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PageBackground)
                    .testTag("top_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PrimaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = title,
                color = PrimaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )

            actions()
        }
    }
}

@Composable
fun DocSaathiBottomNavBar(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit
) {
    Surface(
        color = SurfaceWhite,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(0.8.dp, SubtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Outlined.Home,
                selectedIcon = Icons.Filled.Home,
                label = "Home",
                isSelected = currentScreen == AppScreen.HOME,
                onClick = { onNavigate(AppScreen.HOME) },
                testTag = "nav_home"
            )
            BottomNavItem(
                icon = Icons.Outlined.Folder,
                selectedIcon = Icons.Filled.Folder,
                label = "Files",
                isSelected = currentScreen == AppScreen.FILES,
                onClick = { onNavigate(AppScreen.FILES) },
                testTag = "nav_files"
            )
            BottomNavItem(
                icon = Icons.Outlined.Settings,
                selectedIcon = Icons.Filled.Settings,
                label = "Settings",
                isSelected = currentScreen == AppScreen.SETTINGS,
                onClick = { onNavigate(AppScreen.SETTINGS) },
                testTag = "nav_settings"
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val tint by animateColorAsState(
        targetValue = if (isSelected) PrimaryTeal else SecondaryText,
        label = "NavTint"
    )
    val pillWidth by animateDpAsState(
        targetValue = if (isSelected) 56.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "NavPillWidth"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .height(28.dp)
                .widthIn(min = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .width(pillWidth)
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(LightTeal)
                )
            }
            Icon(
                imageVector = if (isSelected) selectedIcon else icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun RedPdfBadge(
    isTextFile: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = if (isTextFile) {
                        listOf(Color(0xFF0A8F83), Color(0xFF065F57))
                    } else {
                        listOf(Color(0xFFEF5350), Color(0xFFD32F2F))
                    }
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(modifier = Modifier.size(18.dp)) {
                val w = size.width
                val h = size.height

                val path = Path().apply {
                    moveTo(w * 0.25f, h * 0.82f)
                    cubicTo(w * 0.35f, h * 0.68f, w * 0.45f, h * 0.48f, w * 0.48f, h * 0.18f)
                    cubicTo(w * 0.50f, h * 0.08f, w * 0.60f, h * 0.08f, w * 0.57f, h * 0.24f)
                    cubicTo(w * 0.52f, h * 0.45f, w * 0.64f, h * 0.64f, w * 0.85f, h * 0.76f)
                    cubicTo(w * 0.92f, h * 0.82f, w * 0.78f, h * 0.90f, w * 0.64f, h * 0.78f)
                    cubicTo(w * 0.50f, h * 0.68f, w * 0.38f, h * 0.74f, w * 0.28f, h * 0.84f)
                    close()
                }

                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(
                        width = 2.0f * density,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            Text(
                text = if (isTextFile) "TXT" else "PDF",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun PdfFileItemRow(
    document: DocumentItem,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isTxt = document.title.endsWith(".txt", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("file_item_${document.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RedPdfBadge(isTextFile = isTxt)

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = document.title,
                color = PrimaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = document.date,
                    color = SecondaryText,
                    fontSize = 12.sp
                )
                Text(
                    text = "  •  ",
                    color = MutedText,
                    fontSize = 12.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(PageBackground)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"}",
                        color = PrimaryTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "  •  ${document.fileSizeString}",
                    color = SecondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .testTag("menu_${document.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = SecondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(SurfaceWhite)
            ) {
                DropdownMenuItem(
                    text = { Text("Open PDF Reader", fontWeight = FontWeight.Medium) },
                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, tint = PrimaryTeal) },
                    onClick = {
                        menuExpanded = false
                        onClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share Document", fontWeight = FontWeight.Medium) },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryTeal) },
                    onClick = {
                        menuExpanded = false
                        onShare()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename File", fontWeight = FontWeight.Medium) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryTeal) },
                    onClick = {
                        menuExpanded = false
                        showRenameDialog = true
                    }
                )
                HorizontalDivider(color = SubtleBorder)
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuExpanded = false
                        showDeleteConfirm = true
                    }
                )
            }
        }
    }

    if (showRenameDialog) {
        var newTitle by remember {
            mutableStateOf(
                document.title.removeSuffix(".pdf").removeSuffix(".txt")
            )
        }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("File name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onRename(newTitle.trim())
                        }
                        showRenameDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("Save Name")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = SecondaryText)
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Document?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete \"${document.title}\" from Docs Scanner?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = SecondaryText)
                }
            }
        )
    }
}

/**
 * Full-screen In-App PDF & TXT Reader Modal with Real PdfRenderer, Zoom In/Out, and Direct Download.
 */
@Composable
fun InAppPdfViewerModal(
    document: DocumentItem,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onRunOcr: () -> Unit
) {
    val context = LocalContext.current
    var currentPage by remember { mutableIntStateOf(1) }
    val totalPages = document.pageCount.coerceAtLeast(1)
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val file = remember(document.filePath) { File(document.filePath) }
    val isTextFile = remember(document.title) { document.title.endsWith(".txt", ignoreCase = true) }

    val renderedPageBitmap = remember(document.filePath, currentPage) {
        if (!isTextFile && file.exists()) {
            ImageProcessor.renderPdfPageFromFile(file, currentPage - 1)
        } else {
            null
        }
    }

    val textFileContent = remember(document.filePath) {
        if (isTextFile && file.exists()) {
            try {
                file.readText()
            } catch (_: Exception) {
                ""
            }
        } else {
            ""
        }
    }

    val downloadFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(if (isTextFile) "text/plain" else "application/pdf")
    ) { destUri ->
        if (destUri != null && file.exists()) {
            try {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    FileInputStream(file).use { input ->
                        input.copyTo(out)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFF121A1E),
            topBar = {
                Surface(
                    color = Color(0xFF1A242A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(58.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Reader",
                                tint = Color.White
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = document.title,
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${document.date} • ${document.fileSizeString}",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp
                            )
                        }

                        // Zoom Out / Zoom In controls
                        IconButton(
                            onClick = {
                                zoomScale = (zoomScale - 0.5f).coerceIn(1f, 3.5f)
                                if (zoomScale == 1f) panOffset = Offset.Zero
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOut,
                                contentDescription = "Zoom Out",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = {
                                zoomScale = if (zoomScale >= 3f) 1f else (zoomScale + 0.5f).coerceIn(1f, 3.5f)
                                if (zoomScale == 1f) panOffset = Offset.Zero
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Zoom In",
                                tint = Color.White
                            )
                        }

                        // Download to Device
                        IconButton(
                            onClick = { downloadFileLauncher.launch(document.title) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download File",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = onShare) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share File",
                                tint = Color.White
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = Color(0xFF1A242A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentPage > 1) {
                                        currentPage--
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    }
                                },
                                enabled = currentPage > 1,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous Page",
                                    tint = if (currentPage > 1) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                            }
                            Text(
                                text = "Page $currentPage of $totalPages (${(zoomScale * 100).toInt()}%)",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = {
                                    if (currentPage < totalPages) {
                                        currentPage++
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    }
                                },
                                enabled = currentPage < totalPages,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next Page",
                                    tint = if (currentPage < totalPages) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onDismiss()
                                onRunOcr()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Extract OCR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
                            zoomScale = newScale
                            if (newScale > 1f) {
                                panOffset += pan
                            } else {
                                panOffset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(6.dp),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f / 1.414f)
                        .graphicsLayer(
                            scaleX = zoomScale,
                            scaleY = zoomScale,
                            translationX = panOffset.x,
                            translationY = panOffset.y
                        )
                ) {
                    if (isTextFile) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = textFileContent.ifBlank { "Empty text file." },
                                color = PrimaryText,
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp
                            )
                        }
                    } else if (renderedPageBitmap != null) {
                        Image(
                            bitmap = renderedPageBitmap.asImageBitmap(),
                            contentDescription = "PDF Page $currentPage",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Unable to render PDF page.",
                                color = SecondaryText,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OcrResultModal(
    text: String,
    ocrProgress: Int = 100,
    onSaveAsTxt: ((String) -> Unit)? = null,
    onSaveAsPdf: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var editableText by remember(text) { mutableStateOf(text) }
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(LightTeal),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Extracted Text (OCR)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "100% On-Device Recognition • Progress: $ocrProgress%",
                        color = PrimaryTeal,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { (ocrProgress / 100f).coerceIn(0f, 1f) },
                    color = PrimaryTeal,
                    trackColor = LightTeal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                OutlinedTextField(
                    value = editableText,
                    onValueChange = { editableText = it },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryTeal,
                        unfocusedBorderColor = SubtleBorder,
                        focusedContainerColor = PageBackground,
                        unfocusedContainerColor = PageBackground
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = PrimaryText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 260.dp)
                )

                // Save as .TXT and Save as PDF row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onSaveAsTxt != null) {
                        OutlinedButton(
                            onClick = {
                                onSaveAsTxt(editableText)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryTeal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ocr_save_txt_button")
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = PrimaryTeal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save as .TXT", color = PrimaryTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (onSaveAsPdf != null) {
                        OutlinedButton(
                            onClick = {
                                onSaveAsPdf(editableText)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryTeal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ocr_save_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = PrimaryTeal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save as PDF", color = PrimaryTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "OCR Text from Docs Scanner")
                            putExtra(Intent.EXTRA_TEXT, editableText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Extracted Text"))
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryTeal)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", color = PrimaryTeal)
                }

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(editableText))
                        copied = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (copied) "Copied!" else "Copy")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = SecondaryText)
            }
        }
    )
}
