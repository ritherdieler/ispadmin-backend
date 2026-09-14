package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Onu
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.requestbody.smartoltrequest.OnuAuthorizationRequest
import com.dscorp.wispadmin.wispadmin.service.onu.OnuOperationsPort
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDateTime

class CancelledOnuReuseServiceTest {

    private lateinit var onuService: OnuOperationsPort
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var eventPublisher: org.springframework.context.ApplicationEventPublisher
    private lateinit var service: CancelledOnuReuseService

    private val oltSn = "4857544315F5CD86"
    private val suffix = "15F5CD86"

    private val request = OnuAuthorizationRequest(
        olt_id = "1",
        pon_type = "gpon",
        board = "1",
        port = "1",
        sn = oltSn,
        vlan = "100",
        onu_type = "1",
        zone = "1",
        name = "cliente",
        onu_mode = "routing",
        custom_profile = "default"
    )

    private val snAlreadyExists = RuntimeException(
        "400 Bad Request: {\"error\":\"already exists on this OLT\",\"error_code\":\"sn_already_exists\"}"
    )

    @BeforeEach
    fun setUp() {
        onuService = mock(OnuOperationsPort::class.java)
        subscriptionRepository = mock(SubscriptionRepository::class.java)
        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher::class.java)
        service = CancelledOnuReuseService(onuService, subscriptionRepository, eventPublisher)
    }

    @Test
    fun `libera la ONU y reintenta cuando el SN pertenece a una suscripcion CANCELLED`() {
        val cancelled = Subscription(
            id = 1886,
            serviceStatus = ServiceStatus.CANCELLED,
            fiberOnuSn = "HWTC15F5CD86",
            cancellationDateDatetime = LocalDateTime.of(2025, 1, 1, 0, 0),
            equipmentCondition = EquipmentCondition.LOAN
        )
        `when`(subscriptionRepository.findCancelledByFiberOnuSn(oltSn, suffix)).thenReturn(listOf(cancelled))
        doThrow(snAlreadyExists).doNothing().`when`(onuService).authorizeOnuInSmartOltWidthPostMethod(request)

        service.authorizeWithCancelledReuse(request)

        verify(onuService).deleteOnuBySn(oltSn)
        verify(subscriptionRepository).save(cancelled)
        verify(onuService, times(2)).authorizeOnuInSmartOltWidthPostMethod(request)
        assertNull(cancelled.fiberOnuSn)
    }

    @Test
    fun `relanza el error sin liberar cuando no hay suscripcion CANCELLED con ese SN`() {
        `when`(subscriptionRepository.findCancelledByFiberOnuSn(oltSn, suffix)).thenReturn(emptyList())
        doThrow(snAlreadyExists).`when`(onuService).authorizeOnuInSmartOltWidthPostMethod(request)

        assertThrows(RuntimeException::class.java) {
            service.authorizeWithCancelledReuse(request)
        }

        verify(onuService, never()).deleteOnuBySn(anyString())
        verify(onuService, times(1)).authorizeOnuInSmartOltWidthPostMethod(request)
    }

    @Test
    fun `no libera ninguna ONU cuando la autorizacion es exitosa a la primera`() {
        doNothing().`when`(onuService).authorizeOnuInSmartOltWidthPostMethod(request)

        service.authorizeWithCancelledReuse(request)

        verify(onuService, times(1)).authorizeOnuInSmartOltWidthPostMethod(request)
        verify(onuService, never()).deleteOnuBySn(anyString())
        verify(subscriptionRepository, never()).findCancelledByFiberOnuSn(anyString(), anyString())
    }
}
