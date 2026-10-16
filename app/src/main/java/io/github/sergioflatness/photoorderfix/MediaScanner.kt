package io.github.sergioflatness.photoorderfix

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import io.github.sergioflatness.photoorderfix.core.DateResolver
import io.github.sergioflatness.photoorderfix.core.PlanItem
import io.github.sergioflatness.photoorderfix.core.TargetFolder
import io.github.sergioflatness.photoorderfix.core.VideoDate
import java.nio.ByteBuffer
import java.time.ZoneId

/** A photo or video in the phone's media list (MediaStore). */
data class DeviceMedia(
    val id: Long,
    val uri: Uri,
    val volume: String,
    val isVideo: Boolean,
    val name: String,
    val relativePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** Folder the re-saved copy goes into; differs from relativePath for folders Android won't let us write to. */
    val targetPath: String,
    /** Absolute file path, used to fix the file time of copies this app made. */
    val dataPath: String?,
    val modifiedSec: Long,
    /** False when Android has no capture date for it, so the Gallery shows the file time. */
    val hasMediaStoreDate: Boolean,
    val ownedByApp: Boolean,
)

object MediaScanner {

    fun scan(context: Context, zone: ZoneId = ZoneId.systemDefault()): List<PlanItem<DeviceMedia>> {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.VOLUME_NAME,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )

        val result = mutableListOf<PlanItem<DeviceMedia>>()
        resolver.query(collection, projection, selection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val volCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.VOLUME_NAME)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val modCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val ownerCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val isVideo = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val volume = c.getString(volCol) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                val name = c.getString(nameCol) ?: continue
                val path = c.getString(pathCol) ?: ""
                val mime = c.getString(mimeCol) ?: continue
                val size = c.getLong(sizeCol)
                val storeTaken = if (c.isNull(takenCol)) null else c.getLong(takenCol)
                val modified = c.getLong(modCol)

                val uri = if (isVideo) {
                    MediaStore.Video.Media.getContentUri(volume, id)
                } else {
                    MediaStore.Images.Media.getContentUri(volume, id)
                }
                // iPhone videos keep their date in a tag Android doesn't read; look for it ourselves.
                val taken = storeTaken ?: if (isVideo) readVideoDate(resolver, uri) else null
                val target = TargetFolder.of(path, isVideo)
                val media = DeviceMedia(
                    id, uri, volume, isVideo, name, path, mime, size, target ?: path,
                    dataPath = c.getString(dataCol),
                    modifiedSec = modified,
                    hasMediaStoreDate = storeTaken != null,
                    ownedByApp = c.getString(ownerCol) == context.packageName,
                )

                result += PlanItem(
                    id = id,
                    addedSec = c.getLong(addedCol),
                    date = DateResolver.resolve(taken, name, modified, zone),
                    sizeBytes = size,
                    eligible = size > 0 && target != null,
                    payload = media,
                )
            }
        }
        return result
    }

    private fun readVideoDate(resolver: ContentResolver, uri: Uri): Long? = runCatching {
        val pfd = resolver.openFileDescriptor(uri, "r") ?: return@runCatching null
        ParcelFileDescriptor.AutoCloseInputStream(pfd).channel.use { channel ->
            VideoDate.read({ pos, buf, len -> channel.read(ByteBuffer.wrap(buf, 0, len), pos) }, channel.size())
        }
    }.getOrNull()
}
