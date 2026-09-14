package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.ScheduledTaskLogService
import com.dscorp.wispadmin.wispadmin.service.WhatsAppServiceCutNoticeService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IMikroTikService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeAccessService
import com.dscorp.wispadmin.wispadmin.service.validators.ISubscriptionValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher

class ServiceCutManagerServiceTest {

    private val subscriptionRepository = mock(SubscriptionRepository::class.java)
    private val mikrotikService = mock(IMikroTikService::class.java)
    private val subscriptionValidator = mock(ISubscriptionValidator::class.java)
    private val errorLogRepository = mock(ErrorLogRepository::class.java)
    private val scheduledTaskLogService = mock(ScheduledTaskLogService::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)
    private val whatsAppServiceCutNoticeService = mock(WhatsAppServiceCutNoticeService::class.java)
    private val pppoeAccessService = mock(PppoeAccessService::class.java)

    private lateinit var service: ServiceCutManagerService

    @BeforeEach
    fun setUp() {
        service = ServiceCutManagerService(
            subscriptionRepository = subscriptionRepository,
            mikrotikService = mikrotikService,
            subscriptionValidator = subscriptionValidator,
            errorLogRepository = errorLogRepository,
            scheduledTaskLogService = scheduledTaskLogService,
            eventPublisher = eventPublisher,
            whatsAppServiceCutNoticeService = whatsAppServiceCutNoticeService,
            pppoeAccessService = pppoeAccessService
        )
    }

    @Test
    fun `cutInternetService sends whatsapp notices to debtors excluding only tv fiber`() {
        val fiberDebtor = subscription(1, InstallationType.FIBER)
        val tvOnlyDebtor = subscription(2, InstallationType.ONLY_TV_FIBER)
        val debtors = listOf(fiberDebtor, tvOnlyDebtor)
        val capturedCandidates = mutableListOf<List<Subscription>>()

        `when`(subscriptionRepository.findSubscriptionsWithUnpaidAndAutoCutFlagActivePayments())
            .thenReturn(debtors)
        `when`(subscriptionRepository.findCancelledSubscriptions()).thenReturn(emptyList())
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            capturedCandidates.add(invocation.arguments[0] as List<Subscription>)
            null
        }.`when`(whatsAppServiceCutNoticeService).sendCutNoticesForCandidates(
            org.mockito.ArgumentMatchers.anyList()
        )

        service.cutInternetService()

        assertEquals(1, capturedCandidates.size)
        assertEquals(1, capturedCandidates.first().size)
        assertEquals(1, capturedCandidates.first().first().id)
    }

    @Test
    fun `cutInternetService processes cancelled subscriptions excluding only tv fiber`() {
        `when`(subscriptionRepository.findSubscriptionsWithUnpaidAndAutoCutFlagActivePayments())
            .thenReturn(emptyList())
        `when`(subscriptionRepository.findCancelledSubscriptions()).thenReturn(
            listOf(
                subscription(10, InstallationType.FIBER),
                subscription(11, InstallationType.ONLY_TV_FIBER)
            )
        )

        val summary = service.cutInternetService()

        assertEquals(1, summary.cancelled.processedCount)
        assertEquals(2, summary.cancelled.cancelledCount)
        assertEquals(1, summary.cancelled.omittedByTvCable)
        assertNotNull(summary.cancelled.message)
    }

    @Test
    fun `cutInternetService corta las suscripciones PPPOE_DYNAMIC por perfil y no por address list`() {
        val host = cloudCoreRouter()
        val pppoeDebtor = subscription(20, InstallationType.FIBER).apply {
            accessMode = AccessMode.PPPOE_DYNAMIC
            pppoeUsername = "gf20"
            hostDevice = host
        }
        `when`(subscriptionRepository.findSubscriptionsWithUnpaidAndAutoCutFlagActivePayments())
            .thenReturn(listOf(pppoeDebtor))
        `when`(subscriptionRepository.findCancelledSubscriptions()).thenReturn(emptyList())
        `when`(subscriptionRepository.save(pppoeDebtor)).thenReturn(pppoeDebtor)
        `when`(pppoeAccessService.cut(pppoeDebtor, host)).thenReturn(true)

        val summary = service.cutInternetService()

        assertEquals(1, summary.debtors.processedCount)
        assertEquals(1, summary.debtors.createdCount)
        assertEquals(0, summary.debtors.errorCount)
        assertTrue(pppoeDebtor.isServiceCutOff)
        verify(pppoeAccessService).cut(pppoeDebtor, host)
        verifyNoInteractions(subscriptionValidator)
    }

    @Test
    fun `cutInternetService corta cancelados PPPoE sin IP por perfil`() {
        val host = cloudCoreRouter()
        val cancelled = subscription(30, InstallationType.FIBER).apply {
            accessMode = AccessMode.PPPOE_DYNAMIC
            pppoeUsername = "gf30"
            ip = null
            hostDevice = host
        }
        `when`(subscriptionRepository.findSubscriptionsWithUnpaidAndAutoCutFlagActivePayments())
            .thenReturn(emptyList())
        `when`(subscriptionRepository.findCancelledSubscriptions()).thenReturn(listOf(cancelled))
        `when`(subscriptionRepository.save(cancelled)).thenReturn(cancelled)
        `when`(pppoeAccessService.cut(cancelled, host)).thenReturn(true)

        val summary = service.cutInternetService()

        assertEquals(1, summary.cancelled.processedCount)
        assertEquals(1, summary.cancelled.createdCount)
        verify(pppoeAccessService).cut(cancelled, host)
    }

    @Test
    fun `cutInternetService reporta error cuando el corte PPPoE falla`() {
        val host = cloudCoreRouter()
        val pppoeDebtor = subscription(21, InstallationType.FIBER).apply {
            accessMode = AccessMode.PPPOE_DYNAMIC
            pppoeUsername = "gf21"
            hostDevice = host
        }
        `when`(subscriptionRepository.findSubscriptionsWithUnpaidAndAutoCutFlagActivePayments())
            .thenReturn(listOf(pppoeDebtor))
        `when`(subscriptionRepository.findCancelledSubscriptions()).thenReturn(emptyList())
        `when`(pppoeAccessService.cut(pppoeDebtor, host)).thenReturn(false)

        val summary = service.cutInternetService()

        assertEquals(0, summary.debtors.createdCount)
        assertEquals(1, summary.debtors.errorCount)
        assertEquals(1, summary.allFailedItems.size)
        verify(subscriptionRepository, never()).save(pppoeDebtor)
    }

    private fun cloudCoreRouter() = NetworkDevice(
        id = 8,
        name = "MK8",
        ipAddress = "38.224.231.4",
        networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER,
        vlanId = 100
    )

    private fun subscription(id: Int, installationType: InstallationType): Subscription {
        return Subscription(
            firstName = "Juan",
            lastName = "Perez",
            phone = "987654321",
            installationType = installationType,
            equipmentCondition = EquipmentCondition.LOAN,
        ).apply { this.id = id }
    }
}
