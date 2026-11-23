package com.doxaiz.netoptimizer.core.net

import java.io.OutputStream

/** Progress is bytes accepted by the stream; it does not prove they reached the server. */
internal object UploadProgress {
    fun write(
        output: OutputStream,
        buffer: ByteArray,
        count: Int,
        check: () -> Unit,
        onWritten: (Int) -> Unit
    ) {
        require(buffer.isNotEmpty() && count >= 0)
        var written = 0
        while (written < count) {
            check()
            val n = minOf(buffer.size, count - written)
            output.write(buffer, 0, n)
            written += n
            onWritten(n)
        }
    }
}
