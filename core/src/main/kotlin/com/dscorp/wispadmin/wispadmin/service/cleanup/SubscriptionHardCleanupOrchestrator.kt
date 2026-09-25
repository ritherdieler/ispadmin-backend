package com.dscorp.wispadmin.wispadmin.service.cleanup

data class CleanupSnapshot(
    val subscriptionId: Int,
    val serial: String? = null,
    val ip: String? = null,
    val pppoeUsername: String? = null,
    val acsDeviceId: String? = null,
    val facadePhotoUrl: String? = null,
    val hostDeviceId: Int? = null,
)

data class CleanupFailure(
    val code: String,
    val message: String,
    val detail: String,
    val retryable: Boolean,
)

class CleanupStepException(val failure: CleanupFailure) : RuntimeException(failure.message)

data class CleanupStepView(
    val step: String,
    val state: String,
    val code: String? = null,
    val message: String? = null,
    val detail: String? = null,
    val retryable: Boolean = false,
    val attempts: Int = 1,
)

data class CleanupReport(
    val status: String,
    val steps: List<CleanupStepView>,
)

data class CleanupRun(
    val snapshot: CleanupSnapshot,
    val steps: Map<String, CleanupStepView> = emptyMap(),
)

interface CleanupJournal {
    fun load(subscriptionId: Int): CleanupRun
    fun save(updated: CleanupRun)
    fun drop(subscriptionId: Int)
}

interface SubscriptionTraceEraser {
    fun erase(snapshot: CleanupSnapshot)
}

class SubscriptionHardCleanupOrchestrator(
    private val journal: CleanupJournal,
    private val steps: List<Pair<String, (CleanupSnapshot) -> Unit>>,
    private val eraser: SubscriptionTraceEraser,
    private val maxAttempts: Int = 3,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) {
    fun execute(subscriptionId: Int): CleanupReport {
        var run = journal.load(subscriptionId)
        val views = mutableListOf<CleanupStepView>()
        var blocked = false
        for ((name, action) in steps) {
            val previous = run.steps[name]
            if (previous?.state == "ok") {
                views += previous
                continue
            }
            val view = attempt(name, run.snapshot, action)
            views += view
            run = run.copy(steps = run.steps + (name to view))
            journal.save(run)
            if (view.state != "ok") blocked = true
        }
        if (blocked) return CleanupReport(PARTIAL, views)
        val core = erase(run.snapshot)
        views += core
        if (core.state != "ok") {
            journal.save(run.copy(steps = run.steps + ("core" to core)))
            return CleanupReport(PARTIAL, views)
        }
        journal.drop(subscriptionId)
        return CleanupReport(COMPLETE, views)
    }

    private fun erase(snapshot: CleanupSnapshot): CleanupStepView = try {
        eraser.erase(snapshot)
        CleanupStepView(step = "core", state = "ok")
    } catch (ex: CleanupStepException) {
        viewOf("core", ex, 1)
    }

    private fun attempt(name: String, snapshot: CleanupSnapshot, action: (CleanupSnapshot) -> Unit): CleanupStepView {
        var last: CleanupStepException? = null
        var attempts = 0
        while (attempts < maxAttempts) {
            attempts++
            try {
                action(snapshot)
                return CleanupStepView(step = name, state = "ok", attempts = attempts)
            } catch (ex: CleanupStepException) {
                last = ex
                if (!ex.failure.retryable || attempts >= maxAttempts) break
                sleeper(200L * attempts)
            }
        }
        return viewOf(name, last!!, attempts)
    }

    private fun viewOf(name: String, ex: CleanupStepException, attempts: Int) = CleanupStepView(
        step = name,
        state = "fail",
        code = ex.failure.code,
        message = ex.failure.message,
        detail = ex.failure.detail,
        retryable = ex.failure.retryable,
        attempts = attempts,
    )

    private companion object {
        const val PARTIAL = "PARTIAL"
        const val COMPLETE = "COMPLETE"
    }
}
