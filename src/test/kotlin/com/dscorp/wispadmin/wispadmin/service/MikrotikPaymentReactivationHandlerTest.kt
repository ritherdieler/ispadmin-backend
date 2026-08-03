package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.dto.NetworkDeviceDto
import com.dscorp.wispadmin.wispadmin.dto.SubscriptionDto
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.routeros.port.MikrotikTimeoutException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MikrotikPaymentReactivationHandlerTest {

    private val errorLogRepository = mockk<ErrorLogRepository>(relaxed = true)
    private lateinit var handler: MikrotikPaymentReactivationHandler

    @BeforeEach
    fun setUp() {
        handler = MikrotikPaymentReactivationHandler(errorLogRepository)
        mockkStatic("com.dscorp.wispadmin.wispadmin.extensions.ExtensionsKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("com.dscorp.wispadmin.wispadmin.extensions.ExtensionsKt")
    }

    @Test
    fun reactivateFromDebtorsList_whenMikrotikFails_persistsErrorLogWithoutPropagating() {
        every { any<NetworkDeviceDto>().executeCommand(any()) } throws
            MikrotikTimeoutException("rest POST /rest/ip/firewall/address-list/print: Read timed out")
        every { errorLogRepository.save(any()) } answers { firstArg() }

        assertDoesNotThrow { handler.reactivateFromDebtorsList(sampleSubscription()) }

        verify(exactly = 1) { errorLogRepository.save(any()) }
    }

    @Test
    fun reactivateFromDebtorsList_withoutHostDevice_doesNotWriteErrorLog() {
        handler.reactivateFromDebtorsList(
            SubscriptionDto(
                id = 1,
                ip = "10.1.1.5",
                hostDevice = null
            )
        )
        verify(exactly = 0) { errorLogRepository.save(any()) }
    }

    private fun sampleSubscription(): SubscriptionDto {
        val host = NetworkDeviceDto(
            id = 8,
            name = "MK",
            ipAddress = "38.224.231.4",
            username = "admin",
            password = "secret"
        )
        return SubscriptionDto(
            id = 1686,
            ip = "10.11.104.50",
            hostDevice = host
        )
    }
}
