package za.co.mjrsolutions.shared.files.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import za.co.mjrsolutions.shared.files.internal.Report
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Turns a photo into the JPEG that leaves the tablet (hael's PhotoImageEncoder): at most
 * 1920x1080, quality 60, with the given lines written in red at the top. The server upload and
 * the Storage copy both use it, so both hold the same picture.
 */
object PhotoEncoder {

    private const val TARGET_WIDTH = 1920
    private const val TARGET_HEIGHT = 1080
    private const val JPEG_QUALITY = 60

    /** hael's overlay, in this order; empty values are left out. */
    @JvmStatic
    fun overlayLines(reportId: String?, clientName: String?, farmName: String?, landName: String?,
                     testIndex: String?, gps: String?): Array<String?> {
        val lines = arrayOfNulls<String>(6)
        var i = 0
        if (!reportId.isNullOrEmpty()) lines[i++] = "Verslag: $reportId"
        if (!clientName.isNullOrEmpty()) lines[i++] = "Kliënt: $clientName"
        if (!farmName.isNullOrEmpty()) lines[i++] = "Plaas naam: $farmName"
        if (!landName.isNullOrEmpty()) lines[i++] = "Land: $landName"
        if (!testIndex.isNullOrEmpty()) lines[i++] = "Toets nr: $testIndex"
        if (!gps.isNullOrEmpty()) lines[i] = "GPS: $gps"
        return lines
    }

    /** The stamped JPEG, or null when the photo cannot be read (the reason goes to Crashlytics). */
    @JvmStatic
    fun stampedJpeg(context: Context, uri: Uri, overlayLines: Array<String?>): ByteArray? {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Report.exception(IOException("Failed to read image dimensions from URI: $uri"))
                return null
            }
            val decode = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, TARGET_WIDTH, TARGET_HEIGHT)
                inMutable = true
            }
            var bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, decode) }
            if (bitmap == null) {
                Report.exception(IOException("Failed to decode Bitmap from URI: $uri"))
                return null
            }
            if (bitmap.width > TARGET_WIDTH || bitmap.height > TARGET_HEIGHT) {
                val resized = resize(bitmap, TARGET_WIDTH, TARGET_HEIGHT)
                bitmap.recycle()
                bitmap = resized
            }
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.RED
                textSize = 60f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val shadow = Paint(paint).apply { color = Color.BLACK; alpha = 180 }
            var lineIndex = 0
            for (line in overlayLines) {
                if (line == null) continue
                val y = 60 + lineIndex * 60
                canvas.drawText(line, 22f, (y + 2).toFloat(), shadow)
                canvas.drawText(line, 20f, y.toFloat(), paint)
                lineIndex++
            }
            val out = ByteArrayOutputStream()
            val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            if (!compressed) {
                Report.exception(IOException("Failed to compress Bitmap to JPEG format."))
                return null
            }
            return out.toByteArray()
        } catch (e: Exception) {
            Report.exception(e)
            return null
        } catch (e: OutOfMemoryError) {
            Report.exception(IOException("Out of memory encoding $uri", e))
            return null
        }
    }

    /** The power-of-two subsample BitmapFactory decodes at, so the decode stays near the target. */
    private fun sampleSize(rawWidth: Int, rawHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun resize(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val ratio = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        return Bitmap.createScaledBitmap(bitmap, Math.round(ratio * bitmap.width), Math.round(ratio * bitmap.height), true)
    }
}
