package com.dscorp.wispadmin.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.streams.asSequence

class LayeringTest {

    private val root: Path = Path.of(System.getProperty("user.dir"))

    private val modules = listOf(
        "shared", "events", "transport", "routeros",
        "servicehealth", "acs", "oltgateway", "traffic", "core",
        "netdiag", "observability", "app",
    )

    @Test
    fun noModuleDependsOnTheSameOrAnOuterLayer() {
        val layers = modules.associateWith { layerOf(it) }
        val violations = mutableListOf<String>()
        for (module in modules) {
            val own = layers.getValue(module)
            for (dep in projectDeps(module)) {
                val other = layers[dep] ?: continue
                if (other >= own) {
                    violations += ":$module (L$own) -> :$dep (L$other)"
                }
            }
        }
        assertTrue(violations.isEmpty()) {
            "Un módulo solo puede depender de capas más internas:\n${violations.joinToString("\n")}"
        }
    }

    @Test
    fun platformAndHealthDoNotImportCore() {
        val inner = listOf("shared", "events", "transport", "routeros", "servicehealth")
        val violations = inner.flatMap { module ->
            val src = root.resolve("$module/src/main")
            if (!Files.isDirectory(src)) return@flatMap emptyList()
            Files.walk(src).use { stream ->
                stream.asSequence()
                    .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                    .flatMap { file ->
                        Files.readAllLines(file).asSequence()
                            .filter { it.startsWith("import com.dscorp.wispadmin.wispadmin") }
                            .map { "${file.toString().removePrefix(root.toString() + "/")}: $it" }
                    }
                    .toList()
            }
        }
        assertTrue(violations.isEmpty()) {
            "L1/L2 no pueden importar com.dscorp.wispadmin.wispadmin:\n${violations.joinToString("\n")}"
        }
    }

    private fun layerOf(module: String): Int {
        val text = root.resolve("$module/build.gradle.kts").readText()
        val match = Regex("""extra\["gigafiberLayer"\]\s*=\s*(\d+)""").find(text)
        requireNotNull(match) { "missing gigafiberLayer in $module" }
        return match.groupValues[1].toInt()
    }

    private fun projectDeps(module: String): List<String> {
        val text = root.resolve("$module/build.gradle.kts").readText()
        return Regex("""(?:implementation|api|testImplementation)\(project\(":([^"]+)"\)\)""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()
    }
}
