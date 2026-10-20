package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AppScreen
import com.example.ui.theme.*
import com.example.util.ImageProcessor
import com.example.viewmodel.DocSaathiViewModel
import java.nio.ByteBuffer
import java.util.concurrent.Executors

@Composable
fun CameraScannerScreen(
    viewModel: DocSaathiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val activePages by viewModel.activePages.collectAsState()
    val flashMode by viewModel.flashMode.collectAsState()
    val isAutoEdge by viewModel.isAutoEdgeDetection.collectAsState()
    val showGrid by viewModel.showGridLines.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            viewModel.showMessage("Camera permission is required to capture live documents.")
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
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
                viewModel.addImportedBitmaps(bitmaps, appendToExisting = activePages.isNotEmpty())
                viewModel.navigateTo(AppScreen.CROP_EDIT)
            } else {
                viewModel.showMessage("Could not decode selected file.")
            }
        }
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                val providerFuture = ProcessCameraProvider.getInstance(context)
                if (providerFuture.isDone) {
                    providerFuture.get().unbindAll()
                }
            } catch (_: Exception) {
            }
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(flashMode, imageCapture) {
        imageCapture?.flashMode = when (flashMode) {
            1 -> ImageCapture.FLASH_MODE_ON
            2 -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    // Animated laser scan line for live viewfinder
    val infiniteTransition = rememberInfiniteTransition(label = "ScannerLaser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserLine"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A1014))
    ) {
        // 1. Dark Glass Camera Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateBack() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .testTag("camera_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Camera",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Center Mode Pill: Auto Edge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isAutoEdge) PrimaryTeal.copy(alpha = 0.85f)
                        else Color.White.copy(alpha = 0.14f)
                    )
                    .border(
                        1.dp,
                        if (isAutoEdge) Color(0xFF34D399) else Color.White.copy(alpha = 0.25f),
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { viewModel.toggleAutoEdgeDetection() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .testTag("auto_edge_chip"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAutoEdge) "Auto Edge: ON" else "Auto Edge: OFF",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { viewModel.toggleFlash() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (flashMode == 1) Color(0xFFF59E0B).copy(alpha = 0.28f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                        .testTag("flash_toggle_button")
                ) {
                    val flashIcon = when (flashMode) {
                        1 -> Icons.Default.FlashOn
                        2 -> Icons.Default.FlashOff
                        else -> Icons.Default.FlashAuto
                    }
                    Icon(
                        imageVector = flashIcon,
                        contentDescription = "Flash Mode",
                        tint = if (flashMode == 1) Color(0xFFFBBF24) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleGridLines() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (showGrid) PrimaryTeal.copy(alpha = 0.45f)
                            else Color.White.copy(alpha = 0.12f)
                        )
                        .testTag("grid_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Grid Lines",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2. Viewfinder Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF141E24)),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        previewViewRef = previewView
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()
                                imageCapture = capture

                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    capture
                                )
                            } catch (_: Exception) {
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Permission required prompt inside viewfinder
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Camera Permission Required",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Grant camera access to scan live paper documents, or use the Gallery button below to import an existing image or PDF.",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Grant Camera Permission", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 3x3 Grid + Teal Detection Frame + Animated Laser Line Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val frameMarginH = size.width * 0.08f
                val frameMarginV = size.height * 0.07f
                val frameLeft = frameMarginH
                val frameTop = frameMarginV
                val frameRight = size.width - frameMarginH
                val frameBottom = size.height - frameMarginV
                val frameWidth = frameRight - frameLeft
                val frameHeight = frameBottom - frameTop

                // Subtle 3x3 Rule-of-Thirds Grid
                if (showGrid) {
                    val gridColor = Color.White.copy(alpha = 0.18f)
                    val thirdW = frameWidth / 3f
                    val thirdH = frameHeight / 3f

                    drawLine(
                        color = gridColor,
                        start = Offset(frameLeft + thirdW, frameTop),
                        end = Offset(frameLeft + thirdW, frameBottom),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(frameLeft + thirdW * 2f, frameTop),
                        end = Offset(frameLeft + thirdW * 2f, frameBottom),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(frameLeft, frameTop + thirdH),
                        end = Offset(frameRight, frameTop + thirdH),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(frameLeft, frameTop + thirdH * 2f),
                        end = Offset(frameRight, frameTop + thirdH * 2f),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Teal Bounding Frame + Corner Brackets
                if (isAutoEdge) {
                    drawRoundRect(
                        color = Color(0xFF14B8A6).copy(alpha = 0.65f),
                        topLeft = Offset(frameLeft, frameTop),
                        size = Size(frameWidth, frameHeight),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                        style = Stroke(width = 1.8.dp.toPx())
                    )

                    // Animated Laser Line inside frame
                    val laserY = frameTop + (frameHeight * laserProgress)
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF34D399),
                                Color(0xFF14B8A6),
                                Color.Transparent
                            ),
                            startX = frameLeft,
                            endX = frameRight
                        ),
                        start = Offset(frameLeft + 8.dp.toPx(), laserY),
                        end = Offset(frameRight - 8.dp.toPx(), laserY),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Corner L-brackets
                    val bracketLen = 26.dp.toPx()
                    val strokeW = 4.dp.toPx()
                    val bracketColor = Color(0xFF34D399)

                    val corners = Path().apply {
                        // Top-Left
                        moveTo(frameLeft, frameTop + bracketLen)
                        lineTo(frameLeft, frameTop)
                        lineTo(frameLeft + bracketLen, frameTop)
                        // Top-Right
                        moveTo(frameRight - bracketLen, frameTop)
                        lineTo(frameRight, frameTop)
                        lineTo(frameRight, frameTop + bracketLen)
                        // Bottom-Left
                        moveTo(frameLeft, frameBottom - bracketLen)
                        lineTo(frameLeft, frameBottom)
                        lineTo(frameLeft + bracketLen, frameBottom)
                        // Bottom-Right
                        moveTo(frameRight - bracketLen, frameBottom)
                        lineTo(frameRight, frameBottom)
                        lineTo(frameRight, frameBottom - bracketLen)
                    }
                    drawPath(
                        path = corners,
                        color = bracketColor,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }

            // Bottom Hint Pill Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC0F171A))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Align document inside frame for automatic edge lock",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 3. Bottom Control Deck
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(104.dp)
                .padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Gallery / File Import
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        galleryLauncher.launch(
                            arrayOf("image/jpeg", "image/png", "image/webp", "image/*", "application/pdf")
                        )
                    }
                    .padding(6.dp)
                    .testTag("camera_gallery_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Import",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Center: Shutter Button
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color(0xFF28B5A7), CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF0A8F83), Color(0xFF065F57))
                        )
                    )
                    .clickable {
                        if (!hasCameraPermission) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            capturePhotoOrFallback(
                                context = context,
                                imageCapture = imageCapture,
                                previewView = previewViewRef,
                                onCaptured = { capturedBitmap ->
                                    viewModel.addCapturedPhoto(capturedBitmap)
                                    viewModel.navigateTo(AppScreen.CROP_EDIT)
                                },
                                onError = { msg ->
                                    viewModel.showMessage(msg)
                                }
                            )
                        }
                    }
                    .testTag("camera_shutter_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Capture Document",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            // Right: Page Thumbnail + Count Badge
            val latestBitmap = remember(activePages.size) {
                activePages.lastOrNull()?.bitmap
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (activePages.isNotEmpty()) {
                            viewModel.navigateTo(AppScreen.CROP_EDIT)
                        } else {
                            viewModel.showMessage("Capture or import a document page first.")
                        }
                    }
                    .padding(6.dp)
                    .testTag("camera_pages_thumbnail")
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (latestBitmap != null) {
                            Image(
                                bitmap = latestBitmap.asImageBitmap(),
                                contentDescription = "Captured Pages",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = "No pages yet",
                                tint = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .offset(x = 5.dp, y = (-5).dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(PrimaryTeal)
                            .border(1.5.dp, Color(0xFF0A1014), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${activePages.size}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (activePages.size == 1) "1 Page" else "${activePages.size} Pages",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun capturePhotoOrFallback(
    context: Context,
    imageCapture: ImageCapture?,
    previewView: PreviewView?,
    onCaptured: (Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    val mainExecutor = ContextCompat.getMainExecutor(context)
    if (imageCapture == null) {
        val viewBmp = previewView?.bitmap
        if (viewBmp != null) {
            onCaptured(viewBmp)
        } else {
            onError("Camera is initializing. Please wait a moment or import a file from Gallery.")
        }
        return
    }

    try {
        imageCapture.takePicture(
            mainExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    if (bitmap != null) {
                        onCaptured(bitmap)
                    } else {
                        val fallbackBmp = previewView?.bitmap
                        if (fallbackBmp != null) {
                            onCaptured(fallbackBmp)
                        } else {
                            onError("Could not decode camera frame.")
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    val fallbackBmp = previewView?.bitmap
                    if (fallbackBmp != null) {
                        onCaptured(fallbackBmp)
                    } else {
                        onError("Camera capture failed: ${exception.localizedMessage ?: "No camera stream"}. Use Import to select a file.")
                    }
                }
            }
        )
    } catch (e: Exception) {
        val fallbackBmp = previewView?.bitmap
        if (fallbackBmp != null) {
            onCaptured(fallbackBmp)
        } else {
            onError("Camera error: ${e.localizedMessage}")
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        val planeProxy = image.planes.firstOrNull() ?: return null
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val rotation = image.imageInfo.rotationDegrees
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        } else {
            raw
        }
    } catch (_: Exception) {
        null
    }
}
