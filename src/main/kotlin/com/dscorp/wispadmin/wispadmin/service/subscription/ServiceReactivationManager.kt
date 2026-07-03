package com.dscorp.wispadmin.wispadmin.service.subscription

import com.dscorp.wispadmin.wispadmin.controller.toErrorLog
import com.dscorp.wispadmin.wispadmin.data.model.*
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.*
import com.dscorp.wispadmin.wispadmin.service.BorneManagementService
import com.dscorp.wispadmin.wispadmin.service.BorneValidationResult
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IMikroTikService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IQueueManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*
import kotlin.math.roundToInt

@Service
class ServiceReactivationManager(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val paymentRepository: PaymentRepository,
    private val subscriptionReconnectionRepository: SubscriptionReconnectionRepository,
    private val subscriptionLogRepository: SubscriptionLogRepository,
    private val borneManagementService: BorneManagementService,
    private val mikrotikService: IMikroTikService,
    private val queueManager: IQueueManager,
    private val errorLogRepository: ErrorLogRepository
) : IServiceReactivationManager {
    
    private val logger = LoggerFactory.getLogger(ServiceReactivationManager::class.java)
    
    @Transactional
    override fun reactivateService(
        subscriptionId: Int,
        responsibleId: Int,
        notes: String?,
        newBorneNumber: String?
    ) {
        val subscription = subscriptionRepository.findById(subscriptionId).get()
        val responsible = userRepository.findById(responsibleId).get()

        val pendingPayments = paymentRepository.findPendingPaymentsBySubscriptionId(subscriptionId)

        if (pendingPayments > 1) throw Exception("No se puede reactivar el servicio, el cliente tiene $pendingPayments pagos pendientes.")

        val borneValidation = borneManagementService.validateReactivationBorne(subscription)

        when (borneValidation) {
            is BorneValidationResult.Valid -> {
                proceedWithReactivation(subscription, responsible, notes)
            }

            is BorneValidationResult.BorneReassigned -> {
                val newBorne = borneManagementService.assignNewBorneForReactivation(
                    subscription,
                    newBorneNumber
                )

                proceedWithReactivation(subscription, responsible, notes)

                logger.info("Suscripción ${subscription.id} reactivada con nuevo borne $newBorne (original: ${borneValidation.originalBorne})")
            }

            is BorneValidationResult.NoBornesAvailable -> {
                throw Exception(borneValidation.message)
            }
        }
    }
    
    private fun proceedWithReactivation(
        subscription: Subscription,
        responsible: User,
        notes: String?
    ) {
        createBill(subscription, responsible.id)

        subscriptionRepository.reactivateService(
            isReactivation = true,
            reactivationDateDatetime = LocalDateTime.now(),
            id = subscription.id!!
        )

        val reconnection = SubscriptionReconnection(
            subscription = subscription,
            responsible = responsible,
            reconnectionDate = LocalDateTime.now(),
            notes = notes
        )
        subscriptionReconnectionRepository.save(reconnection)

        subscriptionLogRepository.save(
            SubscriptionLog(
                subscription = subscription,
                actionType = SubscriptionActionType.RECONNECT_CANCELLED_SUBSCRIPTION,
                planName = subscription.plan?.name,
                planPrice = subscription.plan?.price ?: 0.0,
                planId = subscription.plan?.id
            )
        )

        if (subscription.installationType!!.isInternetUser()) {
            subscription.hostDevice?.let {
                it.executeCommand { connection ->
                    try {
                        val success = queueManager.recreateQueueForSubscription(connection, subscription)
                        if (success) {
                            logger.info("Queue recreada exitosamente para suscripción ${subscription.id}")
                        } else {
                            logger.warn("⚠️ Suscripción ${subscription.id} sin IP válida para crear queue")
                        }
                    } catch (e: Exception) {
                        logger.error("Error creando queue para suscripción ${subscription.id}: ${e.message}")
                        errorLogRepository.save(e.toErrorLog(Modules.SUBSCRIPTION))
                    }

                    mikrotikService.removeIpFromDebtorsList(connection, subscription.ip!!)
                }
            }
        } else {
            println("No se procesa queue ni address list porque no es usuario de internet\nes del tipo ${subscription.installationType?.name}")
        }
    }
    
    private fun createBill(subscription: Subscription, responsibleId: Int) {
        val lastMonthBillingDate = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.toInstant()
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()

        val existAnotherPaymentWithSameDate =
            paymentRepository.existsByBillingDateDatetimeAndSubscriptionId(lastMonthBillingDate, subscription.id!!)

        if (existAnotherPaymentWithSameDate)
            return

        val planPrinceByDay: Double = subscription.plan!!.price!! / 30

        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

        val remainingDays = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH) - currentDay

        val amountToPay = (planPrinceByDay * remainingDays).roundToInt()

        val discountAmount = subscription.plan!!.price!! - amountToPay

        val responsible = userRepository.findById(responsibleId).get()

        val payment = Payment(
            discountAmount = discountAmount,
            discountReason = "Descuento por reactivación de servicio",
            billingDateDatetime = lastMonthBillingDate,
            paid = false,
            amountToPay = subscription.plan!!.price!!.toDouble(),
            amountPaid = amountToPay.toDouble(),
            subscription = subscription,
            responsible = responsible
        )

        paymentRepository.save(payment)
    }
    
    @Transactional
    override fun registerPaymentCommitment(subscriptionId: Int) {
        val currentDateCalendar = Calendar.getInstance()
        when {
            isLastDayOfMonth(currentDateCalendar) -> throw Exception("No se puede registrar un compromiso de pago el último día del mes")
            else -> savePaymentCommitment(currentDateCalendar, subscriptionId)
        }
    }

    private fun savePaymentCommitment(currentDateCalendar: Calendar, subscriptionId: Int) {
        val subscription = subscriptionRepository.findById(subscriptionId).get()

        val paymentCommitmentDate = currentDateCalendar.toInstant()
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()

        subscriptionRepository.updatePaymentCommitment(
            isPaymentCommitment = true,
            paymentCommitmentDateDatetime = paymentCommitmentDate,
            id = subscriptionId
        )

        subscription.hostDevice?.let {
            it.executeCommand { connection ->
                mikrotikService.removeIpFromDebtorsList(connection, subscription.ip!!)
            }
        }
    }
    
    private fun isLastDayOfMonth(date: Calendar): Boolean {
        return date.get(Calendar.DAY_OF_MONTH) == date.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
}



