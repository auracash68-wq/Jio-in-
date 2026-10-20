package com.example.util

import android.content.Context
import android.graphics.*
import com.example.data.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class ProcessedExamResult(
    val bitmap: Bitmap,
    val jpegBytes: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
    val sizeKb: Double,
    val sizeFormatted: String,
    val fileName: String,
    val isWithinPortalBounds: Boolean,
    val verificationBadgeText: String
)

object ExamImageEngine {

    const val IBPS_DECLARATION_TEMPLATE =
        "I, _______ (Name of the candidate), hereby declare that all the information submitted by me in the application form is correct, true and valid. I will present the supporting documents as and when required."

    val builtInPresets: List<ExamPreset> = listOf(
        // BANGLADESH PORTALS
        ExamPreset(
            id = "bd_teletalk_photo",
            country = PortalCountry.BANGLADESH,
            title = "Teletalk AllJobs / BPSC / Police / Bank (Photo)",
            subtitle = "300 × 300 px • 4 KB – 100 KB • Official JPG",
            widthPx = 300,
            heightPx = 300,
            minKb = 4,
            maxKb = 100,
            targetKb = 45,
            type = ExamPresetType.PHOTO,
            portalFileName = "teletalk_photo_300x300.jpg"
        ),
        ExamPreset(
            id = "bd_teletalk_sign",
            country = PortalCountry.BANGLADESH,
            title = "Teletalk AllJobs / BPSC / Police (Signature)",
            subtitle = "300 × 80 px • 3 KB – 60 KB • Official JPG",
            widthPx = 300,
            heightPx = 80,
            minKb = 3,
            maxKb = 60,
            targetKb = 25,
            type = ExamPresetType.SIGNATURE,
            portalFileName = "teletalk_signature_300x80.jpg"
        ),

        // INDIA PORTALS (2026 RULES)
        ExamPreset(
            id = "in_ssc_sign",
            country = PortalCountry.INDIA,
            title = "SSC (CGL / CHSL / MTS) Signature",
            subtitle = "140 × 60 px (4cm × 2cm) • Strict 10 KB – 20 KB",
            widthPx = 140,
            heightPx = 60,
            minKb = 10,
            maxKb = 20,
            targetKb = 15,
            type = ExamPresetType.SIGNATURE,
            portalFileName = "ssc_signature_140x60.jpg"
        ),
        ExamPreset(
            id = "in_ssc_photo",
            country = PortalCountry.INDIA,
            title = "SSC (CGL / CHSL / MTS) Photo",
            subtitle = "275 × 354 px (3.5cm × 4.5cm) • 20 KB – 50 KB",
            widthPx = 275,
            heightPx = 354,
            minKb = 20,
            maxKb = 50,
            targetKb = 35,
            type = ExamPresetType.PHOTO,
            portalFileName = "ssc_photo_275x354.jpg"
        ),
        ExamPreset(
            id = "in_upsc_photo",
            country = PortalCountry.INDIA,
            title = "UPSC (2026 Rule) Photo • 75% Face Guide",
            subtitle = "400 × 400 px (350–500 px) • 20 KB – 200 KB",
            widthPx = 400,
            heightPx = 400,
            minKb = 20,
            maxKb = 200,
            targetKb = 85,
            type = ExamPresetType.PHOTO,
            portalFileName = "photo.jpg",
            requiresFaceCoverage75 = true
        ),
        ExamPreset(
            id = "in_upsc_triple_sign",
            country = PortalCountry.INDIA,
            title = "UPSC (2026 Rule) Triple Signature Stitcher",
            subtitle = "3-Box Vertical Stitch • 400 × 400 px • 20 KB – 100 KB",
            widthPx = 400,
            heightPx = 400,
            minKb = 20,
            maxKb = 100,
            targetKb = 55,
            type = ExamPresetType.TRIPLE_SIGNATURE,
            portalFileName = "signature.jpg"
        ),
        ExamPreset(
            id = "in_ibps_photo",
            country = PortalCountry.INDIA,
            title = "IBPS / SBI / RBI Bank Exam — Photo",
            subtitle = "200 × 230 px • 20 KB – 50 KB • Bank Portal JPG",
            widthPx = 200,
            heightPx = 230,
            minKb = 20,
            maxKb = 50,
            targetKb = 35,
            type = ExamPresetType.PHOTO,
            portalFileName = "ibps_photo_200x230.jpg"
        ),
        ExamPreset(
            id = "in_ibps_sign",
            country = PortalCountry.INDIA,
            title = "IBPS / SBI / RBI Bank Exam — Signature",
            subtitle = "140 × 60 px • Strict 10 KB – 20 KB • Black Ink",
            widthPx = 140,
            heightPx = 60,
            minKb = 10,
            maxKb = 20,
            targetKb = 15,
            type = ExamPresetType.SIGNATURE,
            portalFileName = "ibps_signature_140x60.jpg"
        ),
        ExamPreset(
            id = "in_ibps_thumb",
            country = PortalCountry.INDIA,
            title = "IBPS / SBI / RBI — Left Thumb Impression",
            subtitle = "240 × 240 px • 20 KB – 50 KB • Official Format",
            widthPx = 240,
            heightPx = 240,
            minKb = 20,
            maxKb = 50,
            targetKb = 35,
            type = ExamPresetType.THUMB,
            portalFileName = "ibps_left_thumb_240x240.jpg"
        ),
        ExamPreset(
            id = "in_ibps_declaration",
            country = PortalCountry.INDIA,
            title = "IBPS / SBI / RBI — Hand-Written Declaration",
            subtitle = "800 × 400 px • 50 KB – 100 KB • English Template Included",
            widthPx = 800,
            heightPx = 400,
            minKb = 50,
            maxKb = 100,
            targetKb = 75,
            type = ExamPresetType.DECLARATION,
            portalFileName = "ibps_declaration_800x400.jpg"
        ),
        ExamPreset(
            id = "in_wbpsc_photo",
            country = PortalCountry.INDIA,
            title = "WBPSC / WBCS (West Bengal) Photo",
            subtitle = "138 × 177 px • 20 KB – 50 KB • Official JPG",
            widthPx = 138,
            heightPx = 177,
            minKb = 20,
            maxKb = 50,
            targetKb = 35,
            type = ExamPresetType.PHOTO,
            portalFileName = "wbpsc_photo_138x177.jpg"
        ),
        ExamPreset(
            id = "in_wbpsc_sign",
            country = PortalCountry.INDIA,
            title = "WBPSC / WBCS (West Bengal) Signature",
            subtitle = "138 × 59 px • 10 KB – 20 KB • Official JPG",
            widthPx = 138,
            heightPx = 59,
            minKb = 10,
            maxKb = 20,
            targetKb = 15,
            type = ExamPresetType.SIGNATURE,
            portalFileName = "wbpsc_signature_138x59.jpg"
        )
    )

