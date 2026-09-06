package com.dscorp.wispadmin.architecture

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory

class SatelliteCompilationProfileTest {
    @Test fun `each satellite compiles only owned and transport sources into a separate directory`() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("pom.xml"))
        val xpath = XPathFactory.newInstance().newXPath()
        for (module in listOf("traffic", "oltgateway", "acs")) {
            for (profile in listOf("$module-war", "$module-staging-war")) {
                val base = "/project/profiles/profile[id='$profile']/build"
                val mainSource = xpath.evaluate("/project/profiles/profile[id='$profile']/properties/kotlin.main.source", doc)
                assertTrue(
                    mainSource.contains("/src/main/kotlin/com/dscorp/wispadmin/$module"),
                    "$profile must replace compileSourceRoots; kotlin-maven-plugin always unions them with sourceDirs",
                )
                assertFalse(mainSource.endsWith("/src/main/kotlin"), "$profile cannot keep the full Kotlin tree as kotlin.main.source")
                val sources = xpath.evaluate("$base/plugins/plugin[artifactId='kotlin-maven-plugin']/executions/execution[id='compile']/configuration/sourceDirs", doc)
                assertTrue(sources.contains("/src/main/kotlin/com/dscorp/wispadmin/$module"), "$profile must limit compile sources")
                assertFalse(sources.contains("/wispadmin/wispadmin"), "$profile cannot compile core")
                assertTrue(xpath.evaluate("$base/directory", doc).contains("\${warName}"), "$profile needs an isolated build directory")
            }
        }
    }
}
