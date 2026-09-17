package com.dscorp.wispadmin.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.streams.asSequence

class MikroTikOutsideTrafficForbiddenTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))

    private val forbidden = listOf(
        "import com.dscorp.wispadmin.routeros.port.MikrotikClient",
        "import com.dscorp.wispadmin.routeros.adapter.RouterOs7RestAdapter",
        "RouterOs7RestAdapter(",
        "MikrotikClientAccessor",
    )

    @Test
    fun mainSourcesOutsideTrafficDoNotOpenRouterOS() {
        val modules = listOf("core", "oltgateway", "acs")
        val violations = modules.flatMap { module ->
            val src = root.resolve("$module/src/main")
            if (!Files.isDirectory(src)) return@flatMap emptyList()
            Files.walk(src).use { stream ->
                stream.asSequence()
                    .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                    .flatMap { file ->
                        val text = file.readText()
                        forbidden.asSequence()
                            .filter { token -> text.contains(token) }
                            .map { token ->
                                "${file.toString().removePrefix(root.toString() + "/")}: $token"
                            }
                    }
                    .toList()
            }
        }
        assertTrue(violations.isEmpty()) {
            "Solo Traffic puede abrir RouterOS:\n${violations.joinToString("\n")}"
        }
    }
}
