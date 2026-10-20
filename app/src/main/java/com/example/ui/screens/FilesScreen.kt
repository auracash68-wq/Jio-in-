package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FolderOpen
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
import com.example.data.AppScreen
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.DocSaathiViewModel
import com.example.viewmodel.SortOption

@Composable
fun FilesScreen(
    viewModel: DocSaathiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val documents by viewModel.filteredDocuments.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val previewingDoc by viewModel.previewingDocument.collectAsState()
    val ocrResultText by viewModel.ocrResultText.collectAsState()
    val ocrProgress by viewModel.ocrProgress.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PageBackground,
        topBar = {
            DocSaathiTealTopBar(
                title = "Docs Scanner",
                showOfflineBadge = false,
                onSettingsClick = { viewModel.navigateTo(AppScreen.SETTINGS) }
            )
        },
        bottomBar = {
            DocSaathiBottomNavBar(
                currentScreen = AppScreen.FILES,
                onNavigate = { screen -> viewModel.setBottomNavDestination(screen) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Search Bar with Clear & Sort Controls
            Surface(
                color = SurfaceWhite,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = PrimaryTeal,
                        modifier = Modifier.size(21.dp)
                    )

                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = {
                            Text(
                                text = "Search files...",
                                color = SecondaryText,
                                fontSize = 14.5.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = PrimaryTeal
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("files_search_input")
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setSearchQuery("") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Search",
                                tint = SecondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(LightTeal)
                                .testTag("sort_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Sort",
                                tint = PrimaryTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(SurfaceWhite)
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sortOption == option) PrimaryTeal else PrimaryText
                                        )
                                    },
                                    trailingIcon = {
                                        if (sortOption == option) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = PrimaryTeal,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.setSortOption(option)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 2. Document List or Empty State
            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(LightTeal),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = PrimaryTeal,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isBlank()) {
                                "No documents yet. Scan a document or import a file to get started."
                            } else {
                                "No matching documents found"
                            },
                            color = PrimaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.testTag("files_empty_state_text")
                        )
                        if (searchQuery.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try a different search keyword or clear the search filter.",
                                color = SecondaryText,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                Surface(
                    color = SurfaceWhite,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        itemsIndexed(documents, key = { _, item -> item.id }) { index, doc ->
                            PdfFileItemRow(
                                document = doc,
                                onClick = { viewModel.openDocument(context, doc) },
                                onShare = { viewModel.shareCurrentOrDocument(context, doc) },
                                onDelete = { viewModel.deleteDocument(doc.id) },
                                onRename = { newName -> viewModel.renameDocument(doc.id, newName) }
                            )

                            if (index < documents.size - 1) {
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
