package io.github.sergioflatness.photoorderfix.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UndoLogTest {

    private val day = UndoLog.DAY_MS

    @Test
    fun `round trips through text`() {
        val log = UndoLog(1_000L, listOf("content://media/1" to "content://media/9", "content://media/2" to "content://media/10"))
        assertEquals(log, UndoLog.parse(log.serialize()))
    }

    @Test
    fun `appending keeps earlier pairs`() {
        val log = UndoLog(0, listOf("a" to "b")).plus(listOf("c" to "d"))
        assertEquals(listOf("a" to "b", "c" to "d"), log.pairs)
    }

    @Test
    fun `days left count down to expiry`() {
        val log = UndoLog(0, emptyList())
        assertEquals(30, log.daysLeft(0))
        assertEquals(29, log.daysLeft(day / 2))
        assertEquals(0, log.daysLeft(30 * day - 1))
        assertFalse(log.isExpired(30 * day - 1))
        assertTrue(log.isExpired(30 * day))
    }

    @Test
    fun `broken text gives no log and broken lines are skipped`() {
        assertNull(UndoLog.parse(""))
        assertNull(UndoLog.parse("not a time\na\tb"))
        assertEquals(listOf("a" to "b"), UndoLog.parse("5\na\tb\nonly-one-part\n\t\n")?.pairs)
    }
}
