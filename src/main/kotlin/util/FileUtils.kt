package util

import java.io.File
import java.nio.charset.Charset

object FileUtils {
    private val UTF8: Charset = Charsets.UTF_8

    fun ensureParentDir(path: String) {
        val f = File(path)
        val dir = if (f.isDirectory) f else f.parentFile
        if (dir != null && !dir.exists()) {
            if (!dir.mkdirs()) throw IllegalStateException("Failed to create directory: ${dir.path}")
        }
    }

    fun writeUtf8(path: String, content: String, overwrite: Boolean = true) {
        ensureParentDir(path)
        val file = File(path)
        if (!overwrite && file.exists()) {
            throw IllegalStateException("File exists and overwrite=false: $path")
        }
        file.writeText(content, UTF8)
        if (!file.exists() || file.length() == 0L) {
            throw IllegalStateException("Write verification failed for: $path")
        }
    }

    fun readUtf8(path: String): String = File(path).readText(UTF8)

    fun readUtf8OrNull(path: String): String? {
        val f = File(path)
        return if (f.exists()) f.readText(UTF8) else null
    }

    fun exists(path: String): Boolean = File(path).exists()

    fun existsNonEmpty(path: String): Boolean {
        val f = File(path)
        return f.exists() && f.length() > 0L
    }

    fun createFileIfMissing(path: String, content: String = "") {
        if (!exists(path)) {
            writeUtf8(path, content, overwrite = true)
        }
    }
}
