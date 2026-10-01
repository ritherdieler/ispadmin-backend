package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProvisioningTransitionsTest {
    private val transitions = ProvisioningTransitions()
    private fun fresh() = ProvisioningOperation("op", "staging", 42, "ZTEGDC47BFFD")
    private fun through(last: ProvisioningStage): ProvisioningOperation {
        var operation = fresh()
        for (stage in ProvisioningStage.values().takeWhile { it.ordinal <= last.ordinal }) {
            operation = transitions.finished(transitions.started(operation, stage), stage)
        }
        return operation
    }

    @Test fun `registration stage list no longer includes the retired management protocol`() {
        assertEquals(
            listOf("VALIDATE", "MIKROTIK", "OLT", "ACS_CONTACT", "INTERNET", "WIFI", "WAN_CLEANUP", "VERIFY"),
            ProvisioningStage.values().map { it.name },
        )
        assertEquals(ProvisioningStage.WIFI, transitions.next(through(ProvisioningStage.INTERNET)))
        assertEquals(ProvisioningStage.WAN_CLEANUP, transitions.next(through(ProvisioningStage.WIFI)))
    }

    @Test fun `operation model exposes durable phase without a legacy management mode`() {
        assertTrue(ProvisioningOperation::class.java.declaredFields.any { it.name == "phase" })
        assertFalse(ProvisioningOperation::class.java.declaredFields.any { it.name == "managementMode" })
    }

    @Test fun `wifi retry preserves Internet without a retired management checkpoint`() {
        val ready = through(ProvisioningStage.INTERNET)
        val failed = transitions.failed(transitions.started(ready, ProvisioningStage.WIFI), ProvisioningStage.WIFI,
            ProvisioningFailure("CWMP_FAULT", "No se pudo aplicar WiFi", true))
        assertEquals(ProvisioningState.FAILED, failed.state)
        val retried = transitions.retry(failed, failed.revision)
        assertEquals(ProvisioningStage.WIFI, transitions.next(retried))
        assertEquals(CheckpointState.SUCCEEDED, retried.checkpoints[ProvisioningStage.INTERNET.ordinal].state)
        assertEquals(retried, transitions.retry(retried, retried.revision))
    }

    @Test fun `manual ACS retry resumes the waiting contact checkpoint`() {
        val waiting = fresh().copy(
            flowVersion = 3,
            phase = ProvisioningPhase.WAITING_FOR_ACS,
            state = ProvisioningState.WAITING,
            checkpoints = fresh().checkpoints.map {
                if (it.stage == ProvisioningStage.ACS_CONTACT) it.copy(state = CheckpointState.WAITING, attempts = 1, touched = true)
                else it
            },
        )

        val retried = transitions.retry(waiting, waiting.revision)

        assertEquals(ProvisioningState.PENDING, retried.state)
        assertEquals(CheckpointState.PENDING, retried.checkpoints[ProvisioningStage.ACS_CONTACT.ordinal].state)
        assertEquals(1, retried.checkpoints[ProvisioningStage.ACS_CONTACT.ordinal].attempts)
    }

    @Test fun `preauthorization has no subscription and is never claimed by provisioning worker`() {
        val operation = ProvisioningOperation(
            id = "preauth-op",
            environment = "staging",
            subscriptionId = null,
            serial = "VSOL0031C0B6",
            flowVersion = 3,
            phase = ProvisioningPhase.WAITING_FOR_ACS,
            operatorId = 77,
            onuTarget = ProvisioningOnuTarget("olt", "gpon", "1", "6", "VSOLVA74", 100),
            state = ProvisioningState.WAITING,
        )

        assertNull(transitions.next(operation))
        assertEquals(ProvisioningPhase.WAITING_FOR_ACS, operation.phase)
    }

    @Test fun `cancellation compensates uncertain writes and preserves management until Internet undone`() {
        val wifiInFlight = transitions.started(through(ProvisioningStage.INTERNET), ProvisioningStage.WIFI)
        var cancelled = transitions.cancel(wifiInFlight, wifiInFlight.revision)
        assertNull(transitions.next(cancelled))
        assertEquals(ProvisioningStage.WIFI, transitions.nextCompensation(cancelled))
        for (stage in listOf(ProvisioningStage.WIFI, ProvisioningStage.INTERNET, ProvisioningStage.ACS_CONTACT,
            ProvisioningStage.MIKROTIK, ProvisioningStage.OLT, ProvisioningStage.VALIDATE)) {
            assertEquals(stage, transitions.nextCompensation(cancelled))
            cancelled = transitions.compensated(cancelled, stage)
        }
        assertEquals(ProvisioningState.CANCELLING, cancelled.state)
        assertNull(transitions.nextCompensation(cancelled))
        assertThrows(IllegalStateException::class.java) { transitions.retry(cancelled, cancelled.revision) }
    }

    @Test fun `stale revision and skipping stages are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { transitions.cancel(fresh(), 0) }
        assertThrows(IllegalArgumentException::class.java) { transitions.started(fresh(), ProvisioningStage.WIFI) }
    }

    @Test fun `cancelled execution cannot report late success`() {
        val running = transitions.started(fresh(), ProvisioningStage.VALIDATE)
        val cancelled = transitions.cancel(running, running.revision)
        assertThrows(IllegalStateException::class.java) { transitions.finished(cancelled, ProvisioningStage.VALIDATE) }
    }

    @Test fun `successful legacy provisioning cannot be cancelled through subscription controls`() {
        val done = through(ProvisioningStage.VERIFY)
        assertEquals(ProvisioningState.SUCCEEDED, done.state)
        assertEquals(done, transitions.retry(done, done.revision))
        assertThrows(IllegalStateException::class.java) { transitions.cancel(done, done.revision) }
    }

    @Test fun `successful registration flow can be cancelled for full cleanup`() {
        val done = through(ProvisioningStage.VERIFY).copy(flowVersion = 3, operatorId = 12)
        val cancelled = transitions.cancelRegistration(done, done.revision)
        assertEquals(ProvisioningState.CANCEL_REQUESTED, cancelled.state)
        assertEquals(ProvisioningStage.VERIFY, transitions.nextCompensation(cancelled))
    }
}
