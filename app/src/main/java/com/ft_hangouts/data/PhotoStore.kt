package com.ft_hangouts.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import java.io.File

// Stores contact photos as files inside the app's own private storage.
// We copy the picked image instead of saving the picker's content:// URI, because that
// URI's read permission can expire (reboot, app restart, user revoking access) and the
// photo would silently disappear. A copy we own always loads.
object PhotoStore {

    private const val PHOTO_DIR = "contact_photos"
    private const val MAX_DIMENSION = 1024
    private const val THUMB_DIMENSION = 256

    // Small in-memory cache so scrolling the contact list doesn't re-decode the same
    // photo from disk on every row bind.
    private val thumbCache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // Copies the picked image into private storage. Returns the new file path, or null
    // if the image couldn't be read or decoded.
    fun savePhoto(context: Context, sourceUri: Uri): String? {
        var file: File? = null
        return try {
            val bitmap = decodeScaled(context, sourceUri, MAX_DIMENSION) ?: return null
            val rotated = applyExifRotation(context, sourceUri, bitmap)
            val dir = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }
            file = File(dir, "contact_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            rotated.recycle()
            file.absolutePath
        } catch (e: Exception) {
            // Don't leave a half-written file behind if compressing failed.
            runCatching { file?.delete() }
            null
        }
    }

    // Full-size load, for the detail screen's single large avatar.
    fun loadPhoto(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return try {
            if (!File(path).exists()) null else BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            null
        }
    }

    // Small cached load, for list rows. Decoding a 1024px bitmap per row on the UI
    // thread makes scrolling stutter and risks OutOfMemoryError.
    fun loadThumbnail(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        thumbCache.get(path)?.let { return it }
        return try {
            val file = File(path)
            if (!file.exists()) return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, THUMB_DIMENSION)
            }
            BitmapFactory.decodeFile(path, options)?.also { thumbCache.put(path, it) }
        } catch (e: Exception) {
            null
        }
    }

    fun deletePhoto(path: String?) {
        if (path.isNullOrBlank()) return
        thumbCache.remove(path)
        runCatching { File(path).delete() }
    }

    // Decodes at a reduced size. A full-resolution camera photo can be 50MP+, and
    // decoding one at native size throws OutOfMemoryError.
    private fun decodeScaled(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        // First pass measures the image without loading its pixels. Note decodeStream
        // returns null here BY DESIGN when inJustDecodeBounds is set — the result we
        // want lands in `bounds`, so the null check belongs on the stream, not the decode.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    // Photos taken in portrait are usually stored landscape with an EXIF "rotate" tag.
    // Without applying it here the avatar shows up sideways, permanently.
    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val orientation = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: return bitmap

            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> return bitmap
            }

            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (e: Exception) {
            bitmap
        }
    }
}
