package com.moonlib.cosmos.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.min

/**
 * 图像处理核心工具类
 *
 * 职责单一：负责头像图片的防 OOM 降采样、高保真居中正方形裁剪、大图等比压缩至 512x512 并以 JPEG 格式保存。
 */
object ImageUtils {

    private const val TARGET_SIZE = 512 // 统一头像尺寸 512x512

    /**
     * 处理上传的图片：居中裁剪成正方形，大图缩小到 512x512，并以 JPEG 格式保存到目标私有文件。
     * @param context 上下文
     * @param sourceUri 源图片 Uri
     * @param destFile 保存到的目标私有文件
     * @return 处理并保存成功返回 true，否则返回 false
     */
    fun processAndSaveAvatar(context: Context, sourceUri: Uri, destFile: File): Boolean {
        var inputStream: InputStream? = null
        var originalBitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        var fos: FileOutputStream? = null

        try {
            val resolver = context.contentResolver

            // 1. 获取源图片尺寸，计算 inSampleSize 从而减少大图解码时的堆内存开销，防止 OOM
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            inputStream = resolver.openInputStream(sourceUri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return false

            // 计算降采样率 (使解码后的边缘仍略大于或等于目标 512，节约解码所需的内存)
            val minSide = min(srcWidth, srcHeight)
            var inSampleSize = 1
            while (minSide / (inSampleSize * 2) >= TARGET_SIZE) {
                inSampleSize *= 2
            }

            // 2. 解码真正的 Bitmap 并做初步降采样
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = inSampleSize
            }
            inputStream = resolver.openInputStream(sourceUri)
            originalBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions) ?: return false
            inputStream?.close()

            val width = originalBitmap.width
            val height = originalBitmap.height

            // 3. 居中裁剪成完美正方形
            val cropSize = min(width, height)
            val startX = (width - cropSize) / 2
            val startY = (height - cropSize) / 2
            croppedBitmap = Bitmap.createBitmap(originalBitmap, startX, startY, cropSize, cropSize)

            // 4. 等比双线性插值缩放到 512x512 像素（若裁剪后仍大于 512）
            scaledBitmap = if (cropSize > TARGET_SIZE) {
                Bitmap.createScaledBitmap(croppedBitmap, TARGET_SIZE, TARGET_SIZE, true)
            } else {
                croppedBitmap
            }

            // 5. 将处理好的图像以高质量 JPEG 格式写入私有文件
            fos = FileOutputStream(destFile)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            fos.flush()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try { inputStream?.close() } catch (ignored: Exception) {}
            try { fos?.close() } catch (ignored: Exception) {}
            // 严谨清理并回收 Bitmap 堆内存，以防碎片化或内存泄漏
            if (originalBitmap != null && !originalBitmap.isRecycled) {
                originalBitmap.recycle()
            }
            if (croppedBitmap != null && croppedBitmap != originalBitmap && !croppedBitmap.isRecycled) {
                croppedBitmap.recycle()
            }
            if (scaledBitmap != null && scaledBitmap != croppedBitmap && scaledBitmap != originalBitmap && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }
        }
    }
}
