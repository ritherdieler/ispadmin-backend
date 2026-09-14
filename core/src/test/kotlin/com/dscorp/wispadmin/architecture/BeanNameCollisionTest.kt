package com.dscorp.wispadmin.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.streams.asSequence

class BeanNameCollisionTest {

    private val root = Path.of(System.getProperty("user.dir"))

    private val modules = listOf(
        "shared", "events", "transport", "routeros",
        "servicehealth", "acs", "oltgateway", "traffic", "core",
        "netdiag", "observability", "app",
    )

    private val excludedPath = "/wispadmin/service/genieacs/"

    @Test
    fun scannedModulesDoNotDeclareTheSameSpringBeanName() {
        val byName = linkedMapOf<String, MutableList<String>>()
        for (module in modules) {
            val src = root.resolve("$module/src/main/kotlin")
            if (!Files.isDirectory(src)) continue
            Files.walk(src).use { stream ->
                stream.asSequence()
                    .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                    .filter { !it.toString().contains(excludedPath) }
                    .forEach { file -> collectBeanNames(file, byName) }
            }
        }
        val collisions = byName.entries
            .filter { it.value.size > 1 }
            .map { "${it.key}: ${it.value.joinToString()}" }
        assertTrue(collisions.isEmpty()) {
            "Nombres de bean duplicados en el WAR unico:\n${collisions.joinToString("\n")}"
        }
    }

    private fun collectBeanNames(file: Path, byName: MutableMap<String, MutableList<String>>) {
        val lines = file.readText().lines()
        val rel = root.relativize(file).toString()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val namedBean = BEAN_NAMED.find(line)
            if (namedBean != null) {
                add(byName, namedBean.groupValues[1], "$rel:${i + 1}")
                i++
                continue
            }
            val namedStereo = STEREO_NAMED.find(line)
            if (namedStereo != null) {
                add(byName, namedStereo.groupValues[1], "$rel:${i + 1}")
                i++
                continue
            }
            if (BARE_BEAN.containsMatchIn(line)) {
                val funName = findAhead(lines, i, FUN_NAME)
                if (funName != null) add(byName, funName.first, "$rel:${funName.second}")
                i++
                continue
            }
            if (BARE_STEREO.containsMatchIn(line) && !line.contains("(")) {
                val className = findAhead(lines, i, CLASS_NAME)
                if (className != null) {
                    add(byName, className.first.replaceFirstChar { it.lowercase() }, "$rel:${className.second}")
                }
            }
            i++
        }
    }

    private fun findAhead(lines: List<String>, from: Int, regex: Regex): Pair<String, Int>? {
        for (j in from until minOf(from + 12, lines.size)) {
            val match = regex.find(lines[j]) ?: continue
            return match.groupValues[1] to (j + 1)
        }
        return null
    }

    private fun add(byName: MutableMap<String, MutableList<String>>, name: String, loc: String) {
        byName.getOrPut(name) { mutableListOf() }.add(loc)
    }

    companion object {
        private val BEAN_NAMED = Regex("""@Bean\(\s*["']([^"']+)["']""")
        private val STEREO_NAMED =
            Regex("""@(?:Configuration|Component|Service|Controller|RestController|Repository)\(\s*["']([^"']+)["']""")
        private val BARE_BEAN = Regex("""@Bean\b""")
        private val BARE_STEREO =
            Regex("""@(?:Configuration|Component|Service|Controller|RestController|Repository)\b""")
        private val FUN_NAME = Regex("""fun\s+([A-Za-z0-9_]+)\s*\(""")
        private val CLASS_NAME = Regex("""^(?:open\s+)?(?:class|interface)\s+([A-Za-z0-9_]+)""")
    }
}
