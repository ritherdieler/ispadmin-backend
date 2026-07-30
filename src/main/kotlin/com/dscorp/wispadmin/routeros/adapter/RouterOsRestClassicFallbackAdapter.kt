package com.dscorp.wispadmin.routeros.adapter

import com.dscorp.wispadmin.routeros.port.MikrotikAuthException
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikException
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.routeros.port.MikrotikTimeoutException
import com.dscorp.wispadmin.routeros.port.MikrotikUnreachableException
import org.slf4j.LoggerFactory

class RouterOsRestClassicFallbackAdapter(
    private val primary: MikrotikClient,
    private val fallback: MikrotikClient
) : MikrotikClient, AutoCloseable {

    private val logger = LoggerFactory.getLogger(RouterOsRestClassicFallbackAdapter::class.java)

    override fun <T> withSession(device: MikrotikDeviceRef, block: (MikrotikSession) -> T): T {
        return try {
            primary.withSession(device, block)
        } catch (error: Exception) {
            if (!shouldFallback(error)) {
                throw error
            }
            logger.warn(
                "REST Mikrotik failed for {} ({}); falling back to classic API on port {}",
                device.host,
                error.message,
                device.port
            )
            fallback.withSession(device.copy(port = classicPort(device)), block)
        }
    }

    override fun closeSession(deviceId: String) {
        primary.closeSession(deviceId)
        fallback.closeSession(deviceId)
    }

    override fun isSessionActive(deviceId: String): Boolean {
        return primary.isSessionActive(deviceId) || fallback.isSessionActive(deviceId)
    }

    override fun activeSessionDeviceIds(): Set<String> {
        return primary.activeSessionDeviceIds() + fallback.activeSessionDeviceIds()
    }

    override fun close() {
        closeQuietly(primary)
        closeQuietly(fallback)
    }

    private fun classicPort(device: MikrotikDeviceRef): Int {
        return if (device.port == 443 || device.port == 80) 8728 else device.port
    }

    private fun shouldFallback(error: Exception): Boolean {
        if (error is MikrotikAuthException || error is MikrotikTimeoutException) {
            return false
        }
        if (error is MikrotikUnreachableException) {
            return true
        }
        if (error is MikrotikException) {
            return isTransportOrTlsFailure(error.message)
        }
        return isTransportOrTlsFailure(error.message)
    }

    private fun isTransportOrTlsFailure(message: String?): Boolean {
        val text = message.orEmpty().lowercase()
        return text.contains("handshake") ||
            text.contains("ssl") ||
            text.contains("tls") ||
            text.contains("certificate") ||
            text.contains("connection refused") ||
            text.contains("failed to connect") ||
            text.contains("unreachable")
    }

    private fun closeQuietly(client: MikrotikClient) {
        if (client is AutoCloseable) {
            runCatching { client.close() }
        }
    }
}
