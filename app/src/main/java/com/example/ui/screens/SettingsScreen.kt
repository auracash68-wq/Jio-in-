package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.AppScreen
import com.example.data.OcrLanguage
import com.example.data.PdfQuality
import com.example.ui.components.DocSaathiBottomNavBar
import com.example.ui.components.DocSaathiWhiteTopBar
import com.example.ui.theme.*
import com.example.viewmodel.DocSaathiViewModel

@Composable
fun SettingsScreen(
    viewModel: DocSaathiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ocrLanguage by viewModel.ocrLanguage.collectAsState()
    val installedPacks by viewModel.installedOcrPacks.collectAsState()
    val pdfQuality by viewModel.pdfQuality.collectAsState()

    var showQualityDropdown by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }

    var isCameraPermGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isCameraPermGranted = granted
        if (granted) {
            viewModel.showMessage("Camera permission enabled for Docs Scanner")
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PageBackground,
        topBar = {
            DocSaathiWhiteTopBar(
                title = "Settings & Privacy",
                onBackClick = { viewModel.navigateBack() }
            )
        },
        bottomBar = {
            DocSaathiBottomNavBar(
                currentScreen = AppScreen.SETTINGS,
                onNavigate = { screen -> viewModel.setBottomNavDestination(screen) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp)
        ) {
            // 1. Offline OCR Language & LSTM Pack Manager Section
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "OCR Language & Offline Packs",
                        color = PrimaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Select language for 100% offline LSTM text recognition",
                        color = SecondaryText,
                        fontSize = 12.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = SurfaceWhite,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OcrLanguage.entries.forEachIndexed { index, lang ->
                                val isSelected = ocrLanguage == lang
                                val isInstalled = lang in installedPacks
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isInstalled) {
                                                viewModel.setOcrLanguage(lang)
                                            } else {
                                                viewModel.installOcrLanguagePack(lang)
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .testTag("ocr_lang_${lang.name.lowercase()}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = lang.displayName,
                                                color = PrimaryText,
                                                fontSize = 14.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = if (isInstalled) LightTeal else PageBackground,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (isInstalled) "Offline Ready (${lang.packSizeMb})" else "Tap to Load (${lang.packSizeMb})",
                                                    color = if (isInstalled) PrimaryTeal else SecondaryText,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${lang.nativeHint} • ${lang.packFileName}",
                                            color = SecondaryText,
                                            fontSize = 11.5.sp
                                        )
                                    }

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(21.dp)
                                                .clip(CircleShape)
                                                .border(2.dp, PrimaryTeal, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(PrimaryTeal)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(21.dp)
                                                .clip(CircleShape)
                                                .border(1.5.dp, Color(0xFFC0CBCE), CircleShape)
                                        )
                                    }
                                }

                                if (index < OcrLanguage.entries.size - 1) {
                                    HorizontalDivider(
                                        color = SubtleBorder,
                                        thickness = 0.8.dp,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. PDF Quality Section
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "PDF Quality",
                        color = PrimaryText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Choose the quality of saved PDFs",
                        color = SecondaryText,
                        fontSize = 12.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box {
                        Surface(
                            color = SurfaceWhite,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 2.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showQualityDropdown = true }
                                .testTag("settings_quality_dropdown")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(LightTeal),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "HD",
                                            color = PrimaryTeal,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = pdfQuality.label,
                                        color = PrimaryText,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand",
                                    tint = SecondaryText,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showQualityDropdown,
                            onDismissRequest = { showQualityDropdown = false },
                            modifier = Modifier.background(SurfaceWhite)
                        ) {
                            PdfQuality.entries.forEach { q ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = q.label,
                                            color = if (pdfQuality == q) PrimaryTeal else PrimaryText,
                                            fontWeight = if (pdfQuality == q) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        viewModel.setPdfQuality(q)
                                        showQualityDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Privacy & On-Demand Permissions Section
            item {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(LightTeal),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Privacy Shield",
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Privacy",
                                    color = PrimaryText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "No account. 100% offline.",
                                    color = PrimaryTeal,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Your files stay on your phone.",
                                    color = SecondaryText,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        HorizontalDivider(
                            color = SubtleBorder,
                            thickness = 0.8.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // Smart On-Demand Camera Permission Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isCameraPermGranted) {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    } else {
                                        try {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Outlined.CameraAlt,
                                    contentDescription = null,
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Camera Scanner Access",
                                        color = PrimaryText,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (isCameraPermGranted) "Allowed • Ready for unlimited scanning" else "Requested only when scanning",
                                        color = SecondaryText,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            Surface(
                                color = if (isCameraPermGranted) LightTeal else PageBackground,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isCameraPermGranted) "Allowed" else "Allow",
                                    color = if (isCameraPermGranted) PrimaryTeal else SecondaryText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Action Links: About, Share App, Rate App
            item {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsActionRow(
                            icon = Icons.Outlined.Info,
                            title = "About",
                            onClick = { showAboutDialog = true },
                            testTag = "settings_about"
                        )

                        HorizontalDivider(
                            color = SubtleBorder,
                            thickness = 0.8.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        SettingsActionRow(
                            icon = Icons.Outlined.Share,
                            title = "Share App",
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Docs Scanner App")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Try Docs Scanner — 100% Offline Exam Form Resizer, Exact KB Compressor & Regional OCR Scanner!"
                                    )
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Docs Scanner"))
                            },
                            testTag = "settings_share_app"
                        )

                        HorizontalDivider(
                            color = SubtleBorder,
                            thickness = 0.8.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        SettingsActionRow(
                            icon = Icons.Outlined.StarBorder,
                            title = "Rate App",
                            onClick = { showRateDialog = true },
                            testTag = "settings_rate_app"
                        )
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(PrimaryTeal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("About Docs Scanner", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Version 2.8 Pro (2026–2028 South Asia Edition)",
                        color = PrimaryTeal,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Docs Scanner is a 100% offline Exam Form Resizer, Exact KB Compressor & Regional OCR utility.\n\n" +
                            "• Bangladesh (Teletalk/BPSC) & India (SSC/UPSC 2026/IBPS) Presets\n" +
                            "• Binary-Search Exact KB Compressor (10 KB – 500 KB)\n" +
                            "• Name & Date Strip Generator on Photos\n" +
                            "• ID Card & Passport Front+Back Single A4 Merger\n" +
                            "• On-device Bengali, Hindi, Urdu & English OCR",
                        color = PrimaryText,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("Done")
                }
            }
        )
    }

    if (showRateDialog) {
        var selectedStars by remember { mutableIntStateOf(5) }
        AlertDialog(
            onDismissRequest = { showRateDialog = false },
            title = { Text("Rate Docs Scanner", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "How is your experience with Docs Scanner?",
                        color = SecondaryText,
                        fontSize = 13.5.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (star in 1..5) {
                            IconButton(
                                onClick = { selectedStars = star },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (star <= selectedStars) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "$star Stars",
                                    tint = if (star <= selectedStars) AmberGold else SecondaryText,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRateDialog = false
                        viewModel.showMessage("Thank you for rating Docs Scanner $selectedStars stars!")
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("Submit Rating")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRateDialog = false }) {
                    Text("Later", color = SecondaryText)
                }
            }
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(PageBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PrimaryTeal,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = PrimaryText,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SecondaryText,
            modifier = Modifier.size(21.dp)
        )
    }
}
