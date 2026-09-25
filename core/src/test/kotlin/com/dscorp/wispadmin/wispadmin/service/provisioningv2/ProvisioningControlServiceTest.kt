package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProvisioningControlServiceTest {
    private val journal = mockk<ProvisioningJournal>()
    private val service = ProvisioningControlService(journal, "staging")
    private val operation = ProvisioningOperation("op", "staging", 42, "ZTEGDC47BFFD")

    @Test fun `public commands cannot act on another subscription`() {
        every { journal.get("staging", "op") } returns operation
        assertThrows(NoSuchElementException::class.java) { service.cancel(99, ProvisioningActionRequest("op", 1)) }
        verify(exactly = 0) { journal.requestCancel(any(), any(), any()) }
    }

    @Test fun `failed operation exposes retry and preserves operation reference`() {
        val failed = operation.copy(state = ProvisioningState.FAILED)
        every { journal.get("staging", "op") } returns failed
        every { journal.requestRetry("staging", "op", 1) } returns operation
        assertTrue(service.progress(42, "op").canRetry)
        assertEquals("op", service.retry(42, ProvisioningActionRequest("op", 1)).operation.id)
        verify(exactly = 1) { journal.requestRetry("staging", "op", 1) }
    }

    @Test fun `cancellation failures offer only cancellation retry not registration retry`() {
        every { journal.get("staging", "op") } returns operation.copy(state = ProvisioningState.CANCEL_FAILED)
        val progress = service.progress(42, "op")
        assertFalse(progress.canRetry || progress.canCancel || progress.canStartAgain)
        assertTrue(progress.canRetryCancellation)
    }

    @Test fun `new registration is allowed only after verified cancellation`() {
        every { journal.get("staging", "op") } returns operation.copy(state = ProvisioningState.CANCELLED)
        val progress = service.progress(42, "op")
        assertTrue(progress.canStartAgain)
        assertFalse(progress.canCancel || progress.canRetry || progress.canRetryCancellation)
    }
}
