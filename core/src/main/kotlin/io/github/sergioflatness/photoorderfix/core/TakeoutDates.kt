package io.github.sergioflatness.photoorderfix.core

/** One photo's metadata from a Google Takeout export: its file name and when it was taken. */
data class TakeoutEntry(val title: String, val takenMs: Long)

data class DateFix<T>(val item: PlanItem<T>, val newMs: Long)

data class TakeoutMatch<T>(
    /** Items whose Takeout date is earlier than the date the phone shows now. */
    val fixes: List<DateFix<T>>,
    /** Items Takeout knows about whose phone date is already as early or earlier; left alone. */
    val alreadyEarlier: Int,
    /** File names that appear more than once on the phone or with different dates in Takeout; left alone. */
    val ambiguous: Int,
    /** Takeout entries with no file of that name on the phone. */
    val notOnPhone: Int,
)

/**
 * Matches Google Takeout dates to the photos on the phone by file name.
 *
 * Google Photos keeps the date a photo had when it was first backed up. Transfers and re-saves only ever
 * push a date later (to the copy time), so a Takeout date is used only when it is earlier than what the
 * phone shows. This also keeps photos Google dated by their upload time (screenshots without a date
 * inside) from being moved to that upload time.
 */
object TakeoutDates {

    // 1990-01-01, same floor as DateResolver.
    private const val MIN_PLAUSIBLE_MS = 631_152_000_000L
    // Dates closer than this are the same moment written with different precision.
    private const val SAME_MOMENT_MS = 60_000L

    fun <T> match(entries: List<TakeoutEntry>, items: List<PlanItem<T>>, nameOf: (T) -> String): TakeoutMatch<T> {
        val byTitle = entries
            .filter { it.title.isNotBlank() && it.takenMs >= MIN_PLAUSIBLE_MS }
            .groupBy { it.title.lowercase() }
        val onPhone = items.groupBy { nameOf(it.payload).lowercase() }

        val fixes = ArrayList<DateFix<T>>()
        var alreadyEarlier = 0
        var ambiguous = 0
        var notOnPhone = 0
        for ((title, sameName) in byTitle) {
            val phoneItems = onPhone[title]
            if (phoneItems == null) {
                notOnPhone++
                continue
            }
            val dates = sameName.map { it.takenMs }
            if (phoneItems.size > 1 || dates.max() - dates.min() > SAME_MOMENT_MS) {
                ambiguous++
                continue
            }
            val item = phoneItems.single()
            val newMs = dates.min()
            if (newMs < item.date.epochMs - SAME_MOMENT_MS) fixes += DateFix(item, newMs) else alreadyEarlier++
        }
        return TakeoutMatch(fixes, alreadyEarlier, ambiguous, notOnPhone)
    }
}
