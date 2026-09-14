package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.CrmLlmProperties
import com.dscorp.wispadmin.wispadmin.data.model.CrmIntegrationSetting
import com.dscorp.wispadmin.wispadmin.dto.CrmOpenAiSettingsDto
import com.dscorp.wispadmin.wispadmin.repository.CrmIntegrationSettingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicReference

data class CrmOpenAiRuntimeConfig(
    val enabled: Boolean,
    val apiKey: String?,
    val model: String
)

@Service
class CrmOpenAiSettingsService(
    private val repository: CrmIntegrationSettingRepository,
    private val cipher: CrmSecretCipher,
    private val properties: CrmLlmProperties
) {
    private val runtimeCache = AtomicReference<CrmOpenAiRuntimeConfig?>(null)

    fun getSettings(): CrmOpenAiSettingsDto {
        val apiKeySetting = repository.findBySettingKey(KEY_API_KEY).orElse(null)
        val model = readPlain(KEY_MODEL) ?: properties.defaultModel
        val enabled = readPlain(KEY_ENABLED)?.toBooleanStrictOrNull() ?: true
        val decrypted = decryptApiKey(apiKeySetting)
        val configured = !decrypted.isNullOrBlank() || properties.bootstrapApiKey.isNotBlank()
        val masked = when {
            !decrypted.isNullOrBlank() -> cipher.maskApiKey(decrypted)
            properties.bootstrapApiKey.isNotBlank() -> cipher.maskApiKey(properties.bootstrapApiKey)
            else -> null
        }
        val latest = listOfNotNull(apiKeySetting, find(KEY_MODEL), find(KEY_ENABLED))
            .maxByOrNull { it.updatedAt }
        return CrmOpenAiSettingsDto(
            configured = configured,
            maskedApiKey = masked,
            model = model,
            enabled = enabled && configured,
            updatedAt = latest?.updatedAt,
            updatedBy = latest?.updatedBy
        )
    }

    @Transactional
    fun updateSettings(
        apiKey: String?,
        model: String?,
        enabled: Boolean?,
        updatedBy: String?
    ): CrmOpenAiSettingsDto {
        val now = LocalDateTime.now()
        if (apiKey != null) {
            if (apiKey.isBlank()) {
                find(KEY_API_KEY)?.let { repository.delete(it) }
            } else {
                upsertSensitive(KEY_API_KEY, apiKey.trim(), updatedBy, now)
            }
        }
        if (model != null) {
            val trimmed = model.trim().ifBlank { properties.defaultModel }
            upsertPlain(KEY_MODEL, trimmed, updatedBy, now)
        }
        if (enabled != null) {
            upsertPlain(KEY_ENABLED, enabled.toString(), updatedBy, now)
        }
        invalidateCache()
        return getSettings()
    }

    fun resolveRuntimeConfig(): CrmOpenAiRuntimeConfig {
        runtimeCache.get()?.let { return it }
        val configured = loadRuntimeConfig()
        runtimeCache.set(configured)
        return configured
    }

    fun invalidateCache() {
        runtimeCache.set(null)
    }

    private fun loadRuntimeConfig(): CrmOpenAiRuntimeConfig {
        val dbKey = decryptApiKey(find(KEY_API_KEY))
        val apiKey = when {
            !dbKey.isNullOrBlank() -> dbKey
            properties.bootstrapApiKey.isNotBlank() -> properties.bootstrapApiKey.trim()
            else -> null
        }
        val model = readPlain(KEY_MODEL) ?: properties.defaultModel
        val enabledFlag = readPlain(KEY_ENABLED)?.toBooleanStrictOrNull() ?: true
        val enabled = enabledFlag && !apiKey.isNullOrBlank()
        return CrmOpenAiRuntimeConfig(
            enabled = enabled,
            apiKey = if (enabled) apiKey else null,
            model = model
        )
    }

    private fun decryptApiKey(setting: CrmIntegrationSetting?): String? {
        val cipherText = setting?.valueCiphertext ?: return null
        return try {
            cipher.decrypt(cipherText).trim().ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun upsertSensitive(key: String, value: String, updatedBy: String?, now: LocalDateTime) {
        val existing = find(key)
        val entity = existing?.copy(
            valueCiphertext = cipher.encrypt(value),
            valuePlain = null,
            sensitive = true,
            updatedBy = updatedBy,
            updatedAt = now
        ) ?: CrmIntegrationSetting(
            settingKey = key,
            valueCiphertext = cipher.encrypt(value),
            valuePlain = null,
            sensitive = true,
            updatedBy = updatedBy,
            updatedAt = now
        )
        repository.save(entity)
    }

    private fun upsertPlain(key: String, value: String, updatedBy: String?, now: LocalDateTime) {
        val existing = find(key)
        val entity = existing?.copy(
            valueCiphertext = null,
            valuePlain = value,
            sensitive = false,
            updatedBy = updatedBy,
            updatedAt = now
        ) ?: CrmIntegrationSetting(
            settingKey = key,
            valueCiphertext = null,
            valuePlain = value,
            sensitive = false,
            updatedBy = updatedBy,
            updatedAt = now
        )
        repository.save(entity)
    }

    private fun readPlain(key: String): String? = find(key)?.valuePlain?.trim()?.takeIf { it.isNotEmpty() }

    private fun find(key: String): CrmIntegrationSetting? = repository.findBySettingKey(key).orElse(null)

    companion object {
        const val KEY_API_KEY = "openai.api_key"
        const val KEY_MODEL = "openai.model"
        const val KEY_ENABLED = "openai.enabled"
    }
}
