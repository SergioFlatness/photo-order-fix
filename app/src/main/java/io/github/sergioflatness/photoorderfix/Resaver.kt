package io.github.sergioflatness.photoorderfix

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import io.github.sergioflatness.photoorderfix.core.ExifDate
import io.github.sergioflatness.photoorderfix.core.ResolvedDate
import java.io.File
import java.io.IOException
import java.time.ZoneId

/**
 * Re-saves a photo or video as a fresh MediaStore entry with identical bytes.
 *
 * The fresh entry gets a new "date added", which is what Instagram and similar pickers sort by.
 * The original is only removed later, after the user (or the Media management permission)
 * approves the deletion.
 */
class Resaver(private val resolver: ContentResolver, private val zone: ZoneId = ZoneId.systemDefault()) {

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
            put(MediaStore.MediaColumns.RELATIVE_PATH, item.targetPath)
            put(MediaStore.MediaColumns.DATE_TAKEN, date.epochMs)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val newUri = resolver.insert(collection, values)
            ?: throw IOException("Could not create a new entry for ${item.name}")

        try {
            // Without setRequireOriginal Android hands us the file with its location data blanked out.
            // This throws when the Media location permission is missing, so the item fails instead of
            // being copied without its location.
            val copied = resolver.openInputStream(MediaStore.setRequireOriginal(item.uri)).use { input ->
                input ?: throw IOException("Could not read ${item.name}")
                resolver.openOutputStream(newUri, "w").use { output ->
                    output ?: throw IOException("Could not write ${item.name}")
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
            if (copied != item.sizeBytes) {
                throw IOException("Copied $copied of ${item.sizeBytes} bytes for ${item.name}")
            }
            // Where possible the copy stays byte-for-byte identical to the original: cloud backups
            // (Google Photos) only recognise a re-saved photo as the same one when every byte matches,
            // otherwise the Gallery shows it twice. So the date normally goes into the file time.
            // When the copy is published, Android re-reads the file and drops the DATE_TAKEN we set.
            runCatching { setFileTime(newUri, date.epochMs) }
            resolver.update(newUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            // A photo Android found no capture date in (screenshots, downloads, or an EXIF date it
            // rejected) is labelled by Instagram with its "date added", which for a copy is today.
            // Write the date inside with its time zone so Android accepts it, even though the backup
            // may then keep the photo twice.
            if (!item.isVideo && item.mimeType in exifWritable && !hasCaptureDate(newUri)) {
                runCatching {
                    writeExifDate(newUri, date.epochMs)
                    setFileTime(newUri, date.epochMs)
                }
            }
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

    private fun setFileTime(uri: Uri, epochMs: Long) {
        val path = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: return
        setFileTime(path, epochMs)
    }

    /** Works for files this app created; Android's storage layer allows changing their times. */
    fun setFileTime(path: String, epochMs: Long): Boolean = runCatching { File(path).setLastModified(epochMs) }.getOrDefault(false)

    private fun hasCaptureDate(uri: Uri): Boolean =
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use { c ->
            c.moveToFirst() && !c.isNull(0)
        } ?: false

    /** True when Android shows no capture date for the photo and its format lets us write one. */
    fun needsDateInside(item: DeviceMedia): Boolean =
        !item.hasMediaStoreDate && !item.isVideo && item.mimeType in exifWritable

    /**
     * Gives an existing photo a new date: written into the file where the format allows,
     * and as the file time, which the Gallery falls back to. Returns false if neither worked.
     */
    fun setDate(item: DeviceMedia, epochMs: Long): Boolean {
        val exif = !item.isVideo && item.mimeType in exifWritable &&
            runCatching { writeExifDate(item.uri, epochMs) }.isSuccess
        val time = item.dataPath?.let { setFileTime(it, epochMs) } ?: false
        return exif || time
    }

    private fun writeExifDate(uri: Uri, epochMs: Long) {
        val text = ExifDate.formatDate(epochMs, zone)
        // Without the zone tags Android throws the date away once the file time drifts.
        val offset = ExifDate.formatOffset(epochMs, zone)
        resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            ExifInterface(pfd.fileDescriptor).apply {
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, text)
                setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, text)
                setAttribute(ExifInterface.TAG_DATETIME, text)
                setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
                setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offset)
                setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
                saveAttributes()
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 256 * 1024
    }
}
