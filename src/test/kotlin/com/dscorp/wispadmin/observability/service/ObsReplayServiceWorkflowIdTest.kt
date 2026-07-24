package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsReplay
import com.dscorp.wispadmin.observability.repository.ObsReplayRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ObsReplayServiceWorkflowIdTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `persiste workflowId al almacenar replay`() {
        val repository = mockk<ObsReplayRepository>()
        val properties = ObservabilityProperties().apply {
            replay.storageDir = tempDir.absolutePath
        }
        val service = ObsReplayService(repository, properties)
        val captured = slot<ObsReplay>()
        every { repository.save(capture(captured)) } answers { firstArg() }

        service.store(
            sessionId = "sess-1",
            eventId = null,
            issueId = null,
            platform = "android",
            format = "frames",
            durationMs = 1000L,
            contentEncoding = "gzip",
            data = byteArrayOf(1, 2, 3),
            workflowId = "wf-abc"
        )

        assertEquals("wf-abc", captured.captured.workflowId)
    }
}
