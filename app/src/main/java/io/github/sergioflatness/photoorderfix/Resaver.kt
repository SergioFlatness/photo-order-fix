package io.github.sergioflatness.photoorderfix

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import io.github.sergioflatness.photoorderfix.core.DateSource
import io.github.sergioflatness.photoorderfix.core.ResolvedDate
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Re-saves a photo or video as a fresh MediaStore entry with identical bytes.
 *
 * The fresh entry gets a new "date added", which is what Instagram and similar pickers sort by.
 * The original is only removed later, after the user (or the Media management permission)
 * approves the deletion.
 */
class Resaver(private val resolver: ContentResolver, private val zone: ZoneId = ZoneId.systemDefault()) {

    private val exifFormat = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    // Formats androidx ExifInterface can write into.
    private val exifWritable = setOf("image/jpeg", "image/png", "image/webp")

    fun copy(item: DeviceMedia, date: ResolvedDate): Uri {
        val collection = if (item.isVideo) {
            MediaStore.Video.Media.getContentUri(item.volume)
        } else {
            MediaStore.Images.Media.getContentUri(item.volume)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, item.relativePath)
            put(MediaStore.MediaColumns.DATE_TAKEN, date.epochMs)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val newUri = resolver.insert(collection, values)
            ?: throw IOException("Could not create a new entry for ${item.name}")

        try {
            val copied = resolver.openInputStream(item.uri).use { input ->
                input ?: throw IOException("Could not read ${item.name}")
                resolver.openOutputStream(newUri, "w").use { output ->
                    output ?: throw IOException("Could not write ${item.name}")
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
            if (copied != item.sizeBytes) {
                throw IOException("Copied $copied of ${item.sizeBytes} bytes for ${item.name}")
            }
            // Screenshots and other files without a capture date: write the date into the copy
            // so it survives future moves. Best effort, the copy is still valid without it.
            if (!item.isVideo && date.source != DateSource.TAKEN && item.mimeType in exifWritable) {
                runCatching { writeExifDate(newUri, date.epochMs) }
            }
            resolver.update(newUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return newUri
        } catch (e: Exception) {
            runCatching { resolver.delete(newUri, null, null) }
            throw e
        }
    }

    /** After the original is gone, give the copy back the exact original file name. */
    fun restoreName(copyUri: Uri, name: String) {
        runCatching {
            resolver.update(copyUri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }, null, null)
        }
    }

    /** Undo a copy, used when the user declines deleting the originals. */
    fun discard(copyUri: Uri) {
        runCatching { resolver.delete(copyUri, null, null) }
    }

    private fun writeExifDate(uri: Uri, epochMs: Long) {
        val text = exifFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))
        resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            ExifInterface(pfd.fileDescriptor).apply {
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, text)
                setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, text)
                setAttribute(ExifInterface.TAG_DATETIME, text)
                saveAttributes()
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 256 * 1024
    }
}
