package io.github.sergioflatness.photoorderfix.core

/**
 * Picks the folder a re-saved copy goes into.
 *
 * Android only lets an app create images under DCIM/ or Pictures/, and videos under DCIM/,
 * Movies/ or Pictures/. Items already there are re-saved in the same folder. Items elsewhere
 * (Samsung Smart Switch puts iPhone photos in a top-level iPhoneData/ folder) are re-saved
 * under DCIM/ with their folder path kept, so gallery albums keep their names.
 */
object TargetFolder {

    private val imageRoots = listOf("DCIM/", "Pictures/")
    private val videoRoots = listOf("DCIM/", "Movies/", "Pictures/")

    /** Returns the RELATIVE_PATH for the copy, or null when the item must be left alone. */
    fun of(relativePath: String, isVideo: Boolean): String? {
        val path = relativePath.trimStart('/')
        if (path.isEmpty()) return null
        // App-private folders: other apps own these files.
        if (path.startsWith("Android/", ignoreCase = true)) return null
        val normalized = if (path.endsWith("/")) path else "$path/"
        val roots = if (isVideo) videoRoots else imageRoots
        return if (roots.any { normalized.startsWith(it) }) normalized else "DCIM/$normalized"
    }

    fun isMoved(relativePath: String, target: String): Boolean =
        relativePath.trimStart('/').trimEnd('/') + "/" != target
}
