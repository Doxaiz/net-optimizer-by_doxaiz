package com.doxaiz.netoptimizer.core.net

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CancellationException

class UploadProgressTest {
    @Test fun reportsOnlySuccessfullyWrittenChunks() {
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Int>()
        var checks = 0
        UploadProgress.write(output, byteArrayOf(1, 2, 3), 8, { checks++ }, { progress.add(it) })
        assertArrayEquals(byteArrayOf(1, 2, 3, 1, 2, 3, 1, 2), output.toByteArray())
        assertEquals(listOf(3, 3, 2), progress)
        assertEquals(3, checks)
    }

    @Test fun neverReportsAFailedWriteOrContinuesAfterCancellation() {
        val progress = mutableListOf<Int>()
        val failing = object : OutputStream() {
            var calls = 0
            override fun write(b: Int) = throw UnsupportedOperationException()
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (++calls == 2) throw IOException("failed")
            }
        }
        try {
            UploadProgress.write(failing, ByteArray(4), 12, {}, { progress.add(it) })
            fail("expected IOException")
        } catch (_: IOException) { }
        assertEquals(listOf(4), progress)
        var checks = 0
        try {
            UploadProgress.write(ByteArrayOutputStream(), ByteArray(4), 12,
                { if (++checks == 2) throw CancellationException() }, { progress.add(it) })
            fail("expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(listOf(4, 4), progress)
    }

    @Test fun emptyUploadAndInvalidArguments() {
        UploadProgress.write(ByteArrayOutputStream(), ByteArray(1), 0, { fail() }, { fail() })
        try {
            UploadProgress.write(ByteArrayOutputStream(), ByteArray(0), 1, {}, {})
            fail("expected invalid buffer")
        } catch (_: IllegalArgumentException) { }
        try {
            UploadProgress.write(ByteArrayOutputStream(), ByteArray(1), -1, {}, {})
            fail("expected invalid count")
        } catch (_: IllegalArgumentException) { }
    }
}
