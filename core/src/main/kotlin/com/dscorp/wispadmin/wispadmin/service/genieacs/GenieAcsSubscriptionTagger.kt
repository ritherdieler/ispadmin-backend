package com.dscorp.wispadmin.wispadmin.service.genieacs

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GenieAcsSubscriptionTagger(
    private val client: GenieAcsClient,
) {
    private val log = LoggerFactory.getLogger(GenieAcsSubscriptionTagger::class.java)

    fun apply(
        deviceId: String,
        subscriptionId: Int,
        kind: GenieAcsServiceKind,
        fullName: String?,
        previousDeviceId: String? = null,
    ) {
        val desired = GenieAcsSubscriptionTags.managedTags(subscriptionId, kind, fullName)
        if (!previousDeviceId.isNullOrBlank() && previousDeviceId != deviceId) {
            clearManagedTags(previousDeviceId)
        }
        val existing = try {
            client.listTags(deviceId)
        } catch (ex: Exception) {
            log.warn("No se pudieron listar tags de {}: {}", deviceId, ex.message)
            emptyList()
        }
        for (stale in GenieAcsSubscriptionTags.tagsToRemove(existing, desired)) {
            runCatching { client.deleteTag(deviceId, stale) }
                .onFailure { log.warn("No se pudo borrar tag {} de {}: {}", stale, deviceId, it.message) }
        }
        for (tag in desired) {
            runCatching { client.addTag(deviceId, tag) }
                .onFailure { log.warn("No se pudo agregar tag {} a {}: {}", tag, deviceId, it.message) }
        }
    }

    private fun clearManagedTags(deviceId: String) {
        val existing = try {
            client.listTags(deviceId)
        } catch (ex: Exception) {
            log.warn("No se pudieron listar tags del device anterior {}: {}", deviceId, ex.message)
            return
        }
        for (tag in existing.filter { GenieAcsSubscriptionTags.isManagedTag(it) }) {
            runCatching { client.deleteTag(deviceId, tag) }
                .onFailure { log.warn("No se pudo limpiar tag {} de {}: {}", tag, deviceId, it.message) }
        }
    }
}
