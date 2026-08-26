package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Tr069AsyncApplicatorTest {

    private val provisioner = mockk<Tr069PostInstallProvisioner>()
    private val repository = mockk<SubscriptionRepository>()
    private lateinit var applicator: Tr069AsyncApplicator

    @BeforeEach
    fun setUp() {
        applicator = Tr069AsyncApplicator(
            provisioner = provisioner,
            repository = repository,
            executor = ImmediateExecutor,
        )
    }

    @Test
    fun `schedule runs apply on executor`() {
        val dto = SubscriptionDto(id = 10, tr069ProvisionStatus = Tr069ProvisionStatus.PENDING)
        val request = sampleRequest()
        every { provisioner.apply(dto, request) } returns dto.copy(
            tr069ProvisionStatus = Tr069ProvisionStatus.COMPLETE,
        )

        applicator.schedule(dto, request)

        verify(exactly = 1) { provisioner.apply(dto, request) }
        assertFalse(applicator.isInFlight(10))
    }

    @Test
    fun `schedule skips when already in flight`() {
        val dto = SubscriptionDto(id = 11)
        val request = sampleRequest()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        applicator = Tr069AsyncApplicator(provisioner, repository, BlockingExecutor(started, release))
        every { provisioner.apply(any(), any()) } returns dto

        applicator.schedule(dto, request)
        assertTrue(started.await(1, TimeUnit.SECONDS))
        assertTrue(applicator.isInFlight(11))

        applicator.schedule(dto, request)
        release.countDown()
        Thread.sleep(50)

        verify(exactly = 1) { provisioner.apply(any(), any()) }
    }

    @Test
    fun `applyExclusive returns current dto when in flight`() {
        val dto = SubscriptionDto(id = 12, tr069ProvisionStatus = Tr069ProvisionStatus.PENDING)
        val request = sampleRequest()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        applicator = Tr069AsyncApplicator(provisioner, repository, BlockingExecutor(started, release))
        every { provisioner.apply(any(), any()) } returns dto
        val entity = mockk<Subscription>()
        every { entity.toDto() } returns dto.copy(tr069Message = "en curso")
        every { repository.findById(12) } returns Optional.of(entity)

        applicator.schedule(dto, request)
        assertTrue(started.await(1, TimeUnit.SECONDS))

        val result = applicator.applyExclusive(dto, request)
        assertEquals("en curso", result.tr069Message)
        verify(exactly = 0) { provisioner.apply(dto, request) }

        release.countDown()
    }

    private fun sampleRequest() = SubscriptionRequest(
        firstName = "Ana",
        lastName = "Lopez",
        dni = "87654321",
        address = "Calle 1",
        phone = "999",
        subscriptionDate = System.currentTimeMillis(),
        planId = 1,
        additionalDeviceIds = emptyList(),
        placeId = 1,
        location = GeoLocation(-11.0, -77.0),
        technicianId = 1,
        hostDeviceId = 1,
        installationType = InstallationType.WIRELESS,
        equipmentCondition = EquipmentCondition.LOAN,
    )

    private object ImmediateExecutor : AbstractExecutorService() {
        private var shutdown = false
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() { shutdown = true }
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown() = shutdown
        override fun isTerminated() = shutdown
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }

    private class BlockingExecutor(
        private val started: CountDownLatch,
        private val release: CountDownLatch,
    ) : AbstractExecutorService() {
        private var shutdown = false
        override fun execute(command: Runnable) {
            Thread {
                started.countDown()
                release.await(2, TimeUnit.SECONDS)
                command.run()
            }.start()
        }
        override fun shutdown() { shutdown = true }
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
        override fun isShutdown() = shutdown
        override fun isTerminated() = shutdown
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
}
