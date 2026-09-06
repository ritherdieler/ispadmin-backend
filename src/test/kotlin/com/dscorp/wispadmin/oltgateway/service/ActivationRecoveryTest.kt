package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.events.RecordingEventBus
import com.dscorp.wispadmin.oltgateway.api.SmartOltActionResponseDto
import com.dscorp.wispadmin.oltgateway.client.AcsCpeClient
import com.dscorp.wispadmin.oltgateway.client.AcsCpeProvisionResponse
import com.dscorp.wispadmin.oltgateway.dto.CpeProvisionStatus
import com.dscorp.wispadmin.oltgateway.dto.OltActivationStatus
import com.dscorp.wispadmin.oltgateway.dto.OnuActivateRequestDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.core.io.ClassPathResource
import org.springframework.web.client.ResourceAccessException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class ActivationRecoveryTest {
    private val request = OnuActivateRequestDto(sn = "SN1", oltId = "1", board = "1", port = "1")

    @Test
    fun `acs timeout stays pending and later status can complete without a second provision`() {
        val facade = mockk<OltManagerFacade>()
        every { facade.authorizeOnu(any()) } returns SmartOltActionResponseDto(status = true, unique_external_id = "ext-1")
        val acs = mockk<AcsCpeClient>()
        every { acs.provision(any()) } throws ResourceAccessException("timeout", SocketTimeoutException("fixture"))
        every { acs.status("SN1") } returns AcsCpeProvisionResponse("SN1", CpeProvisionStatus.COMPLETE)
        val journal = MemoryActivationJournal()
        val service = OnuActivationService(facade, acs, RecordingEventBus(), journal, Executor { it.run() })
        val first = service.activate(request)
        assertEquals(OltActivationStatus.COMPLETE, first.oltStatus)
        assertEquals(CpeProvisionStatus.PENDING, first.cpeStatus)
        assertNotEquals(CpeProvisionStatus.FAILED, first.cpeStatus)
        journal.bySn("SN1")!!.leaseUntil = 0
        service.recover()
        assertEquals(CpeProvisionStatus.COMPLETE, service.statusBySn("SN1")!!.cpeStatus)
        verify(exactly = 1) { acs.provision(any()) }
    }

    @Test
    fun `absent remote status retries provision until the attempt budget is spent`() {
        val facade = mockk<OltManagerFacade>()
        every { facade.authorizeOnu(any()) } returns SmartOltActionResponseDto(status = true, unique_external_id = "ext-1")
        val acs = mockk<AcsCpeClient>()
        every { acs.provision(any()) } throws ResourceAccessException("timeout", SocketTimeoutException("fixture"))
        every { acs.status("SN1") } returns null
        val journal = MemoryActivationJournal()
        val service = OnuActivationService(facade, acs, RecordingEventBus(), journal, Executor { it.run() })
        service.activate(request)
        repeat(5) {
            journal.bySn("SN1")!!.leaseUntil = 0
            service.recover()
        }
        verify(exactly = 3) { acs.provision(any()) }
        assertEquals(CpeProvisionStatus.PENDING, service.statusBySn("SN1")!!.cpeStatus)
    }

    @Test
    fun `only one worker acquires a live activation lease`() {
        val db = DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
        ResourceDatabasePopulator(ClassPathResource("db/oltgateway/V1__activation_operation.sql")).execute(db)
        val journal = JdbcActivationJournal(JdbcTemplate(db), jacksonObjectMapper().findAndRegisterModules(), "test-key-at-least-thirty-two-characters")
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val acquired = AtomicInteger()
        val pool = Executors.newFixedThreadPool(2)
        repeat(2) {
            pool.execute {
                start.await()
                if (journal.acquire(request).second) acquired.incrementAndGet()
                done.countDown()
            }
        }
        start.countDown()
        done.await(5, TimeUnit.SECONDS)
        pool.shutdownNow()
        assertEquals(1, acquired.get())
    }

    @Test
    fun `a colliding request cannot steal an existing serial`() {
        val journal = MemoryActivationJournal()
        journal.acquire(request)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            journal.acquire(request.copy(vlan = "200"))
        }
    }

    @Test
    fun `a completed activation can start again with a new request`() {
        val journal = MemoryActivationJournal()
        val first = journal.acquire(request)
        first.first.stage = "DONE"
        journal.save(first.first)
        val second = journal.acquire(request.copy(vlan = "200", name = "retry"))
        assertEquals(true, second.second)
        assertEquals("OLT", second.first.stage)
        assertEquals("200", second.first.request.vlan)
    }
}
