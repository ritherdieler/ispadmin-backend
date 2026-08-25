package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.repository.Tr069ModelProfileRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class Tr069ModelProfileImportServiceTest {

    private val repository = mockk<Tr069ModelProfileRepository>(relaxed = true)
    private val registry = mockk<Tr069ModelProfileRegistry>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private lateinit var service: Tr069ModelProfileImportService

    @BeforeEach
    fun setUp() {
        service = Tr069ModelProfileImportService(repository, registry, objectMapper)
    }

    @Test
    fun `preview detects Huawei profile from CSV fixture`() {
        val csv = readFixture("genieacs-exports/huawei-hg8145x6.csv")
        val preview = service.preview(csv)
        assertEquals("HG8145X6", preview.draft.productClass)
        assertTrue(preview.readyToImport)
        assertEquals(null, preview.draft.clientWanIpConnectionPath)
    }

    @Test
    fun `preview detects ZTE F6600R profile from CSV fixture`() {
        val csv = readFixture("genieacs-exports/zte-f6600r.csv")
        val preview = service.preview(csv)
        assertEquals("F6600R", preview.draft.productClass)
        assertTrue(preview.readyToImport)
        assertTrue(
            preview.draft.wlan24Path!!.endsWith("WLANConfiguration.1"),
            preview.draft.wlan24Path,
        )
        assertTrue(
            preview.draft.wlan5Path!!.endsWith("WLANConfiguration.5"),
            preview.draft.wlan5Path,
        )
        assertTrue(
            preview.draft.clientWanIpConnectionPath!!.contains("WANDevice.2.WANConnectionDevice.1"),
            preview.draft.clientWanIpConnectionPath,
        )
    }

    @Test
    fun `import saves entity and reloads registry`() {
        val csv = readFixture("genieacs-exports/huawei-hg8145x6.csv")
        every { repository.existsById("HG8145X6") } returns false
        every { repository.save(any()) } answers { firstArg() }

        val result = service.importCsv(csv, importedBy = "admin@test", aliases = listOf("HG8245H"))

        assertEquals("HG8145X6", result.saved.productClass)
        assertEquals(listOf("HG8245H"), result.saved.aliases)
        verify(exactly = 1) { registry.reload() }
    }

    private fun readFixture(path: String): String =
        requireNotNull(javaClass.classLoader.getResourceAsStream(path)) { "Missing fixture $path" }
            .bufferedReader()
            .readText()
}
