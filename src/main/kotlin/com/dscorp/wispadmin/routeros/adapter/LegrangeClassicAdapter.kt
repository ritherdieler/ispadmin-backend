package com.dscorp.wispadmin.routeros.adapter

import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import me.legrange.mikrotik.ApiConnection
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class LegrangeClassicAdapter(
    private val properties: RouterOsClientProperties,
    private val connectionFactory: ClassicConnectionFactory = DefaultClassicConnectionFactory
) : MikrotikClient, AutoCloseable {

    private val logger = LoggerFactory.getLogger(LegrangeClassicAdapter::class.java)
    private val connections = ConcurrentHashMap<String, ApiConnection>()
    private val locks = ConcurrentHashMap<String, Any>()

    override fun <T> withSession(device: MikrotikDeviceRef, block: (MikrotikSession) -> T): T {
        val lock = locks.computeIfAbsent(device.id) { Any() }
        synchronized(lock) {
            return try {
                val connection = obtainConnection(device)
                block(LegrangeClassicSession(connection))
            } catch (error: Exception) {
                evict(device.id)
                throw MikrotikExceptionMapper.map(error, "classic ${device.host}:${device.port}")
            }
        }
    }

    override fun closeSession(deviceId: String) {
        evict(deviceId)
        locks.remove(deviceId)
    }

    override fun isSessionActive(deviceId: String): Boolean {
        val connection = connections[deviceId] ?: return false
        return connection.isConnected
    }

    override fun activeSessionDeviceIds(): Set<String> {
        return connections.filterValues { it.isConnected }.keys.toSet()
    }

    private fun obtainConnection(device: MikrotikDeviceRef): ApiConnection {
        val existing = connections[device.id]
        if (existing != null && existing.isConnected) {
            return existing
        }
        if (existing != null) {
            closeQuietly(existing)
            connections.remove(device.id)
        }
        val port = if (device.port > 0) device.port else properties.classic.port
        val timeout = properties.classic.timeoutMs.toInt().coerceAtLeast(1000)
        val connection = connectionFactory.connect(device.host, port, timeout)
        try {
            connection.setTimeout(timeout)
            connection.login(device.username, device.password)
            connections[device.id] = connection
            return connection
        } catch (error: Exception) {
            closeQuietly(connection)
            throw error
        }
    }

    private fun evict(deviceId: String) {
        connections.remove(deviceId)?.let { closeQuietly(it) }
    }

    private fun closeQuietly(connection: ApiConnection) {
        try {
            connection.close()
        } catch (error: Exception) {
            logger.debug("Error closing classic MikroTik connection: {}", error.message)
        }
    }

    override fun close() {
        connections.keys.toList().forEach { evict(it) }
        locks.clear()
    }
}
