package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.User
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.requestbody.MultiPaymentRegisterRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentCreateRequest
import com.dscorp.wispadmin.wispadmin.requestbody.PaymentRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyIterable
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDateTime
import java.util.Optional
import javax.persistence.EntityNotFoundException

class PaymentServiceTest {

    private val paymentRepository = mock(PaymentRepository::class.java)
    private val subscriptionRepository = mock(SubscriptionRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val inboundMessageRepository = mock(WhatsAppInboundMessageRepository::class.java)
    private val reactivationHandler = mock(MikrotikPaymentReactivationHandler::class.java)
    private val mediaBasePath = "/usr/local/tomcat/data/whatsapp/media"
    private val proofPathResolver = PaymentProofPathResolver(
        WhatsAppProperties().apply { media.basePath = mediaBasePath },
    )

    private val service = PaymentService(
        paymentRepository = paymentRepository,
        subscriptionRepository = subscriptionRepository,
        userRepository = userRepository,
        inboundMessageRepository = inboundMessageRepository,
        mikrotikPaymentReactivationHandler = reactivationHandler,
        proofPathResolver = proofPathResolver,
    )

    private fun publicProofPath(filename: String): String =
        Paths.get(mediaBasePath, filename).normalize().toString()

    private val subscription = Subscription(
        firstName = "Juan",
        lastName = "Perez",
        plan = Plan(id = 1, name = "50MB", price = 79.9),
        serviceStatus = ServiceStatus.ACTIVE,
        equipmentCondition = EquipmentCondition.LOAN,
    ).apply { id = 10 }

    private val responsible = User(id = 5, name = "Cobrador")

    @Test
    fun `registerPayment liquidates unpaid payment and sets proofImagePath from explicit path`() {
        val payment = unpaidPayment(id = 1, amountToPay = 79.9)
        `when`(paymentRepository.findById(1)).thenReturn(Optional.of(payment))
        `when`(userRepository.getReferenceById(5)).thenReturn(responsible)
        `when`(paymentRepository.save(payment)).thenReturn(payment)
        `when`(paymentRepository.findPendingPaymentsBySubscriptionId(10)).thenReturn(0)

        val result = service.registerPayment(
            PaymentRequest(
                id = 1,
                method = "Yape",
                responsibleId = 5,
                electronicPayerName = "Juan P.",
                proofImagePath = "/media/proofs/receipt-001.jpg",
            ),
        )

        assertTrue(result.paid)
        assertEquals("Yape", result.method)
        assertEquals(79.9, result.amountPaid)
        assertEquals(responsible, result.responsible)
        assertEquals("receipt-001.jpg", result.proofImagePath)
        assertEquals(publicProofPath("receipt-001.jpg"), service.toPublicDto(result).proofImagePath)
        verify(paymentRepository).save(payment)
    }

    @Test
    fun `registerPayment resolves proofImagePath from inbound message mediaStoredPath`() {
        val payment = unpaidPayment(id = 2, amountToPay = 50.0)
        val inbound = WhatsAppInboundMessage(
            metaMessageId = "wamid-abc",
            phone = "51999999999",
            mediaStoredPath = "/media/inbound/proof-42.jpg",
        ).apply { id = 42 }

        `when`(paymentRepository.findById(2)).thenReturn(Optional.of(payment))
        `when`(userRepository.getReferenceById(5)).thenReturn(responsible)
        `when`(paymentRepository.save(payment)).thenReturn(payment)
        `when`(paymentRepository.findPendingPaymentsBySubscriptionId(10)).thenReturn(0)
        `when`(inboundMessageRepository.findById(42)).thenReturn(Optional.of(inbound))

        val result = service.registerPayment(
            PaymentRequest(
                id = 2,
                method = "Plin",
                responsibleId = 5,
                inboundMessageId = 42,
            ),
        )

        assertEquals("proof-42.jpg", result.proofImagePath)
        assertEquals(publicProofPath("proof-42.jpg"), service.toPublicDto(result).proofImagePath)
    }

    @Test
    fun `registerPayment rejects discount greater than amount to pay`() {
        val payment = unpaidPayment(id = 3, amountToPay = 50.0)
        `when`(paymentRepository.findById(3)).thenReturn(Optional.of(payment))

        val error = assertThrows(Exception::class.java) {
            service.registerPayment(
                PaymentRequest(
                    id = 3,
                    method = "Efectivo",
                    responsibleId = 5,
                    discountAmount = 60.0,
                ),
            )
        }

        assertEquals("El descuento no puede ser mayor al monto a pagar", error.message)
        verify(paymentRepository, never()).save(payment)
    }

    @Test
    fun `registerPayment rejects inboundMessageId without mediaStoredPath`() {
        val payment = unpaidPayment(id = 4, amountToPay = 50.0)
        val inbound = WhatsAppInboundMessage(
            metaMessageId = "wamid-no-media",
            phone = "51988888888",
            mediaStoredPath = null,
        ).apply { id = 99 }

        `when`(paymentRepository.findById(4)).thenReturn(Optional.of(payment))
        `when`(inboundMessageRepository.findById(99)).thenReturn(Optional.of(inbound))

        val error = assertThrows(IllegalArgumentException::class.java) {
            service.registerPayment(
                PaymentRequest(
                    id = 4,
                    method = "Yape",
                    responsibleId = 5,
                    inboundMessageId = 99,
                ),
            )
        }

        assertTrue(error.message!!.contains("media"))
        verify(paymentRepository, never()).save(payment)
    }

    @Test
    fun `registerPayments liquidates multiple unpaid payments sharing same proofImagePath`() {
        val payment1 = unpaidPayment(id = 10, amountToPay = 79.9)
        val payment2 = unpaidPayment(id = 11, amountToPay = 79.9)
        val payments = listOf(payment1, payment2)

        `when`(paymentRepository.findAllById(anyIterable())).thenReturn(payments)
        `when`(userRepository.getReferenceById(5)).thenReturn(responsible)
        `when`(paymentRepository.saveAll(payments)).thenReturn(payments)
        `when`(paymentRepository.findPendingPaymentsBySubscriptionId(10)).thenReturn(0)

        val result = service.registerPayments(
            MultiPaymentRegisterRequest(
                paymentIds = listOf(10, 11),
                method = "Transferencia",
                responsibleId = 5,
                proofImagePath = "/media/proofs/batch-receipt.jpg",
            ),
        )

        assertEquals(2, result.size)
        assertTrue(result.all { it.paid })
        assertTrue(result.all { it.proofImagePath == "batch-receipt.jpg" })
        assertTrue(result.all { service.toPublicDto(it).proofImagePath == publicProofPath("batch-receipt.jpg") })
        assertTrue(result.all { it.method == "Transferencia" })
        verify(paymentRepository).saveAll(payments)
    }

    @Test
    fun `registerPayments rejects when any payment is already paid`() {
        val paid = unpaidPayment(id = 20, amountToPay = 79.9).apply { paid = true }
        val unpaid = unpaidPayment(id = 21, amountToPay = 79.9)
        `when`(paymentRepository.findAllById(anyIterable())).thenReturn(listOf(paid, unpaid))

        val error = assertThrows(IllegalArgumentException::class.java) {
            service.registerPayments(
                MultiPaymentRegisterRequest(
                    paymentIds = listOf(20, 21),
                    method = "Yape",
                    responsibleId = 5,
                ),
            )
        }

        assertTrue(error.message!!.contains("paid", ignoreCase = true))
        verify(paymentRepository, never()).saveAll(anyList())
    }

    @Test
    fun `registerPayment rejects already paid payment`() {
        val payment = unpaidPayment(id = 5, amountToPay = 50.0).apply { paid = true }
        `when`(paymentRepository.findById(5)).thenReturn(Optional.of(payment))

        val error = assertThrows(IllegalArgumentException::class.java) {
            service.registerPayment(
                PaymentRequest(
                    id = 5,
                    method = "Yape",
                    responsibleId = 5,
                ),
            )
        }

        assertTrue(error.message!!.contains("paid", ignoreCase = true))
        verify(paymentRepository, never()).save(payment)
    }

    @Test
    fun `registerPayment rejects missing payment id`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            service.registerPayment(
                PaymentRequest(
                    id = null,
                    method = "Yape",
                    responsibleId = 5,
                ),
            )
        }