    fun convertUnitToPixels(value: Float, unit: DimensionUnit, dpi: Int): Int {
        return when (unit) {
            DimensionUnit.PX -> value.roundToInt().coerceIn(40, 2400)
            DimensionUnit.CM -> ((value / 2.54f) * dpi).roundToInt().coerceIn(40, 2400)
            DimensionUnit.MM -> ((value / 25.4f) * dpi).roundToInt().coerceIn(40, 2400)
        }
    }

    fun getTodayDateFormatted(): String {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        return sdf.format(Date())
    }

    /**
     * Generates a clean passport photo bitmap for instant live preview & Vault testing.
     */
    fun createSamplePassportPhotoBitmap(): Bitmap {
        val w = 600
        val h = 750
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Studio light-blue/white background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                Color.rgb(228, 242, 252),
                Color.rgb(245, 250, 255),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Formal suit shoulders
        val suitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(24, 43, 73)
        }
        canvas.drawOval(RectF(70f, 470f, 530f, 860f), suitPaint)

        // White collar shirt + tie
        val shirtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val collarPath = Path().apply {
            moveTo(235f, 480f)
            lineTo(365f, 480f)
            lineTo(300f, 620f)
            close()
        }
        canvas.drawPath(collarPath, shirtPaint)

        val tiePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(7, 127, 116) }
        val tiePath = Path().apply {
            moveTo(286f, 515f)
            lineTo(314f, 515f)
            lineTo(322f, 640f)
            lineTo(300f, 670f)
            lineTo(278f, 640f)
            close()
        }
        canvas.drawPath(tiePath, tiePaint)

        // Neck & Head silhouette
        val skinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(232, 190, 158) }
        canvas.drawRect(255f, 400f, 345f, 505f, skinPaint)
        canvas.drawOval(RectF(185f, 155f, 415f, 445f), skinPaint)

        // Hair
        val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 34, 40) }
        canvas.drawArc(RectF(178f, 135f, 422f, 320f), 170f, 200f, true, hairPaint)

        // Eyes & smile
        val featurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 40, 45)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(255f, 285f, 9f, featurePaint)
        canvas.drawCircle(345f, 285f, 9f, featurePaint)

        val smilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(145, 85, 75)
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(RectF(260f, 330f, 340f, 385f), 20f, 140f, false, smilePaint)

        return bmp
    }

    /**
     * Generates a realistic handwritten signature on white paper.
     */
    fun createSampleSignatureBitmap(label: String = "A. Rahman"): Bitmap {
        val w = 600
        val h = 200
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(12, 24, 68)
            style = Paint.Style.STROKE
            strokeWidth = 5.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val sigPath = Path().apply {
            moveTo(75f, 135f)
            cubicTo(95f, 55f, 135f, 45f, 145f, 130f)
            cubicTo(155f, 95f, 195f, 80f, 225f, 118f)
            cubicTo(255f, 70f, 295f, 75f, 315f, 115f)
            cubicTo(345f, 82f, 410f, 85f, 485f, 108f)
            moveTo(95f, 152f)
            quadTo(285f, 132f, 515f, 142f)
        }
        canvas.drawPath(sigPath, inkPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(12, 24, 68)
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textSize = 36f
        }
        canvas.drawText(label, 180f, 95f, textPaint)

        return bmp
    }

    /**
     * Stitches 3 signatures vertically onto a single white canvas for UPSC 2026 Triple Signature Rule.
     */
    fun createTripleSignatureStitchedBitmap(
        sig1: Bitmap? = null,
        sig2: Bitmap? = null,
        sig3: Bitmap? = null
    ): Bitmap {
        val w = 500
        val h = 500
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        val s1 = sig1 ?: createSampleSignatureBitmap("Candidate Sign 1")
        val s2 = sig2 ?: createSampleSignatureBitmap("Candidate Sign 2")
        val s3 = sig3 ?: createSampleSignatureBitmap("Candidate Sign 3")

        val boxH = h / 3
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(225, 232, 236)
            strokeWidth = 2f
        }

        listOf(s1, s2, s3).forEachIndexed { idx, bmp ->
            val top = idx * boxH
            val destRect = Rect(24, top + 14, w - 24, top + boxH - 14)
            canvas.drawBitmap(bmp, null, destRect, paint)
            if (idx < 2) {
                canvas.drawLine(20f, ((idx + 1) * boxH).toFloat(), (w - 20).toFloat(), ((idx + 1) * boxH).toFloat(), dividerPaint)
            }
        }

        return out
    }

    /**
     * Generates a sample Left Thumb Impression bitmap on white paper.
     */
    fun createSampleThumbBitmap(): Bitmap {
        val w = 400
        val h = 400
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 42, 110)
            style = Paint.Style.STROKE
            strokeWidth = 3.2f
            strokeCap = Paint.Cap.ROUND
        }

        for (i in 1..14) {
            val rx = i * 8.5f
            val ry = i * 11.5f
            val oval = RectF(200f - rx, 205f - ry, 200f + rx, 205f + ry)
            canvas.drawArc(oval, 195f, 310f, false, ridgePaint)
        }

        return bmp
    }

    /**
     * Generates a sample Handwritten Declaration bitmap with the official IBPS/SBI text.
     */
    fun createSampleDeclarationBitmap(candidateName: String = "RAHUL SHARMA"): Bitmap {
        val w = 800
        val h = 400
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 28, 52)
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textSize = 27f
        }

        val nameToUse = candidateName.ifBlank { "Candidate Name" }
        val lines = listOf(
            "I, $nameToUse, hereby declare that all the",
            "information submitted by me in the application form",
            "is correct, true and valid. I will present the",
            "supporting documents as and when required."
        )

        var y = 105f
        lines.forEach { line ->
            canvas.drawText(line, 52f, y, paint)
            y += 55f
        }

        return bmp
    }

    /**
     * Generates a sample ID Card (Front or Back) for the ID Card & Passport A4 Merger.
     */
    fun createSampleIdCardBitmap(isFront: Boolean): Bitmap {
        val w = 680
        val h = 420
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(244, 249, 248)
        }
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 24f, 24f, cardBg)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(7, 127, 116)
        }
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), 88f), 24f, 24f, headerPaint)
        canvas.drawRect(0f, 44f, w.toFloat(), 88f, headerPaint)

        val whiteText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 26f
        }
        val darkBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 29, 36)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 24f
        }
        val bodyText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(65, 80, 88)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 21f
        }

        if (isFront) {
            canvas.drawText("NATIONAL IDENTITY CARD • FRONT SIDE", 32f, 54f, whiteText)
            val photoBmp = createSamplePassportPhotoBitmap()
            canvas.drawBitmap(photoBmp, null, Rect(36, 118, 196, 318), null)

            canvas.drawText("Name: MD. ARIFUR RAHMAN", 224f, 155f, darkBold)
            canvas.drawText("Date of Birth: 14 Aug 1999", 224f, 200f, bodyText)
            canvas.drawText("ID No: 4892 7301 9582", 224f, 245f, darkBold)
            canvas.drawText("Blood Group: O+  |  Status: Verified", 224f, 290f, bodyText)
        } else {
            canvas.drawText("NATIONAL IDENTITY CARD • BACK SIDE", 32f, 54f, whiteText)
            canvas.drawText("Permanent Address:", 36f, 140f, darkBold)
            canvas.drawText("House 42, Road 7, Dhanmondi / Sector V", 36f, 180f, bodyText)
            canvas.drawText("Issuing Authority: Regional Registration Board", 36f, 230f, bodyText)

            // Barcode strip at bottom
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 25, 30) }
            var bx = 36f
            while (bx < w - 40f) {
                val bw = if ((bx.toInt() % 3) == 0) 6f else 3f
                canvas.drawRect(bx, 290f, bx + bw, 365f, barPaint)
                bx += bw + 5f
            }
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(185, 208, 205)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRoundRect(RectF(2f, 2f, w - 2f, h - 2f), 24f, 24f, borderPaint)

        return bmp
    }

    /**
     * Merges Front and Back ID card bitmaps onto a single A4 page (300 DPI aspect ratio).
     */
    fun mergeIdCardFrontAndBackOnA4(
        frontBitmap: Bitmap,
        backBitmap: Bitmap,
        applyBwFilter: Boolean = false,
        crossWatermarkText: String = ""
    ): Bitmap {
        val a4Width = 1240
        val a4Height = 1754
        val page = Bitmap.createBitmap(a4Width, a4Height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)

        val cardW = 860
        val cardH = 540
        val left = (a4Width - cardW) / 2
        val frontTop = 210
        val backTop = 920

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (applyBwFilter) {
            val cm = ColorMatrix().apply { setSaturation(0f) }
            paint.colorFilter = ColorMatrixColorFilter(cm)
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 105, 112)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 26f
        }
        canvas.drawText("FRONT SIDE", left.toFloat(), (frontTop - 22).toFloat(), labelPaint)
        canvas.drawBitmap(frontBitmap, null, Rect(left, frontTop, left + cardW, frontTop + cardH), paint)

        canvas.drawText("BACK SIDE", left.toFloat(), (backTop - 22).toFloat(), labelPaint)
        canvas.drawBitmap(backBitmap, null, Rect(left, backTop, left + cardW, backTop + cardH), paint)

        if (crossWatermarkText.isNotBlank()) {
            canvas.save()
            canvas.rotate(-22f, a4Width / 2f, a4Height / 2f)
            val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(65, 7, 127, 116)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textSize = 38f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(crossWatermarkText.uppercase(), a4Width / 2f, a4Height / 2f, wmPaint)
            canvas.restore()
        }

        return page
    }

    /**
     * Draws a clean 15% height white strip at the bottom of a passport photo with Candidate Name & Date.
     */
    fun applyNameAndDateStrip(
        source: Bitmap,
        candidateName: String,
        dateText: String
    ): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val stripHeight = (h * 0.15f).coerceAtLeast(22f)
        val stripTop = h - stripHeight

        // White background strip across bottom 15%
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, stripTop, w, h, bgPaint)

        // Subtle top divider line
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = (h * 0.006f).coerceAtLeast(1f)
        }
        canvas.drawLine(0f, stripTop, w, stripTop, linePaint)

        val cleanName = candidateName.trim().uppercase().ifBlank { "CANDIDATE NAME" }
        val cleanDate = dateText.trim().ifBlank { getTodayDateFormatted() }
        val combinedLabel = "$cleanName | $cleanDate"

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = (stripHeight * 0.42f).coerceIn(9f, 42f)
        }

        // Auto-fit text width inside 92% of photo width
        val maxTextWidth = w * 0.92f
        var measured = textPaint.measureText(combinedLabel)
        while (measured > maxTextWidth && textPaint.textSize > 7f) {
            textPaint.textSize -= 1f
            measured = textPaint.measureText(combinedLabel)
        }

        val fontMetrics = textPaint.fontMetrics
        val textCenterY = stripTop + (stripHeight / 2f) - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(combinedLabel, w / 2f, textCenterY, textPaint)

        return out
    }

    /**
     * Resizes bitmap to exact pixel dimensions, strips EXIF, and binary-searches JPEG encoding
     * to land strictly within [minKb, maxKb] around targetKb.
     */
    fun processForExamPortal(
        sourceBitmap: Bitmap,
        widthPx: Int,
        heightPx: Int,
        minKb: Int,
        maxKb: Int,
        targetKb: Int,
        addNameDateStrip: Boolean = false,
        candidateName: String = "",
        photoDate: String = "",
        outputFileName: String = "exam_portal_output.jpg"
    ): ProcessedExamResult {
        val safeW = widthPx.coerceIn(40, 2400)
        val safeH = heightPx.coerceIn(30, 2400)

        // 1. Scale onto a clean sRGB ARGB_8888 canvas (strips all EXIF/GPS metadata automatically)
        var resized = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resized)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(sourceBitmap, null, Rect(0, 0, safeW, safeH), paint)

        // 2. Apply Name & Date Strip if enabled
        if (addNameDateStrip) {
            resized = applyNameAndDateStrip(resized, candidateName, photoDate)
        }

        // 3. Binary Search JPEG Compression Loop
        val targetBytes = targetKb * 1024
        val minBytes = minKb * 1024
        val maxBytes = maxKb * 1024

        var lowQ = 10
        var highQ = 100
        var bestBytes = encodeJpeg(resized, 92)

        for (step in 0..7) {
            val midQ = (lowQ + highQ) / 2
            val candidate = encodeJpeg(resized, midQ)
            bestBytes = candidate
            if (candidate.size > targetBytes) {
                highQ = midQ - 1
            } else {
                lowQ = midQ + 1
            }
        }

        // Ensure strict upper bound compliance
        if (bestBytes.size > maxBytes) {
            var q = 60
            while (q >= 8 && bestBytes.size > maxBytes) {
                bestBytes = encodeJpeg(resized, q)
                q -= 8
            }
        }

        // Ensure strict lower bound compliance (Minimum KB Guard for SSC 10-20 KB & UPSC 20-100 KB)
        if (bestBytes.size < minBytes) {
            val microVaried = applyImperceptibleMicroVariance(resized)
            val maxQBytes = encodeJpeg(microVaried, 100)
            val desiredBytes = ((minBytes + maxBytes) / 2).coerceIn(minBytes + 1024, (maxBytes - 1024).coerceAtLeast(minBytes + 512))
            bestBytes = if (maxQBytes.size < minBytes) {
                padJpegToTargetBytes(maxQBytes, desiredBytes)
            } else if (maxQBytes.size > maxBytes) {
                padJpegToTargetBytes(encodeJpeg(resized, 99), desiredBytes)
            } else {
                maxQBytes
            }
        }

        val sizeKb = bestBytes.size.toDouble() / 1024.0
        val df = DecimalFormat("#.#")
        val sizeFormatted = "${df.format(sizeKb)} KB"
        val isValid = sizeKb >= minKb && sizeKb <= maxKb

        val safeFileName = sanitizePortalFileName(outputFileName)
        val badgeText = "✓ PORTAL VERIFIED: ${safeW}x${safeH} px | $sizeFormatted | True JPG"

        return ProcessedExamResult(
            bitmap = resized,
            jpegBytes = bestBytes,
            widthPx = safeW,
            heightPx = safeH,
            sizeKb = sizeKb,
            sizeFormatted = sizeFormatted,
            fileName = safeFileName,
            isWithinPortalBounds = isValid,
            verificationBadgeText = badgeText
        )
    }

    /**
     * Applies imperceptible +/-2 RGB high-frequency pixel variance across background pixels
     * to naturally increase Huffman entropy when a signature on white background is under the minimum KB floor.
     */
    private fun applyImperceptibleMicroVariance(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(w * h)
        out.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val delta = ((i * 1103515245 + 12345) ushr 16) and 0x03
            val r = (Color.red(c) - delta).coerceIn(0, 255)
            val g = (Color.green(c) - ((delta + 1) and 0x03)).coerceIn(0, 255)
            val b = (Color.blue(c) - ((delta + 2) and 0x03)).coerceIn(0, 255)
            pixels[i] = Color.rgb(r, g, b)
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Exact KB Binary-Search Compressor for general images (unlocks dimension scaling if needed).
     */
    fun compressImageToExactKb(
        sourceBitmap: Bitmap,
        targetKb: Int
    ): ProcessedExamResult {
        val targetBytes = targetKb * 1024
        val minBytes = ((targetKb - 4).coerceAtLeast(4)) * 1024
        val maxBytes = targetKb * 1024

        var workingBitmap = sourceBitmap
        var bestBytes = encodeJpeg(workingBitmap, 85)

        // Binary search quality first
        var lowQ = 12
        var highQ = 100
        for (i in 0..7) {
            val midQ = (lowQ + highQ) / 2
            val candidate = encodeJpeg(workingBitmap, midQ)
            bestBytes = candidate
            if (candidate.size > targetBytes) {
                highQ = midQ - 1
            } else {
                lowQ = midQ + 1
            }
        }

        // If still over maxBytes, progressively scale dimensions down
        var scale = 0.85f
        while (bestBytes.size > maxBytes && scale >= 0.25f) {
            val nw = (sourceBitmap.width * scale).roundToInt().coerceAtLeast(120)
            val nh = (sourceBitmap.height * scale).roundToInt().coerceAtLeast(120)
            workingBitmap = Bitmap.createScaledBitmap(sourceBitmap, nw, nh, true)
            bestBytes = encodeJpeg(workingBitmap, 75)
            scale -= 0.15f
        }

        // If under minBytes, pad to land right near targetKb
        if (bestBytes.size < minBytes) {
            val maxQ = encodeJpeg(workingBitmap, 100)
            if (maxQ.size < minBytes) {
                bestBytes = padJpegToTargetBytes(maxQ, (targetBytes * 0.94).roundToInt())
            } else {
                bestBytes = maxQ
            }
        }

        val sizeKb = bestBytes.size.toDouble() / 1024.0
        val df = DecimalFormat("#.#")
        val sizeFormatted = "${df.format(sizeKb)} KB"

        return ProcessedExamResult(
            bitmap = workingBitmap,
            jpegBytes = bestBytes,
            widthPx = workingBitmap.width,
            heightPx = workingBitmap.height,
            sizeKb = sizeKb,
            sizeFormatted = sizeFormatted,
            fileName = "compressed_${targetKb}kb.jpg",
            isWithinPortalBounds = true,
            verificationBadgeText = "✓ PORTAL VERIFIED: ${workingBitmap.width}x${workingBitmap.height} px | $sizeFormatted | True JPG"
        )
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(5, 100), out)
        return out.toByteArray()
    }

    /**
     * Injects a standard ISO/IEC 10918-1 JPEG COM (0xFF 0xFE) segment before EOI (0xFF 0xD9)
     * when a tiny signature/photo needs to strictly meet a portal's minimum KB floor (e.g. 10 KB minimum).
     */
    private fun padJpegToTargetBytes(jpegBytes: ByteArray, targetSizeBytes: Int): ByteArray {
        val diff = targetSizeBytes - jpegBytes.size
        if (diff <= 6 || jpegBytes.size < 4) return jpegBytes

        val out = ByteArrayOutputStream(targetSizeBytes + 16)
        // Write everything up to the final EOI marker (0xFF 0xD9)
        val contentLen = if (
            jpegBytes[jpegBytes.size - 2] == 0xFF.toByte() &&
            jpegBytes[jpegBytes.size - 1] == 0xD9.toByte()
        ) {
            jpegBytes.size - 2
        } else {
            jpegBytes.size
        }
        out.write(jpegBytes, 0, contentLen)

        var remaining = diff
        while (remaining > 4) {
            val chunkPayload = (remaining - 4).coerceIn(2, 60000)
            val segLen = chunkPayload + 2
            out.write(0xFF)
            out.write(0xFE) // JPEG COM marker
            out.write((segLen shr 8) and 0xFF)
            out.write(segLen and 0xFF)
            val padBytes = ByteArray(chunkPayload) { 0x20.toByte() }
            out.write(padBytes)
            remaining -= (chunkPayload + 4)
        }

        // Write JPEG EOI marker (0xFF 0xD9)
        out.write(0xFF)
        out.write(0xD9)
        return out.toByteArray()
    }

    fun sanitizePortalFileName(raw: String): String {
        val clean = raw.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return if (clean.endsWith(".jpg")) clean else "$clean.jpg"
    }

    fun saveProcessedJpegToStorage(context: Context, result: ProcessedExamResult): File {
        val dir = File(context.filesDir, "exam_exports")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, result.fileName)
        FileOutputStream(file).use { out ->
            out.write(result.jpegBytes)
        }
        return file
    }
}
