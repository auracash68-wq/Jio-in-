package com.example.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.util.PdfGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("docs_scanner_prefs", Context.MODE_PRIVATE)
    private val metaFile = File(context.filesDir, "documents_meta.json")
    private val vaultMetaFile = File(context.filesDir, "exam_vault_meta.json")

    private val _documents = MutableStateFlow<List<DocumentItem>>(emptyList())
    val documents: StateFlow<List<DocumentItem>> = _documents.asStateFlow()

    private val _examVaultItems = MutableStateFlow<List<ExamVaultItem>>(emptyList())
    val examVaultItems: StateFlow<List<ExamVaultItem>> = _examVaultItems.asStateFlow()

    private val _ocrLanguage = MutableStateFlow(
        OcrLanguage.fromName(prefs.getString("ocr_language", OcrLanguage.BENGALI.displayName) ?: OcrLanguage.BENGALI.displayName)
    )
    val ocrLanguage: StateFlow<OcrLanguage> = _ocrLanguage.asStateFlow()

    private val _pdfQuality = MutableStateFlow(
        PdfQuality.fromLabel(prefs.getString("pdf_quality", PdfQuality.HIGH.label) ?: PdfQuality.HIGH.label)
    )
    val pdfQuality: StateFlow<PdfQuality> = _pdfQuality.asStateFlow()

    init {
        loadSavedDocumentsFromStorage()
        loadExamVaultFromStorage()
    }

    /**
     * Loads ONLY real user-created documents from local persistent storage.
     * No hardcoded demo or dummy files are ever loaded.
     */
    fun loadSavedDocumentsFromStorage() {
        val docsDir = File(context.filesDir, "saved_pdfs")
        if (!docsDir.exists()) {
            docsDir.mkdirs()
        }

        // Read persisted metadata map if present
        val savedMetaMap = mutableMapOf<String, JSONObject>()
        if (metaFile.exists()) {
            try {
                val arr = JSONArray(metaFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val path = obj.optString("filePath", "")
                    if (path.isNotEmpty()) {
                        savedMetaMap[path] = obj
                    }
                }
            } catch (_: Exception) {
            }
        }

        val realList = mutableListOf<DocumentItem>()
        val files = docsDir.listFiles { f ->
            f.isFile && (f.extension.equals("pdf", ignoreCase = true) || f.extension.equals("txt", ignoreCase = true))
        } ?: emptyArray()

        for (file in files) {
            if (!file.exists() || file.length() <= 0L) continue

            val meta = savedMetaMap[file.absolutePath]
            val pageCount = meta?.optInt("pageCount", 0)?.takeIf { it > 0 } ?: readRealPdfPageCount(file)
            val timestamp = meta?.optLong("timestamp", file.lastModified()) ?: file.lastModified()
            val formattedDate = meta?.optString("date", "")?.takeIf { it.isNotEmpty() }
                ?: SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(timestamp))
            val customTitle = meta?.optString("title", file.name)?.takeIf { it.isNotEmpty() } ?: file.name

            realList.add(
                DocumentItem(
                    id = file.name,
                    title = customTitle,
                    date = formattedDate,
                    pageCount = pageCount,
                    fileSizeString = PdfGenerator.formatFileSize(file.length()),
                    filePath = file.absolutePath,
                    isDemonstration = false,
                    timestamp = timestamp
                )
            )
        }

        _documents.value = realList.sortedByDescending { it.timestamp }
        persistDocumentsMeta()
    }

    private fun readRealPdfPageCount(file: File): Int {
        if (!file.extension.equals("pdf", ignoreCase = true)) return 1
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount.coerceAtLeast(1)
                }
            }
        } catch (_: Exception) {
            1
        }
    }

    private fun persistDocumentsMeta() {
        try {
            val arr = JSONArray()
            for (doc in _documents.value) {
                val obj = JSONObject().apply {
                    put("id", doc.id)
                    put("title", doc.title)
                    put("date", doc.date)
                    put("pageCount", doc.pageCount)
                    put("fileSizeString", doc.fileSizeString)
                    put("filePath", doc.filePath)
                    put("timestamp", doc.timestamp)
                }
                arr.put(obj)
            }
            metaFile.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    fun addDocument(document: DocumentItem) {
        val current = _documents.value.toMutableList()
        current.removeAll { it.filePath == document.filePath || it.id == document.id }
        current.add(0, document)
        _documents.value = current.sortedByDescending { it.timestamp }
        persistDocumentsMeta()
    }

    fun deleteDocument(id: String) {
        val item = _documents.value.firstOrNull { it.id == id }
        if (item != null && item.filePath.isNotEmpty()) {
            val file = File(item.filePath)
            if (file.exists()) {
                file.delete()
            }
        }
        _documents.value = _documents.value.filterNot { it.id == id }
        persistDocumentsMeta()
    }

    fun renameDocument(id: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return

        _documents.value = _documents.value.map { item ->
            if (item.id == id) {
                val ext = if (item.filePath.endsWith(".txt", ignoreCase = true)) ".txt" else ".pdf"
                val safeTitle = if (trimmed.endsWith(ext, ignoreCase = true)) trimmed else "$trimmed$ext"
                var newPath = item.filePath
                var newId = item.id
                if (item.filePath.isNotEmpty()) {
                    val oldFile = File(item.filePath)
                    val newFile = File(oldFile.parentFile, safeTitle)
                    if (oldFile.exists() && oldFile.renameTo(newFile)) {
                        newPath = newFile.absolutePath
                        newId = newFile.name
                    }
                }
                item.copy(id = newId, title = safeTitle, filePath = newPath)
            } else {
                item
            }
        }
        persistDocumentsMeta()
    }

    /**
     * Loads real user-saved Exam Candidate Vault items from persistent storage.
     */
    fun loadExamVaultFromStorage() {
        if (!vaultMetaFile.exists()) {
            _examVaultItems.value = emptyList()
            return
        }
        try {
            val arr = JSONArray(vaultMetaFile.readText())
            val items = mutableListOf<ExamVaultItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.optString("filePath", "")
                val file = File(path)
                if (!file.exists()) continue
                val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                val catName = obj.optString("category", ExamPresetType.PHOTO.name)
                val category = ExamPresetType.entries.firstOrNull { it.name == catName } ?: ExamPresetType.PHOTO
                items.add(
                    ExamVaultItem(
                        id = obj.optString("id", file.name),
                        title = obj.optString("title", file.name),
                        category = category,
                        dimensionsText = obj.optString("dimensionsText", "${bmp.width} × ${bmp.height} px"),
                        fileSizeText = obj.optString("fileSizeText", PdfGenerator.formatFileSize(file.length())),
                        dateAdded = obj.optString("dateAdded", PdfGenerator.getCurrentDateFormatted()),
                        bitmap = bmp,
                        filePath = file.absolutePath
                    )
                )
            }
            _examVaultItems.value = items
        } catch (_: Exception) {
            _examVaultItems.value = emptyList()
        }
    }

    fun addExamVaultItem(item: ExamVaultItem) {
        val updated = listOf(item) + _examVaultItems.value.filterNot { it.id == item.id }
        _examVaultItems.value = updated
        persistExamVaultMeta()
    }

    fun deleteExamVaultItem(id: String) {
        val target = _examVaultItems.value.firstOrNull { it.id == id }
        if (target != null && target.filePath.isNotEmpty()) {
            val file = File(target.filePath)
            if (file.exists()) file.delete()
        }
        _examVaultItems.value = _examVaultItems.value.filterNot { it.id == id }
        persistExamVaultMeta()
    }

    private fun persistExamVaultMeta() {
        try {
            val arr = JSONArray()
            for (item in _examVaultItems.value) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("category", item.category.name)
                    put("dimensionsText", item.dimensionsText)
                    put("fileSizeText", item.fileSizeText)
                    put("dateAdded", item.dateAdded)
                    put("filePath", item.filePath)
                }
                arr.put(obj)
            }
            vaultMetaFile.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    fun setOcrLanguage(language: OcrLanguage) {
        _ocrLanguage.value = language
        prefs.edit().putString("ocr_language", language.displayName).apply()
    }

    fun setPdfQuality(quality: PdfQuality) {
        _pdfQuality.value = quality
        prefs.edit().putString("pdf_quality", quality.label).apply()
    }
}
