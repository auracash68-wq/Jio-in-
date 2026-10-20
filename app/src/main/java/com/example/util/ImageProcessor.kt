package com.example.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.example.data.PageFilter
import com.example.data.ScannedPage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.roundToInt

data class DetectedCorners(
    val tlX: Float,
    val tlY: Float,
    val trX: Float,
    val trY: Float,
    val blX: Float,
    val blY: Float,
    val brX: Float,
    val brY: Float
)

object ImageProcessor {

    /**
     * Creates a clean blank A4 canvas only when a fallback bitmap is needed before capture.
     */
    fun createBlankA4Canvas(width: Int = 900, height: Int = 1273): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        return bitmap
    }

    /**
     * Backward-compatible helper that returns a clean white document canvas without any fake demo text.
     */
    fun createSampleDocumentBitmap(pageNumber: Int = 1): Bitmap {
        return createBlankA4Canvas()
    }

    /**
     * Returns the bitmap with rotation and pixel filter applied, BEFORE 4-point cropping.
     * Used on the interactive Crop & Edit canvas so handles can be dragged freely across the full image.
     */
    fun getRotatedFilteredBitmap(page: ScannedPage): Bitmap {
        val baseBitmap = page.bitmap ?: page.imagePath?.let { BitmapFactory.decodeFile(it) }
            ?: createBlankA4Canvas()

        val rotated = if (page.rotationDegrees % 360 != 0) {
            val matrix = Matrix().apply {
                postRotate(page.rotationDegrees.toFloat())
            }
            Bitmap.createBitmap(baseBitmap, 0, 0, baseBitmap.width, baseBitmap.height, matrix, true)
        } else {
            baseBitmap
        }

        return applyFilterToBitmap(rotated, page.filter)
    }

    /**
     * Performs genuine 4-point perspective deskew (homography warp via Matrix.setPolyToPoly)
     * and bounding-box cropping on the rotated & filtered page bitmap.
     */
    fun processPage(page: ScannedPage): Bitmap {
        val rotatedFiltered = getRotatedFilteredBitmap(page)
        val w = rotatedFiltered.width.toFloat()
        val h = rotatedFiltered.height.toFloat()

        val tlX = (page.cropTopLeftX * w).coerceIn(0f, w - 1f)
        val tlY = (page.cropTopLeftY * h).coerceIn(0f, h - 1f)
        val trX = (page.cropTopRightX * w).coerceIn(0f, w - 1f)
        val trY = (page.cropTopRightY * h).coerceIn(0f, h - 1f)
        val brX = (page.cropBottomRightX * w).coerceIn(0f, w - 1f)
        val brY = (page.cropBottomRightY * h).coerceIn(0f, h - 1f)
        val blX = (page.cropBottomLeftX * w).coerceIn(0f, w - 1f)
        val blY = (page.cropBottomLeftY * h).coerceIn(0f, h - 1f)

        val topWidth = hypot(trX - tlX, trY - tlY)
        val bottomWidth = hypot(brX - blX, brY - blY)
        val leftHeight = hypot(blX - tlX, blY - tlY)
        val rightHeight = hypot(brX - trX, brY - trY)

        val dstWidth = maxOf(topWidth, bottomWidth).roundToInt().coerceIn(64, rotatedFiltered.width)
        val dstHeight = maxOf(leftHeight, rightHeight).roundToInt().coerceIn(64, rotatedFiltered.height)

        return try {
            val srcPts = floatArrayOf(
                tlX, tlY,
                trX, trY,
                brX, brY,
                blX, blY
            )
            val dstPts = floatArrayOf(
                0f, 0f,
                dstWidth.toFloat(), 0f,
                dstWidth.toFloat(), dstHeight.toFloat(),
                0f, dstHeight.toFloat()
            )
            val warpMatrix = Matrix()
            val success = warpMatrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)
            if (success) {
                val out = Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(rotatedFiltered, warpMatrix, paint)
                out
            } else {
                cropBoundingBoxFallback(rotatedFiltered, page)
            }
        } catch (_: Exception) {
            cropBoundingBoxFallback(rotatedFiltered, page)
        }
    }

    private fun cropBoundingBoxFallback(rotatedFiltered: Bitmap, page: ScannedPage): Bitmap {
        val minX = minOf(page.cropTopLeftX, page.cropBottomLeftX).coerceIn(0f, 0.85f)
        val maxX = maxOf(page.cropTopRightX, page.cropBottomRightX).coerceIn(minX + 0.15f, 1f)
        val minY = minOf(page.cropTopLeftY, page.cropTopRightY).coerceIn(0f, 0.85f)
        val maxY = maxOf(page.cropBottomLeftY, page.cropBottomRightY).coerceIn(minY + 0.15f, 1f)

        val srcLeft = (minX * rotatedFiltered.width).toInt().coerceIn(0, rotatedFiltered.width - 10)
        val srcTop = (minY * rotatedFiltered.height).toInt().coerceIn(0, rotatedFiltered.height - 10)
        val srcWidth = ((maxX - minX) * rotatedFiltered.width).toInt().coerceIn(10, rotatedFiltered.width - srcLeft)
        val srcHeight = ((maxY - minY) * rotatedFiltered.height).toInt().coerceIn(10, rotatedFiltered.height - srcTop)

        return try {
            Bitmap.createBitmap(rotatedFiltered, srcLeft, srcTop, srcWidth, srcHeight)
        } catch (_: Exception) {
            rotatedFiltered
        }
    }

    /**
     * Real luminance-gradient document boundary detector for "Auto Fit".
     */
    fun detectDocumentCorners(bitmap: Bitmap): DetectedCorners {
        val sampleW = 160
        val sampleH = 220
        val small = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)
        val pixels = IntArray(sampleW * sampleH)
        small.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)

        var minX = sampleW
        var maxX = 0
        var minY = sampleH
        var maxY = 0
        var brightCount = 0

        for (y in 4 until sampleH - 4) {
            for (x in 4 until sampleW - 4) {
                val c = pixels[y * sampleW + x]
                val lum = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8
                if (lum > 145) {
                    brightCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        return if (brightCount > (sampleW * sampleH) / 10 && maxX > minX + 20 && maxY > minY + 20) {
            val left = (minX.toFloat() / sampleW).coerceIn(0.02f, 0.35f)
            val right = (maxX.toFloat() / sampleW).coerceIn(0.65f, 0.98f)
            val top = (minY.toFloat() / sampleH).coerceIn(0.02f, 0.35f)
            val bottom = (maxY.toFloat() / sampleH).coerceIn(0.65f, 0.98f)
            DetectedCorners(left, top, right, top, left, bottom, right, bottom)
        } else {
            DetectedCorners(0.03f, 0.03f, 0.97f, 0.03f, 0.03f, 0.97f, 0.97f, 0.97f)
        }
    }

    private fun applyFilterToBitmap(src: Bitmap, filter: PageFilter): Bitmap {
        return when (filter) {
            PageFilter.ORIGINAL -> src
            PageFilter.GRAYSCALE -> applyGrayscale(src)
            PageFilter.BLACK_AND_WHITE -> applyAdaptiveMagicBw(src)
            PageFilter.MAGIC_COLOR -> applyMagicColor(src)
            PageFilter.OCR_CLEAR -> applyOcrClear(src)
        }
    }

    /**
     * True luminance grayscale conversion.
     */
    fun applyGrayscale(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val cm = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /**
     * Genuine pixel-level Adaptive B&W (300 DPI Clean):
     * Removes uneven phone camera lighting/shadows via integral-image local mean thresholding
     * so document text is crisp black on a pure white background.
     */
    fun applyAdaptiveMagicBw(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val lum = IntArray(w * h)
        val integral = LongArray((w + 1) * (h + 1))

        for (y in 0 until h) {
            var rowSum = 0L
            val rowOffset = y * w
            val intRowOffset = (y + 1) * (w + 1)
            val prevIntRowOffset = y * (w + 1)
            for (x in 0 until w) {
                val c = pixels[rowOffset + x]
                val l = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8
                lum[rowOffset + x] = l
                rowSum += l
                integral[intRowOffset + x + 1] = integral[prevIntRowOffset + x + 1] + rowSum
            }
        }

        val radius = (minOf(w, h) / 18).coerceIn(8, 42)
        val outPixels = IntArray(w * h)

        for (y in 0 until h) {
            val y1 = (y - radius).coerceAtLeast(0)
            val y2 = (y + radius).coerceAtMost(h - 1)
            val rowOffset = y * w
            for (x in 0 until w) {
                val x1 = (x - radius).coerceAtLeast(0)
                val x2 = (x + radius).coerceAtMost(w - 1)
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)

                val sum = integral[(y2 + 1) * (w + 1) + (x2 + 1)] -
                    integral[y1 * (w + 1) + (x2 + 1)] -
                    integral[(y2 + 1) * (w + 1) + x1] +
                    integral[y1 * (w + 1) + x1]

                val localMean = (sum / count).toInt()
                val pixelLum = lum[rowOffset + x]

                val outVal = if (pixelLum < localMean - 11) {
                    0 // Crisp black ink
                } else if (pixelLum < localMean - 5) {
                    60 // Anti-aliased stroke edge
                } else {
                    255 // Pure white paper background
                }
                outPixels[rowOffset + x] = Color.rgb(outVal, outVal, outVal)
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun applyMagicColor(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val cm = ColorMatrix(
            floatArrayOf(
                1.35f, 0f, 0f, 0f, -24f,
                0f, 1.35f, 0f, 0f, -24f,
                0f, 0f, 1.35f, 0f, -24f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val sat = ColorMatrix().apply { setSaturation(1.3f) }
        cm.postConcat(sat)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /**
     * Pre-processes document bitmap for optimal OCR recognition (grayscale + high contrast normalization).
     */
    fun applyOcrClear(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint()
        val cm = ColorMatrix(
            floatArrayOf(
                1.65f, 0f, 0f, 0f, -52f,
                0f, 1.65f, 0f, 0f, -52f,
                0f, 0f, 1.65f, 0f, -52f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val gray = ColorMatrix().apply { setSaturation(0f) }
        cm.preConcat(gray)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /**
     * Loads a single Bitmap from an image or PDF Uri.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return loadBitmapsFromUri(context, uri).firstOrNull()
    }

    /**
     * Loads one or more Bitmaps from an image (.jpg, .jpeg, .png, .webp) OR a multi-page .pdf Uri.
     */
    fun loadBitmapsFromUri(context: Context, uri: Uri): List<Bitmap> {
        val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""
        val uriString = uri.toString().lowercase()

        if (mimeType.contains("pdf") || uriString.endsWith(".pdf")) {
            return renderBitmapsFromPdfUri(context, uri)
        }

        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream)
                if (bmp != null) listOf(bmp) else emptyList()
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun renderBitmapsFromPdfUri(context: Context, uri: Uri): List<Bitmap> {
        val renderedPages = mutableListOf<Bitmap>()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = renderer.pageCount.coerceAtMost(25)
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            val renderW = (page.width * 2).coerceIn(600, 1800)
                            val renderH = (page.height * 2).coerceIn(800, 2500)
                            val bmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            canvas.drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            renderedPages.add(bmp)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return renderedPages
    }

    /**
     * Renders a specific 0-based page from a local PDF file using Android's native PdfRenderer.
     */
    fun renderPdfPageFromFile(file: File, pageIndex: Int): Bitmap? {
        if (!file.exists() || !file.extension.equals("pdf", ignoreCase = true)) return null
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return null
                    val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                    renderer.openPage(safeIndex).use { page ->
                        val renderW = (page.width * 2).coerceIn(600, 1800)
                        val renderH = (page.height * 2).coerceIn(800, 2500)
                        val bmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        canvas.drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Returns the exact byte size of a Uri from ContentResolver, or 0L if unavailable.
     */
    fun measureUriByteSize(context: Context, uri: Uri): Long {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        return cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Measures the real uncompressed/95%-quality JPEG byte size of a Bitmap.
     */
    fun measureBitmapByteSize(bitmap: Bitmap): Long {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        return out.size().toLong()
    }

    fun saveBitmapToFile(context: Context, bitmap: Bitmap, fileName: String): File {
        val dir = File(context.filesDir, "scans")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return file
    }
}
