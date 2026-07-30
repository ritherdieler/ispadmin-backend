package com.dscorp.wispadmin.oltgateway.ssh

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

enum class CliJobType {
    WRITE,
    ADHOC,
    KEEPALIVE,
    INVENTORY,
    SIGNAL_POLL;

    fun priority(): Int = when (this) {
        WRITE -> 0
        ADHOC -> 1
        KEEPALIVE -> 2
        INVENTORY, SIGNAL_POLL -> 3
    }

    fun isSync(): Boolean = this == INVENTORY || this == SIGNAL_POLL
}

sealed class CliBusResult<out T> {
    data class Ok<T>(val value: T) : CliBusResult<T>()
    data class Skipped(val reason: String) : CliBusResult<Nothing>()
}

class OltCliBus(
    private val sshClient: OltSshClient,
    private val properties: OltGatewayProperties,
    private val sessionFactory: (OltSshClient, OltGatewayProperties) -> HuaweiCliSession = { client, props ->
        HuaweiCliSession(client, props)
    },
    private val schedulerFactory: () -> ScheduledExecutorService = {
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "olt-cli-bus-keepalive").apply { isDaemon = true }
        }
    },
    private val reachability: OltReachabilityTracker = OltReachabilityTracker(
        failureThreshold = properties.reachability.failureThreshold,
        backoffMs = properties.reachability.backoffMs
    )
) : AutoCloseable {

    companion object {
        private val logger = LoggerFactory.getLogger(OltCliBus::class.java)
        private val workerThreadLocal = ThreadLocal<Boolean>()
    }

    private val closed = AtomicBoolean(false)
    private val sequence = AtomicLong(0)
    private val queueLock = ReentrantLock()
    private val queueNotEmpty = queueLock.newCondition()
    private val queue = PriorityBlockingQueue<QueuedJob<*>>()

    @Volatile
    private var session: HuaweiCliSession? = null

    @Volatile
    private var busyType: CliJobType? = null

    @Volatile
    private var workerThread: Thread? = null

    private var keepaliveScheduler: ScheduledExecutorService? = null

    fun start() {
        check(!closed.get()) { "CLI bus is closed" }
        val sessionProps = cloneSessionPropsWithoutKeepalive()
        val created = sessionFactory(sshClient, sessionProps)
        created.start()
        session = created
        val worker = Thread({ drainLoop() }, "olt-cli-bus-worker").apply {
            isDaemon = true
            start()
        }
        workerThread = worker
        if (properties.session.keepaliveEnabled) {
            startKeepalive()
        }
        logger.info("CLI bus ready sessionCount=1")
    }

    fun sessionCount(): Int = 1

    fun queueDepth(): Int = queue.size

    fun busyJobType(): CliJobType? = busyType

    fun <T> submit(type: CliJobType, block: (HuaweiCliSession) -> T): CompletableFuture<CliBusResult<T>> {
        check(!closed.get()) { "CLI bus is closed" }
        if (reachability.shouldSkip(type)) {
            return CompletableFuture.completedFuture(CliBusResult.Skipped(reachability.skipReason()))
        }
        if (workerThreadLocal.get() == true) {
            return CompletableFuture.completedFuture(runOnCurrentSession(block))
        }
        queueLock.withLock {
            val skipReason = syncSkipReasonLocked(type)
            if (skipReason != null) {
                return CompletableFuture.completedFuture(CliBusResult.Skipped(skipReason))
            }
            val future = CompletableFuture<CliBusResult<T>>()
            queue.offer(
                QueuedJob(
                    type = type,
                    seq = sequence.incrementAndGet(),
                    block = block,
                    future = future
                )
            )
            queueNotEmpty.signal()
            return future
        }
    }

    fun <T> execute(type: CliJobType, block: (HuaweiCliSession) -> T): CliBusResult<T> {
        return try {
            submit(type, block).get()
        } catch (ex: ExecutionException) {
            val cause = ex.cause
            throw if (cause is Exception) cause else ex
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ex
        }
    }

    fun executeCommand(type: CliJobType, command: String): String {
        return when (val result = execute(type) { it.execute(command) }) {
            is CliBusResult.Ok -> result.value
            is CliBusResult.Skipped -> throw IllegalStateException("CLI job skipped: ${result.reason}")
        }
    }

    fun ping(): Long {
        return when (val result = execute(CliJobType.ADHOC) { it.ping() }) {
            is CliBusResult.Ok -> result.value
            is CliBusResult.Skipped -> throw IllegalStateException("CLI ping skipped: ${result.reason}")
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        stopKeepalive()
        queueLock.withLock {
            queueNotEmpty.signalAll()
        }
        workerThread?.join(properties.commandTimeoutMs)
        workerThread = null
        while (true) {
            val job = queue.poll() ?: break
            job.failClosed()
        }
        try {
            session?.close()
        } catch (ex: Exception) {
            logger.warn("Error closing CLI bus session: {}", ex.message)
        }
        session = null
    }

    private fun drainLoop() {
        workerThreadLocal.set(true)
        try {
            while (!closed.get()) {
                val job = takeNextJob() ?: continue
                val currentSession = session
                if (currentSession == null) {
                    clearBusy()
                    job.failClosed()
                    continue
                }
                try {
                    @Suppress("UNCHECKED_CAST")
                    val typed = job as QueuedJob<Any?>
                    val value = typed.block(currentSession)
                    reachability.recordSuccess()
                    typed.future.complete(CliBusResult.Ok(value))
                } catch (ex: Exception) {
                    if (isUnreachable(ex)) {
                        reachability.recordFailure()
                    }
                    job.future.completeExceptionally(ex)
                } finally {
                    clearBusy()
                }
            }
        } finally {
            workerThreadLocal.remove()
        }
    }

    private fun takeNextJob(): QueuedJob<*>? {
        queueLock.withLock {
            while (!closed.get() && queue.isEmpty()) {
                queueNotEmpty.await(200, TimeUnit.MILLISECONDS)
            }
            if (closed.get() && queue.isEmpty()) {
                return null
            }
            val job = queue.poll() ?: return null
            busyType = job.type
            return job
        }
    }

    private fun clearBusy() {
        queueLock.withLock {
            busyType = null
        }
    }

    private fun syncSkipReasonLocked(type: CliJobType): String? {
        if (!type.isSync()) {
            return null
        }
        if (busyType == type) {
            return "already_running"
        }
        if (queue.any { it.type == type }) {
            return "already_queued"
        }
        return null
    }

    private fun <T> runOnCurrentSession(block: (HuaweiCliSession) -> T): CliBusResult<T> {
        val currentSession = session ?: return CliBusResult.Skipped("bus_not_started")
        return try {
            CliBusResult.Ok(block(currentSession))
        } catch (ex: Exception) {
            if (isUnreachable(ex)) {
                reachability.recordFailure()
            }
            throw ex
        }
    }

    private fun isUnreachable(ex: Exception): Boolean {
        var current: Throwable? = ex
        while (current != null) {
            if (current is com.dscorp.wispadmin.oltgateway.exception.OltUnreachableException) {
                return true
            }
            val message = current.message.orEmpty().lowercase()
            if (message.contains("unable to reach olt")) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun startKeepalive() {
        val scheduler = schedulerFactory()
        keepaliveScheduler = scheduler
        val interval = properties.session.keepaliveIntervalMs
        scheduler.scheduleWithFixedDelay(
            { runKeepaliveTick() },
            interval,
            interval,
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopKeepalive() {
        val scheduler = keepaliveScheduler
        keepaliveScheduler = null
        scheduler?.shutdownNow()
    }

    private fun runKeepaliveTick() {
        if (closed.get()) {
            return
        }
        if (busyType != null || queueDepth() > 0) {
            return
        }
        try {
            submit(CliJobType.KEEPALIVE) { session ->
                session.ping()
            }
        } catch (ex: Exception) {
            logger.warn("CLI bus keepalive submit failed: {}", ex.message)
        }
    }

    private fun cloneSessionPropsWithoutKeepalive(): OltGatewayProperties {
        val copy = OltGatewayProperties()
        copy.enabled = properties.enabled
        copy.apiKey = properties.apiKey
        copy.host = properties.host
        copy.port = properties.port
        copy.username = properties.username
        copy.password = properties.password
        copy.oltId = properties.oltId
        copy.modelCode = properties.modelCode
        copy.commandTimeoutMs = properties.commandTimeoutMs
        copy.mock.enabled = properties.mock.enabled
        copy.ssh.legacyAlgorithms = properties.ssh.legacyAlgorithms
        copy.session.poolSize = 1
        copy.session.keepaliveEnabled = false
        copy.session.keepaliveIntervalMs = properties.session.keepaliveIntervalMs
        copy.session.keepaliveCommand = properties.session.keepaliveCommand
        copy.session.healthTimeoutMs = properties.session.healthTimeoutMs
        copy.session.sshIdleTimeoutMinutes = properties.session.sshIdleTimeoutMinutes
        copy.inventory.topologyCacheTtlMs = properties.inventory.topologyCacheTtlMs
        copy.inventory.maxSlotProbe = properties.inventory.maxSlotProbe
        copy.inventory.defaultPortsPerGponBoard = properties.inventory.defaultPortsPerGponBoard
        copy.inventory.slotAllProbeTimeoutMs = properties.inventory.slotAllProbeTimeoutMs
        copy.writes.enabled = properties.writes.enabled
        copy.writes.defaultLineProfileId = properties.writes.defaultLineProfileId
        copy.writes.defaultServiceProfileId = properties.writes.defaultServiceProfileId
        copy.sync.inventoryEnabled = properties.sync.inventoryEnabled
        copy.sync.inventoryIntervalMs = properties.sync.inventoryIntervalMs
        copy.sync.inventoryInitialDelayMs = properties.sync.inventoryInitialDelayMs
        copy.sync.signalEnabled = properties.sync.signalEnabled
        copy.sync.signalIntervalMs = properties.sync.signalIntervalMs
        copy.sync.signalInitialDelayMs = properties.sync.signalInitialDelayMs
        copy.sync.skipWhenWriteRunning = properties.sync.skipWhenWriteRunning
        copy.reachability.failureThreshold = properties.reachability.failureThreshold
        copy.reachability.backoffMs = properties.reachability.backoffMs
        return copy
    }

    private class QueuedJob<T>(
        val type: CliJobType,
        val seq: Long,
        val block: (HuaweiCliSession) -> T,
        val future: CompletableFuture<CliBusResult<T>>
    ) : Comparable<QueuedJob<*>> {
        override fun compareTo(other: QueuedJob<*>): Int {
            val byPriority = type.priority().compareTo(other.type.priority())
            if (byPriority != 0) {
                return byPriority
            }
            return seq.compareTo(other.seq)
        }

        fun failClosed() {
            future.completeExceptionally(IllegalStateException("CLI bus is closed"))
        }
    }
}
