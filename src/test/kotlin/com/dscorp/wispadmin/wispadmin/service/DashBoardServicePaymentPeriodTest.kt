package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.oltclient.OltGatewayHttpClient
import org.springframework.beans.factory.ObjectProvider
import com.fasterxml.jackson.databind.ObjectMapper
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionsStaticsRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.MonthlyCollectsRepository
import com.dscorp.wispadmin.wispadmin.repository.OutlayRepository
import com.dscorp.wispadmin.wispadmin.repository.FixedCostRepository
import com.dscorp.wispadmin.wispadmin.repository.CorporationCustomerRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionLogRepository
import com.dscorp.wispadmin.wispadmin.repository.AssistanceTicketRepository
import com.dscorp.wispadmin.wispadmin.repository.PlanRepository
import com.dscorp.wispadmin.wispadmin.repository.InstallationOrderRepository
import com.dscorp.wispadmin.wispadmin.repository.PlaceRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.repository.NetworkDeviceRepository
import com.dscorp.wispadmin.wispadmin.repository.NapBoxRepository
import com.dscorp.wispadmin.wispadmin.util.PerformanceMonitor
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class DashBoardServicePaymentPeriodTest {

    private val paymentRepository = mockk<PaymentRepository>(relaxed = true)
    private val performanceMonitor = mockk<PerformanceMonitor>(relaxed = true)

    private val service = DashBoardService(
        paymentRepository = paymentRepository,
        subscriptionRepository = mockk(relaxed = true),
        subscriptionsStaticsRepository = mockk(relaxed = true),
        monthlyCollectsRepository = mockk(relaxed = true),
        outLayRepository = mockk(relaxed = true),
        fixedCostRepository = mockk(relaxed = true),
        corporateClientRepository = mockk(relaxed = true),
        subscriptionLogRepository = mockk(relaxed = true),
        assistanceTicketRepository = mockk(relaxed = true),
        planRepository = mockk(relaxed = true),
        installationOrderRepository = mockk(relaxed = true),
        placeRepository = mockk(relaxed = true),
        userRepository = mockk(relaxed = true),
        networkDeviceRepository = mockk(relaxed = true),
        napBoxRepository = mockk(relaxed = true),
        oltGatewayHttp = mockk<ObjectProvider<OltGatewayHttpClient>>(relaxed = true),
        objectMapper = ObjectMapper(),
        performanceMonitor = performanceMonitor,
        paymentStatisticsService = mockk(relaxed = true),
    )

    @Test
    fun `createDashBoardV2 uses billing cycle for all monetary resume queries`() {
        val grossStart = slot<LocalDateTime>()
        val grossEnd = slot<LocalDateTime>()
        val raisedStart = slot<LocalDateTime>()
        val raisedEnd = slot<LocalDateTime>()
        val discountStart = slot<LocalDateTime>()
        val discountEnd = slot<LocalDateTime>()
        val pendingStart = slot<LocalDateTime>()
        val pendingEnd = slot<LocalDateTime>()

        every { paymentRepository.getGrossRevenueBetween(capture(grossStart), capture(grossEnd)) } returns 0.0
        every { paymentRepository.getTotalRaisedBetween(capture(raisedStart), capture(raisedEnd)) } returns 0.0
        every { paymentRepository.getTotalDiscountsBetween(capture(discountStart), capture(discountEnd)) } returns 0.0
        every {
            paymentRepository.calculateTotalToCollectBetween(capture(pendingStart), capture(pendingEnd))
        } returns 0.0
        every { paymentRepository.getTop6GrossRevenueHistory() } returns emptyList()
        every { paymentRepository.getLasMonthsPaymentMethodStatics(any(), any()) } returns emptyList()

        service.createDashBoardV2()

        assertEquals(grossStart.captured, raisedStart.captured)
        assertEquals(grossEnd.captured, raisedEnd.captured)
        assertEquals(grossStart.captured, discountStart.captured)
        assertEquals(grossEnd.captured, discountEnd.captured)
        assertEquals(grossStart.captured, pendingStart.captured)
        assertEquals(grossEnd.captured, pendingEnd.captured)

        verify(exactly = 1) { paymentRepository.getGrossRevenueBetween(any(), any()) }
        verify(exactly = 1) { paymentRepository.getTotalRaisedBetween(any(), any()) }
        verify(exactly = 1) { paymentRepository.getTotalDiscountsBetween(any(), any()) }
        verify(exactly = 1) { paymentRepository.calculateTotalToCollectBetween(any(), any()) }
    }
}
