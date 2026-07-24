package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.config.BorneManagementProperties
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.NapBox
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.NapBoxRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalDateTime
import java.util.Optional

class BorneManagementServiceTest {

    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var napBoxRepository: NapBoxRepository
    private lateinit var properties: BorneManagementProperties
    private lateinit var service: BorneManagementService

    private val napBoxId = 1
    private val napBox = NapBox(id = napBoxId, code = "NAP-001")
    private val fullNapOccupiedBornes = (1..16).map { it.toString() }

    @BeforeEach
    fun setUp() {
        subscriptionRepository = mock(SubscriptionRepository::class.java)
        napBoxRepository = mock(NapBoxRepository::class.java)
        properties = BorneManagementProperties()
        service = BorneManagementService(subscriptionRepository, napBoxRepository, properties)
    }

    @Test
    fun `validateAndAssignBorne throws when capacity check enabled and nap is full`() {
        properties.capacityCheckEnabled = true
        mockFullActiveNap()

        val subscription = fiberSubscription()

        assertThrows(NoAvailableBornesException::class.java) {
            service.validateAndAssignBorne(subscription, null)
        }
    }

    @Test
    fun `validateAndAssignBorne returns null when capacity check disabled and nap is full`() {
        properties.capacityCheckEnabled = false
        mockFullActiveNap()

        val subscription = fiberSubscription()

        val result = service.validateAndAssignBorne(subscription, null)

        assertNull(result)
    }

    @Test
    fun `validateAndAssignBorne rejects duplicate borne when capacity check enabled`() {
        properties.capacityCheckEnabled = true
        mockDbBorneConflict("5")
        `when`(napBoxRepository.findById(napBoxId)).thenReturn(Optional.of(napBox))

        val subscription = fiberSubscription()

        assertThrows(BorneConstraintViolationException::class.java) {
            service.validateAndAssignBorne(subscription, "5")
        }
    }

    @Test
    fun `validateAndAssignBorne returns null on db duplicate when capacity check disabled`() {
        properties.capacityCheckEnabled = false
        mockDbBorneConflict("5")

        val subscription = fiberSubscription()

        val result = service.validateAndAssignBorne(subscription, "5")

        assertNull(result)
    }

    @Test
    fun `validateAndAssignBorne skips db occupied borne on auto assign`() {
        properties.capacityCheckEnabled = true
        `when`(subscriptionRepository.findBorneNumbersByNapBoxId(napBoxId)).thenReturn(listOf("3"))
        `when`(
            subscriptionRepository.findBorneNumbersByNapBoxIdAndServiceStatus(
                napBoxId,
                ServiceStatus.ACTIVE
            )
        ).thenReturn(emptyList())
        `when`(
            subscriptionRepository.existsByNapBoxIdAndBorneNumberExcludingSubscriptionId(
                napBoxId,
                "1",
                null
            )
        ).thenReturn(false)

        val subscription = fiberSubscription()

        val result = service.validateAndAssignBorne(subscription, null)

        assertEquals("1", result)
    }

    @Test
    fun `validateReactivationBorne returns Valid with original borne when capacity check disabled and nap is full`() {
        properties.capacityCheckEnabled = false
        `when`(
            subscriptionRepository.existsByNapBoxIdAndBorneNumberAndServiceStatus(
                napBoxId,
                "3",
                ServiceStatus.ACTIVE
            )
        ).thenReturn(true)
        `when`(
            subscriptionRepository.findBorneNumbersByNapBoxId(napBoxId)
        ).thenReturn(fullNapOccupiedBornes)
        `when`(
            subscriptionRepository.findBorneNumbersByNapBoxIdAndServiceStatus(
                napBoxId,
                ServiceStatus.ACTIVE
            )
        ).thenReturn(fullNapOccupiedBornes)

        val subscription = fiberSubscription(borneNumber = "3")

        val result = service.validateReactivationBorne(subscription)

        assertEquals(BorneValidationResult.Valid("3"), result)
    }

    @Test
    fun `validateReactivationBorne returns NoBornesAvailable when capacity check enabled and nap is full`() {
        properties.capacityCheckEnabled = true
        `when`(
            subscriptionRepository.existsByNapBoxIdAndBorneNumberAndServiceStatus(
                napBoxId,
                "3",
                ServiceStatus.ACTIVE
            )
        ).thenReturn(true)
        `when`(
            subscriptionRepository.findBorneNumbersByNapBoxId(napBoxId)
        ).thenReturn(fullNapOccupiedBornes)
        `when`(
            subscriptionRepository.findBorneNumbersByNapBoxIdAndServiceStatus(
                napBoxId,
                ServiceStatus.ACTIVE
            )
        ).thenReturn(fullNapOccupiedBornes)

        val subscription = fiberSubscription(borneNumber = "3")

        val result = service.validateReactivationBorne(subscription)

        assertInstanceOf(BorneValidationResult.NoBornesAvailable::class.java, result)
        val noBornes = result as BorneValidationResult.NoBornesAvailable
        assertEquals(napBoxId, noBornes.napBoxId)
        assertEquals(napBox.code, noBornes.napBoxCode)
    }

    private fun mockFullActiveNap() {
        `when`(
            subscriptionRepository.findBorneNumbersByNapBoxIdAndServiceStatus(
                napBoxId,
                ServiceStatus.ACTIVE
            )
        ).thenReturn(fullNapOccupiedBornes)
        `when`(subscriptionRepository.findBorneNumbersByNapBoxId(napBoxId)).thenReturn(fullNapOccupiedBornes)
    }

    private fun mockDbBorneConflict(borneNumber: String) {
        `when`(
            subscriptionRepository.existsByNapBoxIdAndBorneNumberExcludingSubscriptionId(
                napBoxId,
                borneNumber,
                null
            )
        ).thenReturn(true)
    }

    private fun fiberSubscription(borneNumber: String? = null): Subscription {
        return Subscription(
            firstName = "Juan",
            lastName = "Perez",
            installationType = InstallationType.FIBER,
            napBox = napBox,
            borneNumber = borneNumber,
            subscriptionDatetime = LocalDateTime.of(2025, 9, 1, 0, 0),
            equipmentCondition = EquipmentCondition.LOAN
        )
    }
}
