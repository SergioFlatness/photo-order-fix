package io.github.sergioflatness.photoorderfix.core

/**
 * What the last fix replaced, so it can be undone while Android keeps the originals in its trash.
 *
 * Stored as plain text: the first line is the time the fix ran, then one "original<TAB>copy" line
 * per item (content URIs as strings).
 */
data class UndoLog(val createdMs: Long, val pairs: List<Pair<String, String>>) {

    /** Whole days left before Android empties its trash, rounded down; 0 means today is the last day. */
    fun daysLeft(nowMs: Long): Int = ((createdMs + TRASH_KEEP_MS - nowMs) / DAY_MS).toInt()

    fun isExpired(nowMs: Long): Boolean = nowMs >= createdMs + TRASH_KEEP_MS

    fun plus(more: List<Pair<String, String>>) = copy(pairs = pairs + more)

    fun serialize(): String = buildString {
        append(createdMs).append('\n')
        pairs.forEach { (original, copy) -> append(original).append('\t').append(copy).append('\n') }
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000

        /**
         * Android deletes trashed media 30 days after trashing. We count from the start of the
         * fix, which is slightly earlier than each batch is trashed, so the shown days never overstate.
         */
        const val TRASH_KEEP_MS = 30 * DAY_MS

        fun parse(text: String): UndoLog? {
            val lines = text.lines().filter { it.isNotBlank() }
            val created = lines.firstOrNull()?.trim()?.toLongOrNull() ?: return null
            val pairs = lines.drop(1).mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) parts[0] to parts[1] else null
            }
            return UndoLog(created, pairs)
        }
    }
}
