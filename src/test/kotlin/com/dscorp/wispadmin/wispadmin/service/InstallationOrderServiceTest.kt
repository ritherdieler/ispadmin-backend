package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.InstallationOrder
import com.dscorp.wispadmin.wispadmin.data.model.InstallationOrderStatus
import com.dscorp.wispadmin.wispadmin.repository.InstallationOrderRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.util.fcm.FcmMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Optional

class InstallationOrderServiceTest {

    private val installationOrderRepository = mockk<InstallationOrderRepository>()
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val notificationService = mockk<NotificationService>(relaxed = true)

    private val service = InstallationOrderService(
        installationOrderRepository = installationOrderRepository,
        userRepository = userRepository,
        notificationService = notificationService,
    )

    @Test
    fun `closeInstallationOrder is idempotent when order is already closed`() {
        val closedOrder = InstallationOrder(
            id = 10,
            customerFirstName = "Ana",
            customerLastName = "Lopez",
            status = InstallationOrderStatus.CERRADO,
        )

        every { installationOrderRepository.findById(10) } returns Optional.of(closedOrder)

        val result = service.closeInstallationOrder(10)

        assertEquals(InstallationOrderStatus.CERRADO, result.status)
        verify(exactly = 0) { installationOrderRepository.save(any()) }
        verify(exactly = 0) {
            notificationService.sendTopicNotification(
                topic = any(),
                title = any(),
                message = any(),
                data = any(),
                type = FcmMessage.FcmMessageType.SALES_CLOSED_INSTALLATION_ORDER,
                id = any(),
            )
        }
    }
}
