package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.service.ScheduledTaskLogService
import com.dscorp.wispadmin.wispadmin.service.WhatsAppServiceCutNoticeService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IMikroTikService
import com.dscorp.wispadmin.wispadmin.service.validators.ISubscriptionValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
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
            whatsAppServiceCutNoticeService = whatsAppServiceCutNoticeService
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
