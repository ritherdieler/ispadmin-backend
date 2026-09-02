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

enum class CliLane {
    INTERACTIVE,
    BACKGROUND
}

enum class CliJobType {
    WRITE,
    ADHOC,
    KEEPALIVE,
    INVENTORY,
    SIGNAL_POLL,
    ALARM_POLL,
    AUTOFIND_POLL;

    fun priority(): Int = when (this) {
        WRITE -> 0
        ADHOC -> 1
        KEEPALIVE -> 2
        AUTOFIND_POLL -> 3
        INVENTORY, SIGNAL_POLL, ALARM_POLL -> 4
    }

    fun isSync(): Boolean =
        this == INVENTORY || this == SIGNAL_POLL || this == ALARM_POLL || this == AUTOFIND_POLL

    fun lane(): CliLane = when (this) {
        WRITE, ADHOC -> CliLane.INTERACTIVE
        KEEPALIVE, INVENTORY, SIGNAL_POLL, ALARM_POLL, AUTOFIND_POLL -> CliLane.BACKGROUND
    }
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
        private const val MAX_LANES = 2
        private val workerSession = ThreadLocal<HuaweiCliSession?>()
    }

    private val closed = AtomicBoolean(false)
    private val sequence = AtomicLong(0)
    private val lanes = LinkedHashMap<CliLane, Lane>()

    private var keepaliveScheduler: ScheduledExecutorService? = null

    fun start() {
        check(!closed.get()) { "CLI bus is closed" }
        val laneCount = properties.session.poolSize.coerceIn(1, MAX_LANES)
        val laneNames = if (laneCount == 1) listOf(CliLane.INTERACTIVE) else CliLane.values().toList()
        laneNames.forEach { name -> lanes[name] = startLane(name) }
        if (properties.session.keepaliveEnabled) {
            startKeepalive()
        }
        logger.info("CLI bus ready sessionCount={} lanes={}", lanes.size, laneNames)
    }

    fun sessionCount(): Int = lanes.size

    fun queueDepth(): Int = lanes.values.sumOf { it.queue.size }

    fun queueDepth(lane: CliLane): Int = laneOrFallback(lane).queue.size

    fun busyJobType(): CliJobType? = lanes.values.firstNotNullOfOrNull { it.busyType }

    fun busyJobType(lane: CliLane): CliJobType? = laneOrFallback(lane).busyType

    fun <T> submit(type: CliJobType, block: (HuaweiCliSession) -> T): CompletableFuture<CliBusResult<T>> =
        submitToLane(type.lane(), type, block)

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
        lanes.values.forEach { lane ->
            lane.lock.withLock { lane.notEmpty.signalAll() }
        }
        lanes.values.forEach { lane ->
            lane.worker?.join(properties.commandTimeoutMs)
            lane.worker = null
            while (true) {
                val job = lane.queue.poll() ?: break
                job.failClosed()
            }
            try {
                lane.session?.close()
            } catch (ex: Exception) {
                logger.warn("Error closing CLI lane {} session: {}", lane.name, ex.message)
            }
            lane.session = null
        }
        lanes.clear()
    }

    internal fun runKeepaliveTickForTest() = runKeepaliveTick()

    private fun startLane(name: CliLane): Lane {
        val created = sessionFactory(sshClient, cloneSessionPropsWithoutKeepalive())
        created.start()
        val lane = Lane(name, created)
        val worker = Thread({ drainLoop(lane) }, "olt-cli-bus-${name.name.lowercase()}").apply {
            isDaemon = true
            start()
        }
        lane.worker = worker
        return lane
    }

    private fun laneOrFallback(lane: CliLane): Lane =
        lanes[lane] ?: lanes.getValue(CliLane.INTERACTIVE)

    private fun <T> submitToLane(
        lane: CliLane,
        type: CliJobType,
        block: (HuaweiCliSession) -> T
    ): CompletableFuture<CliBusResult<T>> {
        check(!closed.get()) { "CLI bus is closed" }
        if (reachability.shouldSkip(type)) {
            return CompletableFuture.completedFuture(CliBusResult.Skipped(reachability.skipReason()))
        }
        workerSession.get()?.let { inherited ->
            return CompletableFuture.completedFuture(runOnSession(inherited, block))
        }
        val target = laneOrFallback(lane)
        target.lock.withLock {
            val skipReason = syncSkipReasonLocked(target, type)
            if (skipReason != null) {
                return CompletableFuture.completedFuture(CliBusResult.Skipped(skipReason))
            }
            val future = CompletableFuture<CliBusResult<T>>()
            target.queue.offer(
                QueuedJob(
                    type = type,
                    seq = sequence.incrementAndGet(),
                    block = block,
                    future = future
                )
            )
            target.notEmpty.signal()
            return future
        }
    }

    private fun drainLoop(lane: Lane) {
        workerSession.set(lane.session)
        try {
            while (!closed.get()) {
                val job = takeNextJob(lane) ?: continue
                val currentSession = lane.session
                if (currentSession == null) {
                    clearBusy(lane)
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
                    clearBusy(lane)
                }
            }
        } finally {
            workerSession.remove()
        }
    }

    private fun takeNextJob(lane: Lane): QueuedJob<*>? {
        lane.lock.withLock {
            while (!closed.get() && lane.queue.isEmpty()) {
                lane.notEmpty.await(200, TimeUnit.MILLISECONDS)
            }
            if (closed.get() && lane.queue.isEmpty()) {
                return null
            }
            val job = lane.queue.poll() ?: return null
            lane.busyType = job.type
            return job
        }
    }

    private fun clearBusy(lane: Lane) {
        lane.lock.withLock {
            lane.busyType = null
        }
    }

    private fun syncSkipReasonLocked(lane: Lane, type: CliJobType): String? {
        if (!type.isSync()) {
            return null
        }
        if (lane.busyType == type) {
            return "already_running"
        }
        if (lane.queue.any { it.type == type }) {
            return "already_queued"
        }
        return null
    }

    private fun <T> runOnSession(session: HuaweiCliSession, block: (HuaweiCliSession) -> T): CliBusResult<T> {
        return try {
            CliBusResult.Ok(block(session))
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
        lanes.forEach { (name, lane) ->
            if (lane.busyType != null || lane.queue.isNotEmpty()) {
                return@forEach
            }
            try {
                submitToLane(name, CliJobType.KEEPALIVE) { session -> session.ping() }
            } catch (ex: Exception) {
                logger.warn("CLI bus keepalive submit failed on lane {}: {}", name, ex.message)
            }
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
        copy.session.poolSize = properties.session.poolSize
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
        copy.sync.alarmEnabled = properties.sync.alarmEnabled
        copy.sync.alarmIntervalMs = properties.sync.alarmIntervalMs
        copy.sync.alarmInitialDelayMs = properties.sync.alarmInitialDelayMs
        copy.sync.skipWhenWriteRunning = properties.sync.skipWhenWriteRunning
        copy.sync.labOpticalSshEnabled = properties.sync.labOpticalSshEnabled
        copy.sync.labOpticalSshIntervalMs = properties.sync.labOpticalSshIntervalMs
        copy.sync.labOpticalSshInitialDelayMs = properties.sync.labOpticalSshInitialDelayMs
        copy.reachability.failureThreshold = properties.reachability.failureThreshold
        copy.reachability.backoffMs = properties.reachability.backoffMs
        return copy
    }

    private class Lane(
        val name: CliLane,
        @Volatile var session: HuaweiCliSession?
    ) {
        val queue = PriorityBlockingQueue<QueuedJob<*>>()
        val lock = ReentrantLock()
        val notEmpty: java.util.concurrent.locks.Condition = lock.newCondition()

        @Volatile
        var busyType: CliJobType? = null

        @Volatile
        var worker: Thread? = null
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
