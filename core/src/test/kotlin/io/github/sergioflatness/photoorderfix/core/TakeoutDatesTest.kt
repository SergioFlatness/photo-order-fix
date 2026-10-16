package io.github.sergioflatness.photoorderfix.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TakeoutDatesTest {

    private val day = 24L * 60 * 60 * 1000
    private val sept23 = 1_790_000_000_000L
    private val today = sept23 + 23 * day

    private fun item(id: Long, name: String, ms: Long) =
        PlanItem(id, 0, ResolvedDate(ms, DateSource.TAKEN), 10, true, payload = name)

    private fun match(entries: List<TakeoutEntry>, items: List<PlanItem<String>>) =
        TakeoutDates.match(entries, items) { it }

    @Test
    fun `earlier Takeout date replaces a copy dated today`() {
        val result = match(listOf(TakeoutEntry("Gemini_Generated_Image.png", sept23)), listOf(item(1, "gemini_generated_image.PNG", today)))
        assertEquals(listOf(1L to sept23), result.fixes.map { it.item.id to it.newMs })
    }

    @Test
    fun `later Takeout date such as an upload time is ignored`() {
        val screenshot = item(1, "20231120_151017.PNG", sept23 - 1000 * day)
        val result = match(listOf(TakeoutEntry("20231120_151017.PNG", sept23)), listOf(screenshot))
        assertEquals(0, result.fixes.size)
        assertEquals(1, result.alreadyEarlier)
    }

    @Test
    fun `names shared by several files or dates are left alone`() {
        val twoOnPhone = match(
            listOf(TakeoutEntry("image.jpg", sept23)),
            listOf(item(1, "image.jpg", today), item(2, "image.jpg", today)),
        )
        assertEquals(1, twoOnPhone.ambiguous)
        val twoDates = match(
            listOf(TakeoutEntry("a.jpg", sept23), TakeoutEntry("a.jpg", sept23 + day)),
            listOf(item(1, "a.jpg", today)),
        )
        assertEquals(1, twoDates.ambiguous)
        assertEquals(0, twoDates.fixes.size + twoOnPhone.fixes.size)
    }

    @Test
    fun `entries without a phone file or with bogus dates are counted or dropped`() {
        val result = match(
            listOf(TakeoutEntry("gone.jpg", sept23), TakeoutEntry("zero.jpg", 0)),
            listOf(item(1, "zero.jpg", today)),
        )
        assertEquals(1, result.notOnPhone)
        assertEquals(0, result.fixes.size)
    }
}
