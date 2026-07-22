package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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
    @Query("SELECT distinct s FROM Subscription s inner join s.payments p WHERE p.paid = false AND p.billingDateDatetime >= ?1 AND p.billingDateDatetime < ?2")
    fun getDebtorsFromLastMonth(startDate: LocalDateTime, endDate: LocalDateTime): List<Subscription>

    @Query("SELECT distinct s FROM Subscription s inner join s.payments p WHERE p.paid = false AND p.billingDateDatetime >= ?1 AND p.billingDateDatetime < ?2")
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

    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.cancellationDateDatetime >= ?1 AND s.cancellationDateDatetime < ?2")
    fun findQuantityByCancellationDate(startDate: LocalDateTime, endDate: LocalDateTime): Int

    @Query("SELECT s FROM Subscription s WHERE s.cancellationDateDatetime >= ?1 AND s.cancellationDateDatetime < ?2")
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

    @Query("SELECT s.borneNumber FROM Subscription s WHERE s.napBox.id = :napBoxId AND s.borneNumber IS NOT NULL")
    fun findBorneNumbersByNapBoxId(napBoxId: Int): List<String>

    @Query(
        """
        SELECT COUNT(s) > 0 FROM Subscription s
        WHERE s.napBox.id = :napBoxId AND s.borneNumber = :borneNumber
        AND (:excludeSubscriptionId IS NULL OR s.id <> :excludeSubscriptionId)
        """
    )
    fun existsByNapBoxIdAndBorneNumberExcludingSubscriptionId(
        napBoxId: Int,
        borneNumber: String,
        excludeSubscriptionId: Int?
    ): Boolean

    @Query(
        """
        SELECT DISTINCT s FROM Subscription s
        INNER JOIN FETCH s.payments p
        INNER JOIN FETCH s.place pl
        LEFT JOIN FETCH s.plan
        WHERE p.paid = false
        AND s.serviceStatus = 'ACTIVE'
        AND LOWER(TRIM(pl.name)) = LOWER(TRIM(:placeName))
        """
    )
    fun findDebtorsByPlaceName(@Param("placeName") placeName: String): List<Subscription>

    @Query(
        """
        SELECT DISTINCT s FROM Subscription s
        INNER JOIN FETCH s.payments p
        LEFT JOIN FETCH s.place pl
        LEFT JOIN FETCH s.plan
        WHERE p.paid = false
        AND s.serviceStatus = 'ACTIVE'
        """
    )
    fun findAllDebtors(): List<Subscription>

    @Query(
        value = """
        SELECT DISTINCT s.id
        FROM subscription s
        INNER JOIN place pl ON s.place_id = pl.id
        INNER JOIN payment p ON p.subscription_id = s.id AND p.paid = false
        INNER JOIN place sector ON LOWER(TRIM(sector.name)) = LOWER(TRIM(:placeName)) AND sector.area IS NOT NULL
        WHERE LOWER(TRIM(pl.name)) = LOWER(TRIM(:placeName))
          AND s.service_status = 'ACTIVE'
          AND s.location IS NOT NULL
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.latitude')) AS DECIMAL(12, 8)) != 0
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.longitude')) AS DECIMAL(12, 8)) != 0
          AND ST_Contains(
                ST_SRID(sector.area, 4326),
                ST_GeomFromText(
                    CONCAT(
                        'POINT(',
                        JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.longitude')),
                        ' ',
                        JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.latitude')),
                        ')'
                    ),
                    4326
                )
          )
        """,
        nativeQuery = true,
    )
    fun findDebtorIdsInsidePlacePolygon(@Param("placeName") placeName: String): List<Int>

    @Query(
        value = """
        SELECT DISTINCT s.id
        FROM subscription s
        INNER JOIN place pl ON s.place_id = pl.id
        INNER JOIN place sector ON LOWER(TRIM(sector.name)) = LOWER(TRIM(pl.name)) AND sector.area IS NOT NULL
        INNER JOIN payment p ON p.subscription_id = s.id AND p.paid = false
        WHERE s.service_status = 'ACTIVE'
          AND s.location IS NOT NULL
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.latitude')) AS DECIMAL(12, 8)) != 0
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.longitude')) AS DECIMAL(12, 8)) != 0
          AND ST_Contains(
                ST_SRID(sector.area, 4326),
                ST_GeomFromText(
                    CONCAT(
                        'POINT(',
                        JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.longitude')),
                        ' ',
                        JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.latitude')),
                        ')'
                    ),
                    4326
                )
          )
        """,
        nativeQuery = true,
    )
    fun findAllDebtorIdsInsidePlacePolygons(): List<Int>

    @Query(
        value = """
        SELECT
            s.id,
            COALESCE(NULLIF(TRIM(s.first_name), ''), s.business_name, ''),
            COALESCE(s.last_name, ''),
            pl.name,
            SUM(p.amount_to_pay)
        FROM subscription s
        INNER JOIN payment p ON p.subscription_id = s.id
            AND p.paid = false
            AND p.billing_date_datetime >= :dateFrom
            AND p.billing_date_datetime < :dateToExclusive
        INNER JOIN place pl ON s.place_id = pl.id
        WHERE s.service_status = 'ACTIVE'
          AND s.location IS NOT NULL
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.latitude')) AS DECIMAL(12, 8)) != 0
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.longitude')) AS DECIMAL(12, 8)) != 0
          AND TRIM(pl.name) != ''
        GROUP BY s.id, s.first_name, s.last_name, s.business_name, pl.name
        HAVING SUM(p.amount_to_pay) > 0
        """,
        nativeQuery = true,
    )
    fun findSweepEligibleDebtorRows(
        @Param("dateFrom") dateFrom: LocalDateTime,
        @Param("dateToExclusive") dateToExclusive: LocalDateTime,
    ): List<Array<Any>>

    @Query(
        """
        SELECT DISTINCT s FROM Subscription s
        INNER JOIN FETCH s.payments p
        LEFT JOIN FETCH s.place pl
        LEFT JOIN FETCH s.plan
        WHERE s.id IN :ids
          AND p.paid = false
          AND s.serviceStatus = 'ACTIVE'
        """
    )
    fun findDebtorsByIds(@Param("ids") ids: Collection<Int>): List<Subscription>

    @Query(
        """
        SELECT DISTINCT s FROM Subscription s
        LEFT JOIN FETCH s.place
        LEFT JOIN FETCH s.plan
        LEFT JOIN FETCH s.payments
        """
    )
    fun findAllWithRelationsForSmartMap(): List<Subscription>

}
