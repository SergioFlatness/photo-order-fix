package io.github.sergioflatness.photoorderfix.core

/**
 * One photo or video as the planner sees it.
 *
 * @param addedSec when the file entered the phone's media list. Apps like Instagram
 *   sort their pickers by this value, newest first.
 * @param eligible false when the app cannot re-save the file in place (for example it
 *   lives outside DCIM/, Pictures/ or Movies/), so it is left alone.
 */
data class PlanItem<T>(
    val id: Long,
    val addedSec: Long,
    val date: ResolvedDate,
    val sizeBytes: Long,
    val eligible: Boolean,
    val payload: T,
)

data class ReorderPlan<T>(
    /** Items already in the right place; they are not touched. */
    val kept: List<PlanItem<T>>,
    /** Items to re-save, oldest capture first. Re-saving in this order makes the newest one land on top. */
    val toResave: List<PlanItem<T>>,
    val skipped: List<PlanItem<T>>,
) {
    val resaveBytes: Long get() = toResave.sumOf { it.sizeBytes }
    fun countBySource(source: DateSource): Int =
        (kept + toResave).count { it.date.source == source }
}

object ReorderPlanner {

    // Photos taken while the scan runs are not "in the future".
    private const val FUTURE_SLACK_SEC = 10 * 60L

    /**
     * Re-saving a file gives it a brand new "added" time, newer than everything else.
     * So the oldest stretch of the library whose added times already rise with the
     * capture dates can stay as it is, and everything after the first out-of-order
     * item has to be re-saved, oldest first.
     *
     * An item whose added time lies in the future (a wrong clock, or an app that wrote a bad
     * value) would sit above every copy we make, so the photos after it could never be fixed.
     * It counts as out of place too: its own copy gets a real added time.
     */
    fun <T> plan(items: List<PlanItem<T>>, nowSec: Long = System.currentTimeMillis() / 1000): ReorderPlan<T> {
        val (eligible, skipped) = items.partition { it.eligible }
        val chronological = eligible.sortedWith(
            compareBy<PlanItem<T>>({ it.date.epochMs }, { it.addedSec }, { it.id })
        )

        var keep = 0
        var lastAdded = Long.MIN_VALUE
        for (item in chronological) {
            if (item.addedSec < lastAdded || item.addedSec > nowSec + FUTURE_SLACK_SEC) break
            lastAdded = item.addedSec
            keep++
        }
        return ReorderPlan(
            kept = chronological.take(keep),
            toResave = chronological.drop(keep),
            skipped = skipped,
        )
    }

    /**
     * Splits the work so that one batch never needs more free space than [maxBytes].
     * Each batch ends with one "may I delete the originals?" prompt.
     * A single file larger than [maxBytes] still gets its own batch.
     */
    fun <T> batches(items: List<PlanItem<T>>, maxBytes: Long, maxCount: Int): List<List<PlanItem<T>>> {
        require(maxCount > 0)
        val result = mutableListOf<List<PlanItem<T>>>()
        var current = mutableListOf<PlanItem<T>>()
        var bytes = 0L
        for (item in items) {
            val full = current.size >= maxCount || (current.isNotEmpty() && bytes + item.sizeBytes > maxBytes)
            if (full) {
                result += current
                current = mutableListOf()
                bytes = 0
            }
            current += item
            bytes += item.sizeBytes
        }
        if (current.isNotEmpty()) result += current
        return result
    }
}
