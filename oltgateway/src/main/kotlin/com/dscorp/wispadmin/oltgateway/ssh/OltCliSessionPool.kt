package com.dscorp.wispadmin.oltgateway.ssh

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.slf4j.LoggerFactory
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class OltCliSessionPool(
    private val sshClient: OltSshClient,
    private val properties: OltGatewayProperties,
    private val targetSize: Int,
    private val sessionFactory: (OltSshClient, OltGatewayProperties) -> HuaweiCliSession = { client, props ->
        HuaweiCliSession(client, props)
    }
) : AutoCloseable {

    companion object {
        private val logger = LoggerFactory.getLogger(OltCliSessionPool::class.java)
    }

    private val closed = AtomicBoolean(false)
    private val sessions = ArrayList<HuaweiCliSession>()
    private val available = ArrayBlockingQueue<HuaweiCliSession>(targetSize.coerceAtLeast(1))

    fun start() {
        val size = targetSize.coerceAtLeast(1)
        repeat(size) { index ->
            try {
                val session = sessionFactory(sshClient, properties)
                session.start()
                sessions += session
                available.offer(session)
            } catch (ex: Exception) {
                logger.warn("Unable to open CLI session {}/{}: {}", index + 1, size, ex.message)
            }
        }
        if (sessions.isEmpty()) {
            throw IllegalStateException("CLI session pool could not open any SSH session")
        }
        if (sessions.size < size) {
            logger.warn("CLI pool degraded size={}/{}", sessions.size, size)
        } else {
            logger.info("CLI session pool ready size={}", sessions.size)
        }
    }

    fun size(): Int = sessions.size

    fun <T> withSession(block: (HuaweiCliSession) -> T): T {
        check(!closed.get()) { "CLI session pool is closed" }
        val session = available.poll(properties.commandTimeoutMs, TimeUnit.MILLISECONDS)
            ?: throw IllegalStateException("Timed out waiting for free CLI session")
        try {
            return block(session)
        } finally {
            if (!closed.get()) {
                available.offer(session)
            }
        }
    }

    fun execute(command: String): String = withSession { it.execute(command) }

    fun ping(): Long = withSession { it.ping() }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        available.clear()
        sessions.forEach { session ->
            try {
                session.close()
            } catch (ex: Exception) {
                logger.warn("Error closing pooled CLI session: {}", ex.message)
            }
        }
        sessions.clear()
    }
}
