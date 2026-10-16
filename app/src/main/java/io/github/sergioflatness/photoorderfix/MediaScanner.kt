package io.github.sergioflatness.photoorderfix

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore
import io.github.sergioflatness.photoorderfix.core.DateResolver
import io.github.sergioflatness.photoorderfix.core.PlanItem
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
)

object MediaScanner {

    // MediaStore only lets an app create images under DCIM/ or Pictures/, and videos under
    // DCIM/, Movies/ or Pictures/. Files elsewhere could not be re-saved in the same folder.
    private val imageRoots = listOf("DCIM/", "Pictures/")
    private val videoRoots = listOf("DCIM/", "Movies/", "Pictures/")

    fun scan(resolver: ContentResolver, zone: ZoneId = ZoneId.systemDefault()): List<PlanItem<DeviceMedia>> {
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

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val isVideo = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val volume = c.getString(volCol) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                val name = c.getString(nameCol) ?: continue
                val path = c.getString(pathCol) ?: ""
                val mime = c.getString(mimeCol) ?: continue
                val size = c.getLong(sizeCol)
                val taken = if (c.isNull(takenCol)) null else c.getLong(takenCol)

                val uri = if (isVideo) {
                    MediaStore.Video.Media.getContentUri(volume, id)
                } else {
                    MediaStore.Images.Media.getContentUri(volume, id)
                }
                val roots = if (isVideo) videoRoots else imageRoots
                val media = DeviceMedia(id, uri, volume, isVideo, name, path, mime, size)

                result += PlanItem(
                    id = id,
                    addedSec = c.getLong(addedCol),
                    date = DateResolver.resolve(taken, name, c.getLong(modCol), zone),
                    sizeBytes = size,
                    eligible = size > 0 && roots.any { path.startsWith(it) },
                    payload = media,
                )
            }
        }
        return result
    }
}
