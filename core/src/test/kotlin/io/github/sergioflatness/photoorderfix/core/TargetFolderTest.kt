package io.github.sergioflatness.photoorderfix.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetFolderTest {

    @Test
    fun `items in allowed folders stay where they are`() {
        assertEquals("DCIM/Camera/", TargetFolder.of("DCIM/Camera/", isVideo = false))
        assertEquals("Pictures/Screenshots/", TargetFolder.of("Pictures/Screenshots/", isVideo = false))
        assertEquals("Movies/Instagram/", TargetFolder.of("Movies/Instagram/", isVideo = true))
        assertFalse(TargetFolder.isMoved("DCIM/Camera/", "DCIM/Camera/"))
    }

    @Test
    fun `Smart Switch iPhone folder moves under DCIM keeping album names`() {
        assertEquals("DCIM/iPhoneData/Pictures/Screenshots/", TargetFolder.of("iPhoneData/Pictures/Screenshots/", isVideo = false))
        assertEquals("DCIM/iPhoneData/Movies/", TargetFolder.of("iPhoneData/Movies/", isVideo = true))
        assertTrue(TargetFolder.isMoved("iPhoneData/Movies/", "DCIM/iPhoneData/Movies/"))
    }

    @Test
    fun `images in Movies are moved because images cannot be created there`() {
        assertEquals("DCIM/Movies/", TargetFolder.of("Movies/", isVideo = false))
    }

    @Test
    fun `app private and unknown folders are left alone`() {
        assertNull(TargetFolder.of("Android/media/com.whatsapp/", isVideo = false))
        assertNull(TargetFolder.of("", isVideo = false))
    }

    @Test
    fun `missing trailing slash is added`() {
        assertEquals("DCIM/Download/", TargetFolder.of("Download", isVideo = false))
    }
}
