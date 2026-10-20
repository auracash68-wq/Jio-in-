package com.example.data

import android.graphics.Bitmap

enum class AppScreen {
    HOME,
    CAMERA_SCANNER,
    CROP_EDIT,
    PDF_PREVIEW,
    FILES,
    SETTINGS,
    EXAM_RESIZER,
    KB_COMPRESSOR,
    ID_CARD_SCANNER
}

enum class PageFilter(val displayName: String) {
    ORIGINAL("Original"),
    MAGIC_COLOR("Magic Pro"),
    OCR_CLEAR("Clear Text"),
    GRAYSCALE("Grayscale"),
    BLACK_AND_WHITE("300 DPI B&W")
}

enum class PdfQuality(val label: String, val scaleFactor: Float, val jpegQuality: Int, val estimatedSizePerMb: Float) {
    HIGH("High (Best quality)", 1.0f, 95, 1.2f),
    MEDIUM("Medium (Balanced)", 0.78f, 82, 0.7f),
    LOW("Low (Compact size)", 0.55f, 68, 0.35f);

    companion object {
        fun fromLabel(label: String): PdfQuality {
            return entries.firstOrNull { it.label == label } ?: HIGH
        }
    }
}

enum class OcrLanguage(
    val displayName: String,
    val nativeHint: String,
    val code: String,
    val packFileName: String,
    val packSizeMb: String
) {
    BENGALI("Bengali", "বাংলা • LSTM OCR Pack", "bn", "ben.traineddata", "1.9 MB"),
    HINDI("Hindi", "हिन्दी • Devanagari OCR", "hi", "hin.traineddata", "1.8 MB"),
    URDU("Urdu", "اردو • Nastaliq LSTM Pack", "ur", "urd.traineddata", "2.0 MB"),
    ENGLISH("English", "English • Latin Script", "en", "eng.traineddata", "Built-in");

    companion object {
        fun fromName(name: String): OcrLanguage {
            return entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: BENGALI
        }
    }
}

enum class PortalCountry(val label: String, val flagCode: String) {
    BANGLADESH("Bangladesh", "BD"),
    INDIA("India (2026)", "IN"),
    CUSTOM("Custom Size", "PX/CM")
}

enum class ExamPresetType(val label: String) {
    PHOTO("Passport Photo"),
    SIGNATURE("Signature"),
    TRIPLE_SIGNATURE("UPSC Triple Sign"),
    THUMB("Left Thumb"),
    DECLARATION("Declaration")
}

enum class DimensionUnit(val label: String) {
    PX("px"),
    CM("cm"),
    MM("mm")
}

data class ExamPreset(
    val id: String,
    val country: PortalCountry,
    val title: String,
    val subtitle: String,
    val widthPx: Int,
    val heightPx: Int,
    val minKb: Int,
    val maxKb: Int,
    val targetKb: Int,
    val type: ExamPresetType,
    val portalFileName: String,
    val requiresFaceCoverage75: Boolean = false
)

data class ExamVaultItem(
    val id: String,
    val title: String,
    val category: ExamPresetType,
    val dimensionsText: String,
    val fileSizeText: String,
    val dateAdded: String,
    val bitmap: Bitmap? = null,
    val filePath: String = ""
)

data class ScannedPage(
    val id: String,
    val bitmap: Bitmap? = null,
    val imagePath: String? = null,
    val rotationDegrees: Int = 0,
    val filter: PageFilter = PageFilter.ORIGINAL,
    val cropTopLeftX: Float = 0.04f,
    val cropTopLeftY: Float = 0.04f,
    val cropTopRightX: Float = 0.96f,
    val cropTopRightY: Float = 0.04f,
    val cropBottomLeftX: Float = 0.04f,
    val cropBottomLeftY: Float = 0.96f,
    val cropBottomRightX: Float = 0.96f,
    val cropBottomRightY: Float = 0.96f
)

data class DocumentItem(
    val id: String,
    val title: String,
    val date: String,
    val pageCount: Int,
    val fileSizeString: String,
    val filePath: String,
    val isDemonstration: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
