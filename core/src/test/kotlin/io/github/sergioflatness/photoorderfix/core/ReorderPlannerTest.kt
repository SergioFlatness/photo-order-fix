package io.github.sergioflatness.photoorderfix.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ReorderPlannerTest {

    private fun item(id: Long, added: Long, taken: Long, size: Long = 10, eligible: Boolean = true) =
        PlanItem(id, added, ResolvedDate(taken, DateSource.TAKEN), size, eligible, payload = id)

    @Test
    fun `already ordered library needs nothing`() {
        val plan = ReorderPlanner.plan(listOf(item(1, 100, 1_000), item(2, 200, 2_000), item(3, 300, 3_000)))
        assertEquals(3, plan.kept.size)
        assertEquals(0, plan.toResave.size)
    }

    @Test
    fun `reversed iPhone batch is fully re-saved oldest first`() {
        // Transfer copied the newest photo first, so the oldest one has the newest "added" time.
        val phone = listOf(item(1, 103, 1_000), item(2, 102, 2_000), item(3, 101, 3_000))
        // Galaxy photos taken after the move are fine on their own...
        val galaxy = listOf(item(4, 500, 9_000), item(5, 600, 9_500))
        val plan = ReorderPlanner.plan(galaxy + phone)

        assertEquals(listOf(1L), plan.kept.map { it.id })
        // ...but they must be re-saved too, or they would sink below the re-saved iPhone photos.
        assertEquals(listOf(2L, 3L, 4L, 5L), plan.toResave.map { it.id })
    }

    @Test
    fun `a video added after newer photos only needs those photos re-saved`() {
        val photos = listOf(item(1, 100, 1_000), item(2, 101, 3_000))
        val video = item(3, 200, 2_000) // taken between the photos, added after them
        val plan = ReorderPlanner.plan(photos + video)
        // Re-saving only photo 2 gives it the newest "added" time: 2, 3, 1 is newest-first.
        assertEquals(listOf(1L, 3L), plan.kept.map { it.id })
        assertEquals(listOf(2L), plan.toResave.map { it.id })
    }

    @Test
    fun `an item added in the future is re-saved with everything after it`() {
        // Item 2 claims to be added later than any copy could be, so 3 and 4 would stay stuck below it.
        val items = listOf(item(1, 100, 1_000), item(2, 99_999, 2_000), item(3, 300, 3_000), item(4, 400, 4_000))
        val plan = ReorderPlanner.plan(items, nowSec = 1_000)
        assertEquals(listOf(1L), plan.kept.map { it.id })
        assertEquals(listOf(2L, 3L, 4L), plan.toResave.map { it.id })
    }

    @Test
    fun `ineligible items are left alone`() {
        val plan = ReorderPlanner.plan(listOf(item(1, 100, 2_000), item(2, 50, 1_000, eligible = false)))
        assertEquals(listOf(1L), plan.kept.map { it.id })
        assertEquals(listOf(2L), plan.skipped.map { it.id })
    }

    @Test
    fun `batches respect size and count limits`() {
        val items = (1L..5L).map { item(it, it, it, size = 40) }
        assertEquals(listOf(2, 2, 1), ReorderPlanner.batches(items, maxBytes = 100, maxCount = 10).map { it.size })
        assertEquals(listOf(3, 2), ReorderPlanner.batches(items, maxBytes = 1_000, maxCount = 3).map { it.size })
        val huge = listOf(item(9, 9, 9, size = 500))
        assertEquals(listOf(1), ReorderPlanner.batches(huge, maxBytes = 100, maxCount = 10).map { it.size })
    }
}
