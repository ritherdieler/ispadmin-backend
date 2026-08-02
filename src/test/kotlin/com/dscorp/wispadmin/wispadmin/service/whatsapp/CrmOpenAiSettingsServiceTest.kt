package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.CrmLlmProperties
import com.dscorp.wispadmin.wispadmin.data.model.CrmIntegrationSetting
import com.dscorp.wispadmin.wispadmin.repository.CrmIntegrationSettingRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.concurrent.atomic.AtomicReference

class CrmOpenAiSettingsServiceTest {

    private val repository = mockk<CrmIntegrationSettingRepository>()
    private val cipher = CrmSecretCipher("unit-test-master-key-crm-openai-xx")
    private val properties = CrmLlmProperties().apply {
        defaultModel = "gpt-4o-mini"
        bootstrapApiKey = ""
    }
    private lateinit var service: CrmOpenAiSettingsService
    private val store = mutableMapOf<String, CrmIntegrationSetting>()

    @BeforeEach
    fun setUp() {
        store.clear()
        every { repository.findBySettingKey(any()) } answers {
            Optional.ofNullable(store[firstArg()])
        }
        every { repository.save(any()) } answers {
            val entity = firstArg<CrmIntegrationSetting>()
            store[entity.settingKey] = entity.copy(id = entity.id ?: (store.size + 1L))
            store[entity.settingKey]!!
        }
        every { repository.delete(any()) } answers {
            store.remove(firstArg<CrmIntegrationSetting>().settingKey)
        }
        service = CrmOpenAiSettingsService(repository, cipher, properties)
    }

    @Test
    fun `getSettings never returns full api key`() {
        service.updateSettings(
            apiKey = "sk-proj-supersecretkey1234",
            model = "gpt-4o-mini",
            enabled = true,
            updatedBy = "admin"
        )
        val dto = service.getSettings()
        assertTrue(dto.configured)
        assertTrue(dto.enabled)
        assertEquals("gpt-4o-mini", dto.model)
        assertEquals("sk-...1234", dto.maskedApiKey)
        assertFalse(dto.toString().contains("supersecretkey"))
    }

    @Test
    fun `empty api key clears configured flag`() {
        service.updateSettings("sk-abcdexxxx", "gpt-4o-mini", true, "admin")
        service.updateSettings("", null, null, "admin")
        val dto = service.getSettings()
        assertFalse(dto.configured)
        assertNull(dto.maskedApiKey)
    }

    @Test
    fun `resolveRuntimeConfig returns decrypted key when enabled`() {
        service.updateSettings("sk-live-runtime-key9999", "gpt-4o", true, "admin")
        val runtime = service.resolveRuntimeConfig()
        assertTrue(runtime.enabled)
        assertEquals("sk-live-runtime-key9999", runtime.apiKey)
        assertEquals("gpt-4o", runtime.model)
    }

    @Test
    fun `resolveRuntimeConfig disabled when flag false`() {
        service.updateSettings("sk-live-runtime-key9999", "gpt-4o", false, "admin")
        val runtime = service.resolveRuntimeConfig()
        assertFalse(runtime.enabled)
        assertNull(runtime.apiKey)
    }

    @Test
    fun `cache invalidates after update`() {
        val hits = AtomicReference(0)
        every { repository.findBySettingKey(CrmOpenAiSettingsService.KEY_API_KEY) } answers {
            hits.updateAndGet { it + 1 }
            Optional.ofNullable(store[CrmOpenAiSettingsService.KEY_API_KEY])
        }
        service.updateSettings("sk-cache-key-aaaa", "gpt-4o-mini", true, "admin")
        hits.set(0)
        service.resolveRuntimeConfig()
        val afterFirst = hits.get()
        service.resolveRuntimeConfig()
        assertEquals(afterFirst, hits.get())
        service.updateSettings("sk-cache-key-bbbb", null, null, "admin")
        hits.set(0)
        service.resolveRuntimeConfig()
        assertTrue(hits.get() >= 1)
    }

    @Test
    fun `bootstrap env key used when db empty`() {
        properties.bootstrapApiKey = "sk-bootstrap-key-zzzz"
        val runtime = service.resolveRuntimeConfig()
        assertTrue(runtime.enabled)
        assertEquals("sk-bootstrap-key-zzzz", runtime.apiKey)
    }
}
