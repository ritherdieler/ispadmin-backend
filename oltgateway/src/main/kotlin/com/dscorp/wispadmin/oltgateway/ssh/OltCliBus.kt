package com.dscorp.wispadmin.oltgateway.ssh

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
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
    AUTHORIZE,
    UNCONFIGURED,
    WRITE,
    ADHOC,
    KEEPALIVE,
    INVENTORY,
    SIGNAL_POLL,
    ALARM_POLL,
    AUTOFIND_POLL;

    fun priority(): Int = when (this) {
        AUTHORIZE -> 0
        UNCONFIGURED -> 1
        else -> 2
    }

    fun isSync(): Boolean =
        this == INVENTORY || this == SIGNAL_POLL || this == ALARM_POLL || this == AUTOFIND_POLL

    fun isWrite(): Boolean = this == AUTHORIZE || this == WRITE

    fun isLongRead(): Boolean =
        this == INVENTORY || this == SIGNAL_POLL || this == ALARM_POLL || this == AUTOFIND_POLL

    fun lane(): CliLane = when (this) {
        AUTHORIZE, UNCONFIGURED -> CliLane.INTERACTIVE
        else -> CliLane.BACKGROUND
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
    private val jobs = ArrayList<QueuedJob<*>>()
    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()
    private var writeInFlight = false
    private var unconfiguredFlight: CompletableFuture<CliBusResult<*>>? = null

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

    fun queueDepth(): Int = lock.withLock { jobs.size }

    fun queueDepth(lane: CliLane): Int = queueDepth()

    fun busyJobType(): CliJobType? = lanes.values.firstNotNullOfOrNull { it.busyType }

    fun busyJobType(lane: CliLane): CliJobType? = laneOrFallback(lane).busyType

    fun <T> submit(type: CliJobType, block: (HuaweiCliSession) -> T): CompletableFuture<CliBusResult<T>> =
        submitInternal(type, boundLane = null, block)

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
        lock.withLock { notEmpty.signalAll() }
        lanes.values.forEach { lane ->
            lane.worker?.join(properties.commandTimeoutMs)
            lane.worker = null
            try {
                lane.session?.close()
            } catch (ex: Exception) {
                logger.warn("Error closing CLI lane {} session: {}", lane.name, ex.message)
            }
            lane.session = null
        }
        lock.withLock {
            jobs.forEach { it.failClosed() }
            jobs.clear()
            unconfiguredFlight = null
            writeInFlight = false
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

    @Suppress("UNCHECKED_CAST")
    private fun <T> submitInternal(
        type: CliJobType,
        boundLane: CliLane?,
        block: (HuaweiCliSession) -> T
    ): CompletableFuture<CliBusResult<T>> {
        check(!closed.get()) { "CLI bus is closed" }
        if (reachability.shouldSkip(type)) {
            return CompletableFuture.completedFuture(CliBusResult.Skipped(reachability.skipReason()))
        }
        workerSession.get()?.let { inherited ->
            return CompletableFuture.completedFuture(runOnSession(inherited, block))
        }
        lock.withLock {
            if (type == CliJobType.UNCONFIGURED) {
                unconfiguredFlight?.let { return it as CompletableFuture<CliBusResult<T>> }
            }
            val skipReason = syncSkipReasonLocked(type)
            if (skipReason != null) {
                return CompletableFuture.completedFuture(CliBusResult.Skipped(skipReason))
            }
            val future = CompletableFuture<CliBusResult<T>>()
            if (type == CliJobType.UNCONFIGURED) {
                unconfiguredFlight = future as CompletableFuture<CliBusResult<*>>
            }
            jobs.add(
                QueuedJob(
                    type = type,
                    seq = sequence.incrementAndGet(),
                    block = block,
                    future = future,
                    boundLane = boundLane
                )
            )
            notEmpty.signalAll()
            return future
        }
    }

    private fun drainLoop(lane: Lane) {
        workerSession.set(lane.session)
        try {
            while (!closed.get()) {
                val job = takeEligibleJob(lane) ?: continue
                val currentSession = lane.session
                if (currentSession == null) {
                    clearBusy(lane, job.type)
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
                    clearBusy(lane, job.type)
                    lock.withLock {
                        if (job.type == CliJobType.UNCONFIGURED) {
                            unconfiguredFlight = null
                        }
                        notEmpty.signalAll()
                    }
                }
            }
        } finally {
            workerSession.remove()
        }
    }

    private fun takeEligibleJob(lane: Lane): QueuedJob<*>? {
        lock.withLock {
            while (!closed.get()) {
                val job = nextEligible(lane)
                if (job != null) {
                    jobs.remove(job)
                    lane.busyType = job.type
                    if (job.type.isWrite()) {
                        writeInFlight = true
                    }
                    return job
                }
                notEmpty.await(200, TimeUnit.MILLISECONDS)
            }
            return null
        }
    }

    private fun nextEligible(lane: Lane): QueuedJob<*>? =
        jobs.filter { eligible(lane, it) }.minWithOrNull(compareBy({ it.type.priority() }, { it.seq }))

    private fun eligible(lane: Lane, job: QueuedJob<*>): Boolean {
        if (job.boundLane != null && job.boundLane != lane.name) {
            return false
        }
        if (job.type.isWrite() && writeInFlight) {
            return false
        }
        if (lanes.size == 1) {
            return true
        }
        return when (lane.name) {
            CliLane.INTERACTIVE -> when (job.type) {
                CliJobType.AUTHORIZE, CliJobType.UNCONFIGURED -> true
                CliJobType.WRITE -> otherBusy(CliLane.BACKGROUND)
                CliJobType.KEEPALIVE -> job.boundLane == CliLane.INTERACTIVE
                else -> false
            }
            CliLane.BACKGROUND -> when (job.type) {
                CliJobType.AUTHORIZE, CliJobType.UNCONFIGURED -> otherBusy(CliLane.INTERACTIVE)
                CliJobType.WRITE, CliJobType.ADHOC, CliJobType.KEEPALIVE -> true
                else -> job.type.isLongRead()
            }
        }
    }

    private fun otherBusy(name: CliLane): Boolean {
        val other = lanes[name] ?: return true
        return other.busyType != null
    }

    private fun clearBusy(lane: Lane, type: CliJobType) {
        lock.withLock {
            lane.busyType = null
            if (type.isWrite()) {
                writeInFlight = false
            }
        }
    }

    private fun syncSkipReasonLocked(type: CliJobType): String? {
        if (!type.isSync()) {
            return null
        }
        if (lanes.values.any { it.busyType == type }) {
            return "already_running"
        }
        if (jobs.any { it.type == type }) {
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
            val idle = lock.withLock {
                lane.busyType == null && jobs.none { eligible(lane, it) }
            }
            if (!idle) {
                return@forEach
            }
            try {
                submitInternal(CliJobType.KEEPALIVE, boundLane = name) { session -> session.ping() }
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
        @Volatile
        var busyType: CliJobType? = null

        @Volatile
        var worker: Thread? = null
    }

    private class QueuedJob<T>(
        val type: CliJobType,
        val seq: Long,
        val block: (HuaweiCliSession) -> T,
        val future: CompletableFuture<CliBusResult<T>>,
        val boundLane: CliLane?
    ) {
        fun failClosed() {
            future.completeExceptionally(IllegalStateException("CLI bus is closed"))
        }
    }
}
