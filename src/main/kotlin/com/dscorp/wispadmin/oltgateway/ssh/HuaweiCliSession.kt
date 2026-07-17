package com.dscorp.wispadmin.oltgateway.ssh

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.exception.OltCommandTimeoutException
import com.dscorp.wispadmin.oltgateway.exception.OltUnreachableException
import org.apache.sshd.client.channel.ChannelShell
import org.apache.sshd.client.channel.ClientChannelEvent
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.EnumSet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class HuaweiCliSession(
    private val sshClient: OltSshClient,
    private val properties: OltGatewayProperties,
    private val schedulerFactory: () -> ScheduledExecutorService = {
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "olt-cli-keepalive").apply { isDaemon = true }
        }
    }
) : AutoCloseable {

    companion object {
        private val logger = LoggerFactory.getLogger(HuaweiCliSession::class.java)
    }

    private val lock = ReentrantLock()
    private var connected: OltSshClient.ConnectedSession? = null
    private var channel: ChannelShell? = null
    private var shellIn: OutputStream? = null
    private var shellOut: ByteArrayOutputStream? = null
    private var prepared = false
    private var keepaliveScheduler: ScheduledExecutorService? = null
    private var keepaliveFuture: ScheduledFuture<*>? = null
    private var lastMoreHandledLength: Int = -1

    fun start() {
        if (properties.session.keepaliveEnabled) {
            startKeepalive()
        }
        lock.withLock {
            try {
                ensurePrepared()
            } catch (ex: Exception) {
                logger.warn("Eager CLI connect failed: {}", ex.message)
            }
        }
    }

    fun execute(command: String): String = lock.withLock {
        try {
            return runCommand(command, properties.commandTimeoutMs)
        } catch (ex: Exception) {
            if (ex is OltCommandTimeoutException) {
                invalidateSession()
                throw ex
            }
            logger.warn("CLI command failed, retrying once: {}", ex.message)
            invalidateSession()
            return runCommand(command, properties.commandTimeoutMs)
        }
    }

    fun ping(): Long = lock.withLock {
        val started = System.currentTimeMillis()
        try {
            runCommand(properties.session.keepaliveCommand, properties.session.healthTimeoutMs)
        } catch (ex: Exception) {
            if (ex is OltCommandTimeoutException) {
                invalidateSession()
                throw ex
            }
            logger.warn("Health ping failed, retrying once: {}", ex.message)
            invalidateSession()
            runCommand(properties.session.keepaliveCommand, properties.session.healthTimeoutMs)
        }
        System.currentTimeMillis() - started
    }

    override fun close() {
        stopKeepalive()
        lock.withLock {
            invalidateSession()
        }
    }

    private fun startKeepalive() {
        val scheduler = schedulerFactory()
        keepaliveScheduler = scheduler
        val interval = properties.session.keepaliveIntervalMs
        keepaliveFuture = scheduler.scheduleWithFixedDelay(
            { runKeepaliveTick() },
            interval,
            interval,
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopKeepalive() {
        keepaliveFuture?.cancel(false)
        keepaliveFuture = null
        val scheduler = keepaliveScheduler
        keepaliveScheduler = null
        if (scheduler != null) {
            scheduler.shutdownNow()
        }
    }

    private fun runKeepaliveTick() {
        try {
            lock.withLock {
                try {
                    if (isSessionAlive()) {
                        clearOutputBuffer()
                        writeLine(properties.session.keepaliveCommand)
                        readUntilPrompt(properties.session.healthTimeoutMs)
                    } else {
                        logger.warn("CLI session dead during keepalive, reconnecting")
                        invalidateSession()
                        ensurePrepared()
                    }
                } catch (ex: Exception) {
                    logger.warn("Keepalive failed, reconnecting: {}", ex.message)
                    invalidateSession()
                    try {
                        ensurePrepared()
                    } catch (reconnectEx: Exception) {
                        logger.warn("Keepalive reconnect failed: {}", reconnectEx.message)
                    }
                }
            }
        } catch (ex: Exception) {
            logger.warn("Keepalive tick error: {}", ex.message)
        }
    }

    private fun runCommand(command: String, timeoutMs: Long): String {
        ensurePrepared()
        logger.info("Executing CLI command {}", command)
        clearOutputBuffer()
        writeLine(command)
        return readUntilPrompt(timeoutMs)
    }

    private fun ensurePrepared() {
        if (prepared && isSessionAlive()) {
            return
        }
        openShell()
        prepareSession()
        prepared = true
    }

    private fun isSessionAlive(): Boolean {
        return connected?.session?.isOpen == true && channel?.isOpen == true
    }

    private fun invalidateSession() {
        prepared = false
        try {
            channel?.close()
        } catch (_: Exception) {
        }
        try {
            shellIn?.close()
        } catch (_: Exception) {
        }
        sshClient.close(connected)
        connected = null
        channel = null
        shellIn = null
        shellOut = null
    }

    private fun openShell() {
        try {
            channel?.close()
        } catch (_: Exception) {
        }
        sshClient.close(connected)
        val newConnected = sshClient.openSession()
        connected = newConnected
        val out = ByteArrayOutputStream()
        shellOut = out
        val shell = newConnected.session.createShellChannel()
        shell.out = out
        shell.err = out
        shell.open().verify(properties.commandTimeoutMs, TimeUnit.MILLISECONDS)
        channel = shell
        shellIn = shell.invertedIn
        readUntilPrompt(properties.commandTimeoutMs)
    }

    private fun prepareSession() {
        writeLine("enable")
        val afterEnable = readUntilPromptOrPassword(properties.commandTimeoutMs)
        if (afterEnable.contains(Regex("(?i)password"))) {
            writeLine(properties.password)
            readUntilPrompt(properties.commandTimeoutMs)
        }
        writeLine("config")
        readUntilPrompt(properties.commandTimeoutMs)
        writeLine("mmi-mode enable")
        readUntilPrompt(properties.commandTimeoutMs)
        writeLine("quit")
        readUntilPrompt(properties.commandTimeoutMs)
    }

    private fun writeLine(line: String) {
        val input = shellIn ?: throw OltUnreachableException("SSH shell is not open")
        input.write("$line\r".toByteArray(StandardCharsets.UTF_8))
        input.flush()
    }

    private fun clearOutputBuffer() {
        shellOut?.reset()
        lastMoreHandledLength = -1
    }

    private fun readUntilPrompt(timeoutMs: Long): String {
        return readUntil(timeoutMs) { text ->
            handleMorePagination(text)
            HuaweiCliPromptDetector.isComplete(text)
        }
    }

    private fun readUntilPromptOrPassword(timeoutMs: Long): String {
        return readUntil(timeoutMs) { text ->
            handleMorePagination(text)
            text.takeLast(400).contains(Regex("(?i)password:")) ||
                HuaweiCliPromptDetector.isComplete(text)
        }
    }

    private fun handleMorePagination(text: String) {
        if (!HuaweiCliPromptDetector.needsMorePage(text)) {
            return
        }
        if (text.length == lastMoreHandledLength) {
            return
        }
        lastMoreHandledLength = text.length
        val input = shellIn ?: return
        input.write(" ".toByteArray(StandardCharsets.UTF_8))
        input.flush()
    }

    private fun readUntil(timeoutMs: Long, predicate: (String) -> Boolean): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        val shell = channel ?: throw OltUnreachableException("SSH shell is not open")
        while (System.currentTimeMillis() < deadline) {
            shell.waitFor(EnumSet.of(ClientChannelEvent.STDOUT_DATA), 200)
            val text = currentOutput()
            if (predicate(text)) {
                return stripPaginationMarkers(text)
            }
            Thread.sleep(50)
        }
        throw OltCommandTimeoutException("CLI command timed out after ${timeoutMs}ms")
    }

    private fun currentOutput(): String {
        return shellOut?.toString(StandardCharsets.UTF_8.name()) ?: ""
    }

    private fun stripPaginationMarkers(text: String): String {
        return HuaweiCliPromptDetector.stripMore(text)
    }
}
