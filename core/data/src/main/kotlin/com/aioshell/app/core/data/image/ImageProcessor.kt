package com.aioshell.app.core.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片附件处理：压缩（限尺寸 / 转 JPEG）+ base64 编码。
 * 图片统一存放到应用私有目录 filesDir/attachments，不暴露给其他应用。
 */
object ImageProcessor {

    private const val MAX_SIDE = 1536
    private const val JPEG_QUALITY = 80

    /** 从 content Uri 压缩为 JPEG 文件（私有目录）。 */
    suspend fun compress(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val bitmap = loadBitmap(context, uri, MAX_SIDE)
            ?: throw IllegalArgumentException("无法读取图片")
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val outFile = File(dir, "${System.currentTimeMillis()}_${bitmap.hashCode()}.jpg")
        outFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        bitmap.recycle()
        outFile
    }

    private fun loadBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null

        var sample = 1
        val w = bounds.outWidth; val h = bounds.outHeight
        while (w / sample > maxSide || h / sample > maxSide) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        // 等比缩放至最长边 maxSide
        val (nw, nh) = scaleToFit(src.width, src.height, maxSide)
        val scaled = if (nw != src.width || nh != src.height) {
            Bitmap.createScaledBitmap(src, nw, nh, true)
        } else {
            src
        }
        if (scaled !== src) src.recycle()
        return scaled
    }

    private fun scaleToFit(w: Int, h: Int, maxSide: Int): Pair<Int, Int> {
        val max = maxOf(w, h)
        if (max <= maxSide) return w to h
        val ratio = maxSide.toFloat() / max
        return ((w * ratio).toInt()) to ((h * ratio).toInt())
    }

    /** 读取文件并编码为 base64（NO_WRAP）。 */
    fun encodeToBase64(file: File): String {
        val bytes = file.readBytes()
        return Base64.getEncoder().encodeToString(bytes)
    }
}