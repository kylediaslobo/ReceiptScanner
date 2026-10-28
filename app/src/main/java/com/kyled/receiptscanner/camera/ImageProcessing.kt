package com.kyled.receiptscanner.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Long edge of uploaded photos. Plenty for reading a receipt, and keeps uploads, storage and image tokens small. */
private const val MAX_EDGE_PX = 1600
private const val JPEG_QUALITY = 85

/**
 * Rotates the camera frame upright, scales it down to [MAX_EDGE_PX] on the long edge,
 * and writes it as a JPEG into [dir]. Returns the file.
 */
fun saveForUpload(source: Bitmap, rotationDegrees: Int, dir: File): File {
    val longEdge = max(source.width, source.height)
    val scale = if (longEdge > MAX_EDGE_PX) MAX_EDGE_PX.toFloat() / longEdge else 1f

    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotationDegrees.toFloat())
    }
    val prepared = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)

    dir.mkdirs()
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    file.outputStream().use { prepared.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }

    if (prepared !== source) prepared.recycle()
    return file
}

