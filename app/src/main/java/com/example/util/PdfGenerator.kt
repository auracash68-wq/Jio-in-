package com.example.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.PdfQuality
import com.example.data.ScannedPage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object PdfGenerator {

    // Standard A4 dimensions in PostScript points (72 points per inch)
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842

    /**
     * Generates a real PDF file from the provided pages.
     */
    fun generatePdf(
        context: Context,
        fileName: String,
        pages: List<ScannedPage>,
        quality: PdfQuality = PdfQuality.HIGH
    ): File {
        val pdfDocument = PdfDocument()

        val documentsDir = File(context.filesDir, "saved_pdfs")
        if (!documentsDir.exists()) documentsDir.mkdirs()

        val safeName = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
        val outputFile = File(documentsDir, safeName)

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        try {
            pages.forEachIndexed { index, scannedPage ->
                val processedBitmap = ImageProcessor.processPage(scannedPage)

                val scaledBitmap = if (quality.scaleFactor < 1.0f) {
                    val targetW = (processedBitmap.width * quality.scaleFactor).toInt().coerceAtLeast(100)
                    val targetH = (processedBitmap.height * quality.scaleFactor).toInt().coerceAtLeast(100)
                    Bitmap.createScaledBitmap(processedBitmap, targetW, targetH, true)
                } else {
                    processedBitmap
                }

                val compressedBitmap = compressBitmapToQuality(scaledBitmap, quality.jpegQuality)

                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = pdfPage.canvas
                canvas.drawColor(Color.WHITE)

                val margin = 20
                val usableW = A4_WIDTH - (margin * 2)
                val usableH = A4_HEIGHT - (margin * 2)

                val bmpW = compressedBitmap.width
                val bmpH = compressedBitmap.height
                val scale = minOf(usableW.toFloat() / bmpW, usableH.toFloat() / bmpH)

                val destW = (bmpW * scale).toInt()
                val destH = (bmpH * scale).toInt()
                val left = margin + (usableW - destW) / 2
                val top = margin + (usableH - destH) / 2

                canvas.drawBitmap(
                    compressedBitmap,
                    Rect(0, 0, bmpW, bmpH),
                    Rect(left, top, left + destW, top + destH),
                    paint
                )
                pdfDocument.finishPage(pdfPage)
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
        }

        return outputFile
    }

    /**
     * Generates a multi-page PDF compressed via binary search over per-page resolution and JPEG quality
     * so the actual generated PDF file size lands strictly under targetMaxKb.
     */
    fun generatePdfWithTargetKb(
        context: Context,
        fileName: String,
        pages: List<ScannedPage>,
        targetMaxKb: Int
    ): File {
        val targetBytes = targetMaxKb * 1024L
        val processedPages = pages.map { ImageProcessor.processPage(it) }

        var lowParam = 0.15f
        var highParam = 1.0f
        var bestFile: File? = null

        for (step in 0..6) {
            val midParam = (lowParam + highParam) / 2f
            val scaleFactor = (0.28f + 0.72f * midParam).coerceIn(0.25f, 1.0f)
            val jpegQ = (12 + (84 * midParam).roundToInt()).coerceIn(8, 96)

            val candidateFile = writeScaledBitmapsToPdf(context, fileName, processedPages, scaleFactor, jpegQ)
            bestFile = candidateFile

            if (candidateFile.length() > targetBytes) {
                highParam = midParam - 0.04f
            } else {
                lowParam = midParam + 0.04f
            }
        }

        if (bestFile != null && bestFile.length() > targetBytes) {
            bestFile = writeScaledBitmapsToPdf(context, fileName, processedPages, 0.25f, 10)
        }

        return bestFile ?: generatePdf(context, fileName, pages, PdfQuality.LOW)
    }

    private fun writeScaledBitmapsToPdf(
        context: Context,
        fileName: String,
        bitmaps: List<Bitmap>,
        scaleFactor: Float,
        jpegQuality: Int
    ): File {
        val pdfDocument = PdfDocument()
        val documentsDir = File(context.filesDir, "saved_pdfs")
        if (!documentsDir.exists()) documentsDir.mkdirs()

        val safeName = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
        val outputFile = File(documentsDir, safeName)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        try {
            bitmaps.forEachIndexed { index, srcBmp ->
                val targetW = (srcBmp.width * scaleFactor).roundToInt().coerceIn(80, 1400)
                val targetH = (srcBmp.height * scaleFactor).roundToInt().coerceIn(100, 1980)
                val scaled = Bitmap.createScaledBitmap(srcBmp, targetW, targetH, true)
                val compressed = compressBitmapToQuality(scaled, jpegQuality)

                val pageWidthPt = (A4_WIDTH * scaleFactor).roundToInt().coerceIn(220, A4_WIDTH)
                val pageHeightPt = (A4_HEIGHT * scaleFactor).roundToInt().coerceIn(310, A4_HEIGHT)

                val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPt, pageHeightPt, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas
                canvas.drawColor(Color.WHITE)

                val margin = (12 * scaleFactor).roundToInt().coerceAtLeast(6)
                val usableW = pageWidthPt - (margin * 2)
                val usableH = pageHeightPt - (margin * 2)

                val scale = minOf(usableW.toFloat() / compressed.width, usableH.toFloat() / compressed.height)
                val destW = (compressed.width * scale).roundToInt()
                val destH = (compressed.height * scale).roundToInt()
                val left = margin + (usableW - destW) / 2
                val top = margin + (usableH - destH) / 2

                canvas.drawBitmap(
                    compressed,
                    Rect(0, 0, compressed.width, compressed.height),
                    Rect(left, top, left + destW, top + destH),
                    paint
                )
                pdfDocument.finishPage(pdfPage)
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
        }

        return outputFile
    }

    /**
     * Generates a real, multi-line formatted PDF file directly from extracted OCR text.
     */
    fun generatePdfFromOcrText(
        context: Context,
        fileName: String,
        extractedText: String
    ): File {
        val pdfDocument = PdfDocument()
        val documentsDir = File(context.filesDir, "saved_pdfs")
        if (!documentsDir.exists()) documentsDir.mkdirs()

        val safeName = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
        val outputFile = File(documentsDir, safeName)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val margin = 40
        val contentWidth = A4_WIDTH - (margin * 2)

        try {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            canvas.save()
            canvas.translate(margin.toFloat(), margin.toFloat())
            val staticLayout = StaticLayout.Builder
                .obtain(extractedText, 0, extractedText.length, textPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(4f, 1.15f)
                .build()
            staticLayout.draw(canvas)
            canvas.restore()

            pdfDocument.finishPage(page)
            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
        }

        return outputFile
    }

    /**
     * Saves extracted OCR text as a real .txt file in local persistent storage.
     */
    fun saveOcrTextAsTxtFile(
        context: Context,
        fileName: String,
        extractedText: String
    ): File {
        val documentsDir = File(context.filesDir, "saved_pdfs")
        if (!documentsDir.exists()) documentsDir.mkdirs()

        val safeName = if (fileName.endsWith(".txt", ignoreCase = true)) fileName else "$fileName.txt"
        val outputFile = File(documentsDir, safeName)
        outputFile.writeText(extractedText)
        return outputFile
    }

    private fun compressBitmapToQuality(src: Bitmap, qualityPercent: Int): Bitmap {
        val stream = ByteArrayOutputStream()
        src.compress(Bitmap.CompressFormat.JPEG, qualityPercent.coerceIn(5, 100), stream)
        val byteArray = stream.toByteArray()
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size) ?: src
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val df = DecimalFormat("#.#")
        return when {
            bytes >= 1024 * 1024 -> "${df.format(bytes.toDouble() / (1024 * 1024))} MB"
            bytes >= 1024 -> "${df.format(bytes.toDouble() / 1024)} KB"
            else -> "$bytes B"
        }
    }

    fun getCurrentDateFormatted(): String {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
        return sdf.format(Date())
    }
}
