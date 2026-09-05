package com.dscorp.wispadmin.wispadmin.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FaceModelFileResolverTest {

    private val resolver = FaceModelFileResolver()

    @Test
    fun resolveReturnsExistingFilesystemFile(@TempDir dir: Path) {
        val model = dir.resolve("face_feature.zip")
        Files.writeString(model, "djl-bytes")
        val resolved = resolver.resolve(model.toAbsolutePath().toString(), "face_feature")
        assertEquals(model.toAbsolutePath().toFile().canonicalFile, resolved.canonicalFile)
    }

    @Test
    fun resolveFilesystemMissingThrows() {
        assertThrows(IllegalStateException::class.java) {
            resolver.resolve("/tmp/gigafiber-missing-face-model.zip", "face_feature")
        }
    }
}
