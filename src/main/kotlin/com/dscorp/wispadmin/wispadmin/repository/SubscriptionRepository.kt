package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.*
import java.time.LocalDateTime

interface SubscriptionRepository : JpaRepository<Subscription, Int> {
    @Query("SELECT distinct s FROM Subscription s inner join  s.payments p WHERE p.paid = false ")
    fun getDebtors(): List<Subscription>

    @Query("SELECT distinct s, SUM(p.amountToPay) FROM Subscription s inner join  s.payments p WHERE p.paid = false and s.serviceStatus!='CANCELLED'  group by s.id")
    fun getDebtorsWithActiveSubscriptionReport(): List<Array<Any>>

    @Query("SELECT distinct s, SUM(p.amountToPay) FROM Subscription s inner join  s.payments p WHERE p.paid = false and s.serviceStatus ='CANCELLED'  group by s.id")
    fun getDebtorsWithCancelledServiceReport(): List<Array<Any>>

    @Query("SELECT distinct s, SUM(p.amountToPay) FROM Subscription s inner join  s.payments p WHERE p.paid = false and s.isPaymentCommit=false  AND s.serviceStatus != 'CANCELLED' group by s.id")
    fun getDebtorsWithCut(): List<Array<Any>>

    @Query("SELECT DISTINCT s FROM Subscription s INNER JOIN s.payments p WHERE p.paid = false AND s.autoCut=true and s.serviceStatus != 'CANCELLED' group by s.id")
    fun findSubscriptionsWithUnpaidAndAutoCutFlagActivePayments(): List<Subscription>

    @Query("SELECT DISTINCT s FROM Subscription s INNER JOIN s.payments p WHERE p.paid = false AND s.autoCut=false and s.serviceStatus != 'CANCELLED' group by s.id")
    fun findSubscriptionsWithUnpaidAndAutoCutFlagInactivePayments(): List<Subscription>

    @Query(
        "SELECT s FROM Subscription s " +
                "INNER JOIN s.payments p " +
                "WHERE p.paid = false " +
                "GROUP BY s.id " +
                "HAVING COUNT(p.id) >= :numberOfPayments " +
                "AND s.autoCut = true " +
                "AND s.serviceStatus != 'CANCELLED'"
    )
    fun findSubscriptionsWithMoreThanXUnpaidBills(numberOfPayments: Int): List<Subscription>


    @Query("SELECT s FROM Subscription s WHERE s.serviceStatus = :serviceStatus ")
    fun findByServiceStatus(serviceStatus: ServiceStatus): List<Subscription>

    // Método para obtener las suscripciones activas
    @Query("SELECT s FROM Subscription s WHERE s.serviceStatus = 'ACTIVE'")
    fun findActiveSubscriptions(): List<Subscription>
    
    // Método para obtener las suscripciones canceladas
    @Query("SELECT s FROM Subscription s WHERE s.serviceStatus = 'CANCELLED'")
    fun findCancelledSubscriptions(): List<Subscription>

    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.serviceStatus = :serviceStatus")
    fun countByServiceStatus(serviceStatus: ServiceStatus): Long

    //get debtors from last month
    @Query("SELECT distinct s FROM Subscription s inner join s.payments p WHERE p.paid = false AND p.billingDateDatetime >= ?1 AND p.billingDateDatetime <= ?2")
    fun getDebtorsFromLastMonth(startDate: LocalDateTime, endDate: LocalDateTime): List<Subscription>

    @Query("SELECT distinct s FROM Subscription s inner join s.payments p WHERE p.paid = false AND p.billingDateDatetime >= ?1 AND p.billingDateDatetime <= ?2")
    fun getCancelledServicesFromDateInterval(startDate: LocalDateTime, endDate: LocalDateTime): List<Subscription>

    @Query("SELECT distinct s FROM Subscription s WHERE s.isPaymentCommit = true")
    fun getWithPaymentCommitment(): List<Subscription>

    @Query("SELECT s FROM Subscription s WHERE LOWER(s.dni) LIKE LOWER(CONCAT('%', :dni, '%'))")
    fun findByDni(dni: String): List<Subscription>

    //find By First Name Containing Ignore Case Or LastName Containing IgnoreCase and ServiceStatus != 'CANCELED'

    @Query("SELECT s FROM Subscription s WHERE LOWER(s.firstName) LIKE LOWER(CONCAT('%', :firstName, '%')) OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', :lastName, '%')) ")
    fun findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseAndServiceStatusNot(
        firstName: String,
        lastName: String
    ): List<Subscription>

    @Query("SELECT s FROM Subscription s WHERE LOWER(s.lastName) LIKE LOWER(CONCAT('%', :lastName, '%')) ")
    fun findByLastNameContainingIgnoreCase(lastName: String): List<Subscription>

    @Query("SELECT s FROM Subscription s WHERE LOWER(s.firstName) LIKE LOWER(CONCAT('%', :firstName, '%')) ")
    fun findByFirstNameContainingIgnoreCase(firstName: String): List<Subscription>

