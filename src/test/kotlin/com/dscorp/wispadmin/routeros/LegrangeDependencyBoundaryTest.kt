package com.dscorp.wispadmin.routeros

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence

class LegrangeDependencyBoundaryTest {

    @Test
    fun `me legrange imports remain only under routeros adapter`() {
        val mainKotlin = Path.of("src/main/kotlin")
        assertTrue(Files.isDirectory(mainKotlin), "src/main/kotlin must exist")

        val offenders = Files.walk(mainKotlin).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(".kt") }
                .filter { path ->
                    path.readText().lines().any { line ->
                        line.trimStart().startsWith("import me.legrange")
                    }
                }
                .map { mainKotlin.relativize(it).toString().replace('\\', '/') }
                .filterNot { it.startsWith("com/dscorp/wispadmin/routeros/adapter/") }
                .sorted()
                .toList()
        }

        assertTrue(
            offenders.isEmpty(),
            "me.legrange must stay inside routeros/adapter until R5 removal. Offenders: $offenders"
        )
    }
}
