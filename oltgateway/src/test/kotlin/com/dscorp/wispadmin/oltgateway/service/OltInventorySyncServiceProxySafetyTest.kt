package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrAuditLogRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuStatusCurrentRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrSyncRunRepository
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrTaskRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OltInventorySyncServiceProxySafetyTest {

    @Test
    fun `syncInventory usa taskRepository del companion tras reinstanciar`() {
        val queryFacade = mockk<OltGatewayQueryFacade>()
        val oltRepository = mockk<OltMgrOltRepository>()
        val onuRepository = mockk<OltMgrOnuRepository>()
        val statusRepository = mockk<OltMgrOnuStatusCurrentRepository>()
        val auditLogRepository = mockk<OltMgrAuditLogRepository>()
        val syncRunRepository = mockk<OltMgrSyncRunRepository>()
        val firstTaskRepository = mockk<OltMgrTaskRepository>()
        val secondTaskRepository = mockk<OltMgrTaskRepository>()
        val properties = OltGatewayProperties().apply {
            oltId = "gigafiber-ma5608t"
            sync.skipWhenWriteRunning = true
        }

        every { firstTaskRepository.existsByStatus("running") } returns false
        every { secondTaskRepository.existsByStatus("running") } returns true
        every { syncRunRepository.save(any()) } answers { firstArg() }

        OltInventorySyncService(
            queryFacade = queryFacade,
            oltRepository = oltRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            auditLogRepository = auditLogRepository,
            syncRunRepository = syncRunRepository,
            taskRepository = firstTaskRepository,
            properties = properties
        )

        val service = OltInventorySyncService(
            queryFacade = queryFacade,
            oltRepository = oltRepository,
            onuRepository = onuRepository,
            statusRepository = statusRepository,
            auditLogRepository = auditLogRepository,
            syncRunRepository = syncRunRepository,
            taskRepository = secondTaskRepository,
            properties = properties
        )

        val result = service.syncInventory()

        assertEquals("write_task_running", result.skippedReason)
        verify(exactly = 1) { secondTaskRepository.existsByStatus("running") }
        verify(exactly = 0) { firstTaskRepository.existsByStatus(any()) }
        verify(exactly = 0) { queryFacade.listOnusParsed() }
    }
}