        assertTrue(error.message!!.contains("id", ignoreCase = true))
        verify(paymentRepository, never()).findById(any())
    }

    @Test
    fun `registerPayment rejects purged inbound message media`() {
        val payment = unpaidPayment(id = 6, amountToPay = 50.0)
        val inbound = WhatsAppInboundMessage(
            metaMessageId = "wamid-purged",
            phone = "51977777777",
            mediaStoredPath = "/media/inbound/purged.jpg",
            mediaPurgedAt = LocalDateTime.now(),
        ).apply { id = 77 }

        `when`(paymentRepository.findById(6)).thenReturn(Optional.of(payment))
        `when`(inboundMessageRepository.findById(77)).thenReturn(Optional.of(inbound))

        val error = assertThrows(IllegalArgumentException::class.java) {
            service.registerPayment(
                PaymentRequest(
                    id = 6,
                    method = "Yape",
                    responsibleId = 5,
                    inboundMessageId = 77,
                ),
            )
        }

        assertTrue(error.message!!.contains("purged", ignoreCase = true))
        verify(paymentRepository, never()).save(payment)
    }

    @Test
    fun `registerPayments rejects duplicate payment ids`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            service.registerPayments(
                MultiPaymentRegisterRequest(
                    paymentIds = listOf(10, 10),
                    method = "Yape",
                    responsibleId = 5,
                ),
            )
        }

        assertTrue(error.message!!.contains("Duplicate", ignoreCase = true))
        verify(paymentRepository, never()).saveAll(anyList())
    }

    @Test
    fun `registerPayments rejects empty payment ids`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            service.registerPayments(
                MultiPaymentRegisterRequest(
                    paymentIds = emptyList(),
                    method = "Yape",
                    responsibleId = 5,
                ),
            )
        }

        assertTrue(error.message!!.contains("At least one", ignoreCase = true))
        verify(paymentRepository, never()).saveAll(anyList())
    }

    @Test
    fun `registerPayments rejects payments from different subscriptions`() {
        val otherSubscription = Subscription(
            firstName = "Ana",
            lastName = "Lopez",
            plan = Plan(id = 2, name = "100MB", price = 99.9),
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN,
        ).apply { id = 20 }

        val payment1 = unpaidPayment(id = 30, amountToPay = 79.9)
        val payment2 = unpaidPayment(id = 31, amountToPay = 79.9).apply {
            subscription = otherSubscription
        }

        `when`(paymentRepository.findAllById(anyIterable())).thenReturn(listOf(payment1, payment2))

        val error = assertThrows(IllegalArgumentException::class.java) {
            service.registerPayments(
                MultiPaymentRegisterRequest(
                    paymentIds = listOf(30, 31),
                    method = "Yape",
                    responsibleId = 5,
                ),
            )
        }

        assertTrue(error.message!!.contains("subscription", ignoreCase = true))
        verify(paymentRepository, never()).saveAll(anyList())
    }

    @Test
    fun `registerPayments resolves proofImagePath from inbound message mediaStoredPath`() {
        val payment1 = unpaidPayment(id = 40, amountToPay = 79.9)
        val payment2 = unpaidPayment(id = 41, amountToPay = 79.9)
        val payments = listOf(payment1, payment2)
        val inbound = WhatsAppInboundMessage(
            metaMessageId = "wamid-batch",
            phone = "51966666666",
            mediaStoredPath = "/media/inbound/batch-proof.jpg",
        ).apply { id = 55 }

        `when`(paymentRepository.findAllById(anyIterable())).thenReturn(payments)
        `when`(userRepository.getReferenceById(5)).thenReturn(responsible)
        `when`(paymentRepository.saveAll(payments)).thenReturn(payments)
        `when`(paymentRepository.findPendingPaymentsBySubscriptionId(10)).thenReturn(0)
        `when`(inboundMessageRepository.findById(55)).thenReturn(Optional.of(inbound))

        val result = service.registerPayments(
            MultiPaymentRegisterRequest(
                paymentIds = listOf(40, 41),
                method = "Plin",
                responsibleId = 5,
                inboundMessageId = 55,
            ),
        )

        assertTrue(result.all { it.proofImagePath == "batch-proof.jpg" })
        assertTrue(result.all { service.toPublicDto(it).proofImagePath == publicProofPath("batch-proof.jpg") })
    }

    @Test
    fun `registerPayment throws EntityNotFoundException when payment does not exist`() {
        `when`(paymentRepository.findById(999)).thenReturn(Optional.empty())

        assertThrows(EntityNotFoundException::class.java) {
            service.registerPayment(
                PaymentRequest(
                    id = 999,
                    method = "Yape",
                    responsibleId = 5,
                ),
            )
        }
    }

    @Test
    fun `createPaidPayment persists new paid payment for subscription`() {
        `when`(subscriptionRepository.findById(10)).thenReturn(Optional.of(subscription))
        `when`(userRepository.getReferenceById(5)).thenReturn(responsible)
        `when`(paymentRepository.save(any(Payment::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as Payment).apply { id = 100 }
        }
        `when`(paymentRepository.findPendingPaymentsBySubscriptionId(10)).thenReturn(0)

        val result = service.createPaidPayment(
            PaymentCreateRequest(
                subscriptionId = 10,
                amountPaid = 79.9,
                discountAmount = 0.0,
                discountReason = null,
                method = "Efectivo",
                electronicPayerName = null,
                paymentDate = null,
                billingDate = null,
                responsibleId = 5,
            ),
        )

        assertTrue(result.paid)
        assertEquals(79.9, result.amountPaid)
        assertEquals(79.9, result.amountToPay)
        assertEquals("Efectivo", result.method)
        assertNull(result.proofImagePath)
        verify(paymentRepository).save(any(Payment::class.java))
    }

    private fun unpaidPayment(id: Int, amountToPay: Double): Payment =
        Payment(
            discountAmount = 0.0,
            paid = false,
            amountToPay = amountToPay,
            billingDateDatetime = LocalDateTime.of(2026, 7, 1, 0, 0),
        ).apply {
            this.id = id
            this.subscription = this@PaymentServiceTest.subscription
        }
}