    // Busca suscripciones por IP (devuelve máximo 20 resultados)
    @Query("SELECT s FROM Subscription s WHERE LOWER(s.ip) LIKE LOWER(CONCAT('%', :ip, '%')) ORDER BY s.id ASC")
    fun findTop20ByIpContainingIgnoreCase(ip: String): List<Subscription>

    @Query(
        """
    SELECT s FROM Subscription s
    WHERE s.subscriptionDatetime >= :startDate
      AND s.subscriptionDatetime <= :endDate
    """
    )
    fun findBySubscriptionDateGreaterThanEqualAndSubscriptionDateLessThanEqual(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Subscription>

    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.cancellationDateDatetime >= ?1 AND s.cancellationDateDatetime <= ?2")
    fun findQuantityByCancellationDate(startDate: LocalDateTime, endDate: LocalDateTime): Int

    @Query("SELECT s FROM Subscription s WHERE s.cancellationDateDatetime >= ?1 AND s.cancellationDateDatetime <= ?2")
    fun findSubscriptionsByCancellationDate(startDate: LocalDateTime, endDate: LocalDateTime): List<Subscription>

    fun findByPlanId(planId: Int): List<Subscription>

    @Modifying
    @Query(
        "UPDATE Subscription s SET " +
                "s.isPaymentCommit = :isPaymentCommitment, " +
                "s.paymentCommitmentDateDatetime = :paymentCommitmentDateDatetime, " +
                "s.serviceStatus = 'ACTIVE' " +
                "WHERE s.id = :id"
    )
    fun updatePaymentCommitment(
        isPaymentCommitment: Boolean,
        paymentCommitmentDateDatetime: LocalDateTime,
        id: Int
    )
    @Modifying
    @Query(
        "UPDATE Subscription s SET " +
                "s.isReactivation = :isReactivation, " +
                "s.reactivationDateDatetime = :reactivationDateDatetime, " +
                "s.serviceStatus = 'ACTIVE', " +
                "s.isPaymentCommit = false, " +
                "s.paymentCommitmentDateDatetime = null " +
                "WHERE s.id = :id"
    )
    fun reactivateService(
        isReactivation: Boolean,
        reactivationDateDatetime: LocalDateTime,
        id: Int
    )

    @Modifying
    @Query(
        "UPDATE Subscription s SET " +
                "s.cancellationDateDatetime = :cancellationDateDatetime, " +
                "s.serviceStatus = 'CANCELLED' " +
                "WHERE s.id = :idSubscription"
    )
    fun cancelService(
        idSubscription: Int,
        cancellationDateDatetime: LocalDateTime
    )

    @Query("SELECT s FROM Subscription s WHERE s.dni = :dni AND s.password = :password")
    fun logIn(dni: String, password: String): Subscription?


    @Query(
        "SELECT s FROM Subscription s WHERE " +
                "LOWER(CONCAT(s.lastName, ' ', s.firstName)) LIKE LOWER(CONCAT('%', :name, '%')) " +
                "OR LOWER(CONCAT(s.firstName, ' ', s.lastName)) LIKE LOWER(CONCAT('%', :name, '%')) " +
                "OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', :name, '%')) " +
                "OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', :name, '%'))"
    )
    fun searchByNameOrLastName(name: String): List<Subscription>


    @Query("SELECT DISTINCT p FROM Subscription s JOIN s.payments p WHERE p.electronicPayerName LIKE %:electronicPayerName%")
    fun findByElectronicPayerName(electronicPayerName: String): List<Payment>




    // ========== NUEVOS MÉTODOS PARA GESTIÓN DE BORNES ==========
    
    /**
     * Buscar suscripciones activas por NAP Box
     */
    @Query("SELECT s FROM Subscription s WHERE s.napBox.id = :napBoxId AND s.serviceStatus = :status")
    fun findByNapBoxIdAndServiceStatus(napBoxId: Int, status: ServiceStatus): List<Subscription>
    
    /**
     * Verificar si un borne específico está ocupado
     */
    @Query("SELECT COUNT(s) > 0 FROM Subscription s WHERE s.napBox.id = :napBoxId AND s.borneNumber = :borneNumber AND s.serviceStatus = :status")
    fun existsByNapBoxIdAndBorneNumberAndServiceStatus(
        napBoxId: Int, 
        borneNumber: String, 
        status: ServiceStatus
    ): Boolean
    
    /**
     * Obtener bornes ocupados de una NAP Box
     */
    @Query("SELECT s.borneNumber FROM Subscription s WHERE s.napBox.id = :napBoxId AND s.serviceStatus = :status AND s.borneNumber IS NOT NULL")
    fun findBorneNumbersByNapBoxIdAndServiceStatus(
        napBoxId: Int, 
        status: ServiceStatus
    ): List<String>

}