package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class SortOption(val label: String) {
    DATE_DESC("Date: Newest First"),
    DATE_ASC("Date: Oldest First"),
    SIZE_DESC("Size: Largest First"),
    SIZE_ASC("Size: Smallest First"),
    NAME_ASC("Name (A–Z)")
}

class DocSaathiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(application)

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val screenBackStack = mutableListOf<AppScreen>()

    // Active Scanning / Editing Session (100% Real User Pages)
    private val _activePages = MutableStateFlow<List<ScannedPage>>(emptyList())
    val activePages: StateFlow<List<ScannedPage>> = _activePages.asStateFlow()

    private val _selectedPageIndex = MutableStateFlow(0)
    val selectedPageIndex: StateFlow<Int> = _selectedPageIndex.asStateFlow()

    private val _isRetakingCurrentPage = MutableStateFlow(false)
    val isRetakingCurrentPage: StateFlow<Boolean> = _isRetakingCurrentPage.asStateFlow()

    private val _fileNameInput = MutableStateFlow(generateDefaultFileName())
    val fileNameInput: StateFlow<String> = _fileNameInput.asStateFlow()

    private val _isOcrEnabled = MutableStateFlow(false)
    val isOcrEnabled: StateFlow<Boolean> = _isOcrEnabled.asStateFlow()

    val ocrLanguage: StateFlow<OcrLanguage> = repository.ocrLanguage
    val pdfQuality: StateFlow<PdfQuality> = repository.pdfQuality

    // Offline LSTM OCR Language Pack Status
    private val _installedOcrPacks = MutableStateFlow(
        setOf(OcrLanguage.ENGLISH, OcrLanguage.BENGALI, OcrLanguage.HINDI, OcrLanguage.URDU)
    )
    val installedOcrPacks: StateFlow<Set<OcrLanguage>> = _installedOcrPacks.asStateFlow()

    // Exam Candidate Vault (Persisted on Disk via DocumentRepository)
    val examVaultItems: StateFlow<List<ExamVaultItem>> = repository.examVaultItems

    // Shared bitmap transferred from Exam Vault or Active Scan into Exam Resizer / KB Compressor
    private val _pendingToolBitmap = MutableStateFlow<Bitmap?>(null)
    val pendingToolBitmap: StateFlow<Bitmap?> = _pendingToolBitmap.asStateFlow()

    private val _pendingToolFileSizeBytes = MutableStateFlow(0L)
    val pendingToolFileSizeBytes: StateFlow<Long> = _pendingToolFileSizeBytes.asStateFlow()

    // Camera Scanner State
    private val _flashMode = MutableStateFlow(0) // 0: Auto, 1: On, 2: Off
    val flashMode: StateFlow<Int> = _flashMode.asStateFlow()

    private val _isAutoEdgeDetection = MutableStateFlow(true)
    val isAutoEdgeDetection: StateFlow<Boolean> = _isAutoEdgeDetection.asStateFlow()

    private val _showGridLines = MutableStateFlow(true)
    val showGridLines: StateFlow<Boolean> = _showGridLines.asStateFlow()

    // Search and Sort in Files Screen
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    // Processing / Modals state
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _ocrProgress = MutableStateFlow(0)
    val ocrProgress: StateFlow<Int> = _ocrProgress.asStateFlow()

    private val _ocrResultText = MutableStateFlow<String?>(null)
    val ocrResultText: StateFlow<String?> = _ocrResultText.asStateFlow()

    private val _previewingDocument = MutableStateFlow<DocumentItem?>(null)
    val previewingDocument: StateFlow<DocumentItem?> = _previewingDocument.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Filtered and sorted real documents list
    val filteredDocuments: StateFlow<List<DocumentItem>> = combine(
        repository.documents,
        _searchQuery,
        _sortOption
    ) { docs, query, sort ->
        val filtered = if (query.isBlank()) {
            docs
        } else {
            docs.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.date.contains(query, ignoreCase = true)
            }
        }

        when (sort) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.timestamp }
            SortOption.DATE_ASC -> filtered.sortedBy { it.timestamp }
            SortOption.SIZE_DESC -> filtered.sortedByDescending { parseSizeToBytes(it.fileSizeString) }
            SortOption.SIZE_ASC -> filtered.sortedBy { parseSizeToBytes(it.fileSizeString) }
            SortOption.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Recent real user documents (up to 3, empty if none exist)
    val recentDocuments: StateFlow<List<DocumentItem>> = repository.documents.map { docs ->
        docs.take(3)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun generateDefaultFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "Scan_$stamp"
    }

    private fun parseSizeToBytes(sizeStr: String): Double {
        return try {
            val clean = sizeStr.uppercase().trim()
            when {
                clean.endsWith("MB") -> (clean.removeSuffix("MB").trim().toDoubleOrNull() ?: 0.0) * 1024 * 1024
                clean.endsWith("KB") -> (clean.removeSuffix("KB").trim().toDoubleOrNull() ?: 0.0) * 1024
                clean.endsWith("B") -> clean.removeSuffix("B").trim().toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        } catch (_: Exception) {
            0.0
        }
    }

    fun navigateTo(screen: AppScreen) {
        if (_currentScreen.value != screen) {
            screenBackStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (_previewingDocument.value != null) {
            _previewingDocument.value = null
            return true
        }
        _isRetakingCurrentPage.value = false
        if (screenBackStack.isNotEmpty()) {
            _currentScreen.value = screenBackStack.removeAt(screenBackStack.size - 1)
            return true
        } else if (_currentScreen.value != AppScreen.HOME) {
            _currentScreen.value = AppScreen.HOME
            return true
        }
        return false
    }

    fun setBottomNavDestination(screen: AppScreen) {
        screenBackStack.clear()
        _isRetakingCurrentPage.value = false
        _currentScreen.value = screen
    }

    fun selectPage(index: Int) {
        if (index in _activePages.value.indices) {
            _selectedPageIndex.value = index
        }
    }

    fun setFileName(name: String) {
        _fileNameInput.value = name
    }

    fun toggleOcr(enabled: Boolean) {
        _isOcrEnabled.value = enabled
    }

    fun setOcrLanguage(lang: OcrLanguage) {
        repository.setOcrLanguage(lang)
        _installedOcrPacks.value = _installedOcrPacks.value + lang
        _userMessage.value = "OCR language active: ${lang.displayName} (${lang.packFileName})"
    }

    fun installOcrLanguagePack(lang: OcrLanguage) {
        _installedOcrPacks.value = _installedOcrPacks.value + lang
        repository.setOcrLanguage(lang)
        _userMessage.value = "${lang.displayName} Offline LSTM Pack (${lang.packSizeMb}) ready"
    }

    fun setPdfQuality(quality: PdfQuality) {
        repository.setPdfQuality(quality)
    }

    fun toggleFlash() {
        _flashMode.value = (_flashMode.value + 1) % 3
    }

    fun toggleAutoEdgeDetection() {
        _isAutoEdgeDetection.value = !_isAutoEdgeDetection.value
    }

    fun toggleGridLines() {
        _showGridLines.value = !_showGridLines.value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(sort: SortOption) {
        _sortOption.value = sort
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun clearOcrResult() {
        _ocrResultText.value = null
        _ocrProgress.value = 0
    }

    fun closeDocumentPreview() {
        _previewingDocument.value = null
    }

    fun setPendingToolBitmap(bitmap: Bitmap?, fileSizeBytes: Long = 0L) {
        _pendingToolBitmap.value = bitmap
        _pendingToolFileSizeBytes.value = fileSizeBytes
    }

    // Exam Candidate Vault Management (Persistent)
    fun saveProcessedExamItemToVault(
        context: Context,
        title: String,
        category: ExamPresetType,
        result: ProcessedExamResult
    ) {
        viewModelScope.launch {
            val savedFile = withContext(Dispatchers.IO) {
                ExamImageEngine.saveProcessedJpegToStorage(context, result)
            }
            val item = ExamVaultItem(
                id = UUID.randomUUID().toString(),
                title = title,
                category = category,
                dimensionsText = "${result.widthPx} × ${result.heightPx} px",
                fileSizeText = result.sizeFormatted,
                dateAdded = PdfGenerator.getCurrentDateFormatted(),
                bitmap = result.bitmap,
                filePath = savedFile.absolutePath
            )
            repository.addExamVaultItem(item)
            _userMessage.value = "Saved to Exam Vault: $title (${result.sizeFormatted})"
        }
    }

    fun deleteExamVaultItem(id: String) {
        repository.deleteExamVaultItem(id)
        _userMessage.value = "Removed from Exam Candidate Vault"
    }

    fun shareProcessedExamResult(context: Context, result: ProcessedExamResult) {
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    ExamImageEngine.saveProcessedJpegToStorage(context, result)
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, result.fileName)
                    putExtra(Intent.EXTRA_TEXT, result.verificationBadgeText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Share Portal-Verified JPG").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                _userMessage.value = "Unable to share JPG: ${e.localizedMessage}"
            }
        }
    }

    fun exportProcessedJpegToUri(context: Context, result: ProcessedExamResult, destinationUri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                        out.write(result.jpegBytes)
                    }
                }
                _userMessage.value = "Exported ${result.fileName} (${result.sizeFormatted}) to device!"
            } catch (e: Exception) {
                _userMessage.value = "Export failed: ${e.localizedMessage}"
            }
        }
    }

    fun saveProcessedBitmapAsDocument(
        context: Context,
        title: String,
        bitmap: Bitmap,
        navigateToFiles: Boolean = true
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val page = ScannedPage(
                    id = UUID.randomUUID().toString(),
                    bitmap = bitmap
                )
                val generatedFile = withContext(Dispatchers.IO) {
                    PdfGenerator.generatePdf(context, title, listOf(page), PdfQuality.HIGH)
                }
                val safeTitle = if (title.endsWith(".pdf", true)) title else "$title.pdf"
                val docItem = DocumentItem(
                    id = generatedFile.name,
                    title = safeTitle,
                    date = PdfGenerator.getCurrentDateFormatted(),
                    pageCount = 1,
                    fileSizeString = PdfGenerator.formatFileSize(generatedFile.length()),
                    filePath = generatedFile.absolutePath,
                    isDemonstration = false,
                    timestamp = System.currentTimeMillis()
                )
                repository.addDocument(docItem)
                _userMessage.value = "Saved $safeTitle (${docItem.fileSizeString})"
                if (navigateToFiles) {
                    navigateTo(AppScreen.FILES)
                }
            } catch (e: Exception) {
                _userMessage.value = "Error saving PDF: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun compressAndSavePdfUnderKb(
        context: Context,
        fileName: String,
        pagesToCompress: List<ScannedPage> = _activePages.value,
        targetMaxKb: Int,
        onComplete: (DocumentItem) -> Unit = {}
    ) {
        if (pagesToCompress.isEmpty()) {
            _userMessage.value = "Please import or scan at least 1 page first."
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val cleanName = fileName.ifBlank { "Compressed_Document_${targetMaxKb}KB" }
                val generatedFile = withContext(Dispatchers.IO) {
                    PdfGenerator.generatePdfWithTargetKb(context, cleanName, pagesToCompress, targetMaxKb)
                }
                val safeTitle = if (cleanName.endsWith(".pdf", true)) cleanName else "$cleanName.pdf"
                val docItem = DocumentItem(
                    id = generatedFile.name,
                    title = safeTitle,
                    date = PdfGenerator.getCurrentDateFormatted(),
                    pageCount = pagesToCompress.size,
                    fileSizeString = PdfGenerator.formatFileSize(generatedFile.length()),
                    filePath = generatedFile.absolutePath,
                    isDemonstration = false,
                    timestamp = System.currentTimeMillis()
                )
                repository.addDocument(docItem)
                _userMessage.value = "Compressed PDF saved: ${docItem.fileSizeString} (Target < $targetMaxKb KB)"
                onComplete(docItem)
            } catch (e: Exception) {
                _userMessage.value = "Compression failed: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // Page editing actions
    fun rotateCurrentPage() {
        val currentList = _activePages.value.toMutableList()
        val idx = _selectedPageIndex.value
        if (idx in currentList.indices) {
            val page = currentList[idx]
            val newDeg = (page.rotationDegrees + 90) % 360
            currentList[idx] = page.copy(rotationDegrees = newDeg)
            _activePages.value = currentList
        }
    }

    fun cycleFilterCurrentPage() {
        val currentList = _activePages.value.toMutableList()
        val idx = _selectedPageIndex.value
        if (idx in currentList.indices) {
            val page = currentList[idx]
            val allFilters = PageFilter.entries
            val nextFilterIndex = (allFilters.indexOf(page.filter) + 1) % allFilters.size
            currentList[idx] = page.copy(filter = allFilters[nextFilterIndex])
            _activePages.value = currentList
        }
    }

    fun setFilterCurrentPage(filter: PageFilter) {
        val currentList = _activePages.value.toMutableList()
        val idx = _selectedPageIndex.value
        if (idx in currentList.indices) {
            currentList[idx] = currentList[idx].copy(filter = filter)
            _activePages.value = currentList
        }
    }

    fun updateCropCoordinates(
        tlX: Float,
        tlY: Float,
        trX: Float,
        trY: Float,
        blX: Float,
        blY: Float,
        brX: Float,
        brY: Float
    ) {
        val currentList = _activePages.value.toMutableList()
        val idx = _selectedPageIndex.value
        if (idx in currentList.indices) {
            currentList[idx] = currentList[idx].copy(
                cropTopLeftX = tlX.coerceIn(0f, 0.48f),
                cropTopLeftY = tlY.coerceIn(0f, 0.48f),
                cropTopRightX = trX.coerceIn(0.52f, 1f),
                cropTopRightY = trY.coerceIn(0f, 0.48f),
                cropBottomLeftX = blX.coerceIn(0f, 0.48f),
                cropBottomLeftY = blY.coerceIn(0.52f, 1f),
                cropBottomRightX = brX.coerceIn(0.52f, 1f),
                cropBottomRightY = brY.coerceIn(0.52f, 1f)
            )
            _activePages.value = currentList
        }
    }

    /**
     * Real Auto Fit: analyzes the current page bitmap's luminance boundaries and snaps the 4 handles
     * to the detected document edges (or full frame if already snapped).
     */
    fun resetCropCurrentPage() {
        val currentList = _activePages.value.toMutableList()
        val idx = _selectedPageIndex.value
        if (idx in currentList.indices) {
            val page = currentList[idx]
            val bmp = page.bitmap
            val detected = if (bmp != null) {
                ImageProcessor.detectDocumentCorners(bmp)
            } else {
                DetectedCorners(0.02f, 0.02f, 0.98f, 0.02f, 0.02f, 0.98f, 0.98f, 0.98f)
            }
            currentList[idx] = page.copy(
                cropTopLeftX = detected.tlX,
                cropTopLeftY = detected.tlY,
                cropTopRightX = detected.trX,
                cropTopRightY = detected.trY,
                cropBottomLeftX = detected.blX,
                cropBottomLeftY = detected.blY,
                cropBottomRightX = detected.brX,
                cropBottomRightY = detected.brY
            )
            _activePages.value = currentList
            _userMessage.value = "Auto-fitted crop handles to document edges"
        }
    }

    fun addNewPage(bitmap: Bitmap? = null) {
        if (bitmap == null) {
            _isRetakingCurrentPage.value = false
            navigateTo(AppScreen.CAMERA_SCANNER)
            return
        }
        val newPage = ScannedPage(
            id = UUID.randomUUID().toString(),
            bitmap = bitmap
        )
        val list = _activePages.value + newPage
        _activePages.value = list
        _selectedPageIndex.value = list.size - 1
    }

    fun addImportedBitmaps(bitmaps: List<Bitmap>, appendToExisting: Boolean = false) {
        if (bitmaps.isEmpty()) return
        val newPages = bitmaps.map { bmp ->
            val corners = ImageProcessor.detectDocumentCorners(bmp)
            ScannedPage(
                id = UUID.randomUUID().toString(),
                bitmap = bmp,
                cropTopLeftX = corners.tlX,
                cropTopLeftY = corners.tlY,
                cropTopRightX = corners.trX,
                cropTopRightY = corners.trY,
                cropBottomLeftX = corners.blX,
                cropBottomLeftY = corners.blY,
                cropBottomRightX = corners.brX,
                cropBottomRightY = corners.brY
            )
        }
        if (appendToExisting) {
            val combined = _activePages.value + newPages
            _activePages.value = combined
            _selectedPageIndex.value = combined.size - 1
        } else {
            _activePages.value = newPages
            _selectedPageIndex.value = 0
            _fileNameInput.value = generateDefaultFileName()
            navigateTo(AppScreen.CROP_EDIT)
        }
        _userMessage.value = "Imported ${bitmaps.size} ${if (bitmaps.size == 1) "page" else "pages"}"
    }

    fun retakeCurrentPage() {
        _isRetakingCurrentPage.value = true
        navigateTo(AppScreen.CAMERA_SCANNER)
    }

    fun deleteCurrentPage() {
        val list = _activePages.value.toMutableList()
        val idx = _selectedPageIndex.value
        if (list.size > 1 && idx in list.indices) {
            list.removeAt(idx)
            _activePages.value = list
            _selectedPageIndex.value = (_selectedPageIndex.value - 1).coerceAtLeast(0)
            _userMessage.value = "Page removed"
        } else if (list.size == 1) {
            _activePages.value = emptyList()
            _selectedPageIndex.value = 0
            _userMessage.value = "All pages cleared"
            navigateBack()
        }
    }

    fun addCapturedPhoto(bitmap: Bitmap) {
        val corners = ImageProcessor.detectDocumentCorners(bitmap)
        val newPage = ScannedPage(
            id = UUID.randomUUID().toString(),
            bitmap = bitmap,
            cropTopLeftX = corners.tlX,
            cropTopLeftY = corners.tlY,
            cropTopRightX = corners.trX,
            cropTopRightY = corners.trY,
            cropBottomLeftX = corners.blX,
            cropBottomLeftY = corners.blY,
            cropBottomRightX = corners.brX,
            cropBottomRightY = corners.brY
        )

        val currentList = _activePages.value.toMutableList()
        val idx = _selectedPageIndex.value
        if (_isRetakingCurrentPage.value && idx in currentList.indices) {
            currentList[idx] = newPage
            _activePages.value = currentList
            _isRetakingCurrentPage.value = false
            _userMessage.value = "Page ${idx + 1} retaken"
        } else {
            _activePages.value = currentList + newPage
            _selectedPageIndex.value = _activePages.value.size - 1
            if (_activePages.value.size == 1) {
                _fileNameInput.value = generateDefaultFileName()
            }
        }
    }

    fun saveCurrentPdf(context: Context, onSaved: () -> Unit = {}) {
        val pages = _activePages.value
        if (pages.isEmpty()) {
            _userMessage.value = "Scan or import at least 1 page before saving PDF."
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val fileName = _fileNameInput.value.ifBlank { generateDefaultFileName() }
                val quality = pdfQuality.value

                val generatedFile = withContext(Dispatchers.IO) {
                    PdfGenerator.generatePdf(context, fileName, pages, quality)
                }

                val safeTitle = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
                val docItem = DocumentItem(
                    id = generatedFile.name,
                    title = safeTitle,
                    date = PdfGenerator.getCurrentDateFormatted(),
                    pageCount = pages.size,
                    fileSizeString = PdfGenerator.formatFileSize(generatedFile.length()),
                    filePath = generatedFile.absolutePath,
                    isDemonstration = false,
                    timestamp = System.currentTimeMillis()
                )

                repository.addDocument(docItem)
                _userMessage.value = "Saved $safeTitle (${docItem.fileSizeString})"
                onSaved()
            } catch (e: Exception) {
                _userMessage.value = "Failed to save PDF: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun exportPdfToExternalUri(context: Context, destinationUri: Uri, doc: DocumentItem? = null) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                withContext(Dispatchers.IO) {
                    val sourceFile = if (doc != null && doc.filePath.isNotEmpty() && File(doc.filePath).exists()) {
                        File(doc.filePath)
                    } else {
                        val fileName = _fileNameInput.value.ifBlank { generateDefaultFileName() }
                        PdfGenerator.generatePdf(context, fileName, _activePages.value, pdfQuality.value)
                    }
                    context.contentResolver.openOutputStream(destinationUri)?.use { outStream ->
                        FileInputStream(sourceFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                }
                _userMessage.value = "Saved to device storage!"
            } catch (e: Exception) {
                _userMessage.value = "Export failed: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun performOcrOnBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _isProcessing.value = true
            _ocrProgress.value = 5
            try {
                val result = OcrHelper.recognizeText(
                    bitmap = bitmap,
                    language = ocrLanguage.value,
                    onProgress = { pct -> _ocrProgress.value = pct }
                )
                _ocrResultText.value = result.getOrNull() ?: "No readable text detected in this image."
            } catch (e: Exception) {
                _ocrResultText.value = "OCR error: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun performOcrOnCurrentDocument(context: Context, doc: DocumentItem? = null) {
        viewModelScope.launch {
            val targetBitmap: Bitmap? = withContext(Dispatchers.IO) {
                if (doc != null && doc.filePath.isNotEmpty()) {
                    val file = File(doc.filePath)
                    if (file.exists() && file.extension.equals("pdf", true)) {
                        ImageProcessor.renderPdfPageFromFile(file, 0)
                    } else null
                } else {
                    val idx = _selectedPageIndex.value
                    val page = _activePages.value.getOrNull(idx) ?: _activePages.value.firstOrNull()
                    page?.let { ImageProcessor.processPage(it) }
                }
            }

            if (targetBitmap == null) {
                _userMessage.value = "Please scan or import an image first to extract OCR text."
                return@launch
            }
            performOcrOnBitmap(targetBitmap)
        }
    }

    fun saveOcrTextAsTxt(context: Context, text: String) {
        viewModelScope.launch {
            try {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "OCR_Text_$stamp.txt"
                val file = withContext(Dispatchers.IO) {
                    PdfGenerator.saveOcrTextAsTxtFile(context, fileName, text)
                }
                val docItem = DocumentItem(
                    id = file.name,
                    title = file.name,
                    date = PdfGenerator.getCurrentDateFormatted(),
                    pageCount = 1,
                    fileSizeString = PdfGenerator.formatFileSize(file.length()),
                    filePath = file.absolutePath,
                    isDemonstration = false,
                    timestamp = System.currentTimeMillis()
                )
                repository.addDocument(docItem)
                _userMessage.value = "Saved ${file.name} (${docItem.fileSizeString})"
            } catch (e: Exception) {
                _userMessage.value = "Failed to save .TXT: ${e.localizedMessage}"
            }
        }
    }

    fun saveOcrTextAsPdf(context: Context, text: String) {
        viewModelScope.launch {
            try {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "OCR_Document_$stamp.pdf"
                val file = withContext(Dispatchers.IO) {
                    PdfGenerator.generatePdfFromOcrText(context, fileName, text)
                }
                val docItem = DocumentItem(
                    id = file.name,
                    title = file.name,
                    date = PdfGenerator.getCurrentDateFormatted(),
                    pageCount = 1,
                    fileSizeString = PdfGenerator.formatFileSize(file.length()),
                    filePath = file.absolutePath,
                    isDemonstration = false,
                    timestamp = System.currentTimeMillis()
                )
                repository.addDocument(docItem)
                _userMessage.value = "Saved ${file.name} (${docItem.fileSizeString})"
            } catch (e: Exception) {
                _userMessage.value = "Failed to save OCR PDF: ${e.localizedMessage}"
            }
        }
    }

    fun shareCurrentOrDocument(context: Context, doc: DocumentItem? = null) {
        viewModelScope.launch {
            try {
                val targetFile: File? = if (doc != null && doc.filePath.isNotEmpty() && File(doc.filePath).exists()) {
                    File(doc.filePath)
                } else if (_activePages.value.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        val exportName = _fileNameInput.value.ifBlank { generateDefaultFileName() }
                        PdfGenerator.generatePdf(
                            context,
                            exportName,
                            _activePages.value,
                            pdfQuality.value
                        )
                    }
                } else if (repository.documents.value.isNotEmpty()) {
                    val latest = repository.documents.value.first()
                    File(latest.filePath).takeIf { it.exists() }
                } else {
                    null
                }

                if (targetFile == null || !targetFile.exists()) {
                    _userMessage.value = "No document to share yet. Scan or import a file first."
                    return@launch
                }

                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    targetFile
                )
                val mime = if (targetFile.extension.equals("txt", true)) "text/plain" else "application/pdf"
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, targetFile.name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Share via Docs Scanner").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                _userMessage.value = "Unable to share file: ${e.localizedMessage}"
            }
        }
    }

    fun deleteDocument(id: String) {
        repository.deleteDocument(id)
        if (_previewingDocument.value?.id == id) {
            _previewingDocument.value = null
        }
        _userMessage.value = "Document permanently deleted"
    }

    fun renameDocument(id: String, newName: String) {
        repository.renameDocument(id, newName)
        _userMessage.value = "Renamed to $newName"
    }

    fun openDocument(context: Context, doc: DocumentItem) {
        _previewingDocument.value = doc
    }

    fun openDocumentInExternalApp(context: Context, doc: DocumentItem) {
        viewModelScope.launch {
            try {
                val file = File(doc.filePath)
                if (!file.exists()) {
                    _userMessage.value = "File not found on storage"
                    return@launch
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val mime = if (file.extension.equals("txt", true)) "text/plain" else "application/pdf"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                _userMessage.value = "Opened in built-in Docs Scanner Reader"
            }
        }
    }
}
