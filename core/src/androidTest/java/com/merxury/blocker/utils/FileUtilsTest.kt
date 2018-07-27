package com.merxury.blocker.utils

import com.merxury.libkit.utils.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class FileUtilsTest {

    @Test
    fun testGetFileName() {
        val path = "/emulated/0/Blocker/rules/com.merxury.blocker.json"
        assertEquals("com.merxury.blocker", FileUtils.getFileName(path))
    }
}