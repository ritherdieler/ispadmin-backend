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

    fun findByClientRequestId(clientRequestId: String): Optional<Subscription>

    @Query(
        """
        SELECT s FROM Subscription s
        WHERE s.provisionNextAttemptAt IS NOT NULL
          AND s.provisionNextAttemptAt <= :now
          AND (
            s.mikrotikProvisionStatus = com.dscorp.wispadmin.wispadmin.data.model.MikrotikProvisionStatus.PENDING
            OR s.oltProvisionStatus = com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus.PENDING
            OR (
              s.tr069ProvisionStatus = com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus.PENDING
              AND s.oltProvisionStatus = com.dscorp.wispadmin.wispadmin.data.model.OltProvisionStatus.COMPLETE
            )
          )
        ORDER BY s.provisionNextAttemptAt ASC
        """
    )
    fun findDueForProvisionReconciliation(
        @Param("now") now: LocalDateTime,
        pageable: org.springframework.data.domain.Pageable
    ): List<Subscription>

    @Query(
        """
        SELECT DISTINCT s FROM Subscription s
        LEFT JOIN FETCH s.plan
        LEFT JOIN FETCH s.place
        LEFT JOIN FETCH s.napBox
        LEFT JOIN FETCH s.hostDevice
        LEFT JOIN FETCH s.technician
        LEFT JOIN FETCH s.fiberOnu
        LEFT JOIN FETCH s.ipPool
        WHERE s.id IN :ids
        """
    )
    fun findAllWithProvisionRelationsByIdIn(@Param("ids") ids: List<Int>): List<Subscription>

    @Query(
        """
        SELECT DISTINCT s FROM Subscription s
        LEFT JOIN FETCH s.plan
        LEFT JOIN FETCH s.place
        LEFT JOIN FETCH s.napBox nb
        LEFT JOIN FETCH nb.mufa
        LEFT JOIN FETCH s.hostDevice
        LEFT JOIN FETCH s.technician
        LEFT JOIN FETCH s.fiberOnu
        LEFT JOIN FETCH s.ipPool
        LEFT JOIN FETCH s.cpe
        LEFT JOIN FETCH s.coupon
        LEFT JOIN FETCH s.additionalDevices
        ORDER BY s.id
        """
    )
    fun findAllWithCoreRelations(): List<Subscription>

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

    @Query("SELECT s.ip FROM Subscription s WHERE s.serviceStatus = 'ACTIVE' AND s.ip IS NOT NULL AND s.ip <> ''")
    fun findActiveIps(): List<String>

    fun existsByIpAndServiceStatus(ip: String, serviceStatus: ServiceStatus): Boolean

    fun findByIpAndServiceStatus(ip: String, serviceStatus: ServiceStatus): List<Subscription>

    
    // Método para obtener las suscripciones canceladas
    @Query("SELECT s FROM Subscription s WHERE s.serviceStatus = 'CANCELLED'")
    fun findCancelledSubscriptions(): List<Subscription>

    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.serviceStatus = :serviceStatus")
    fun countByServiceStatus(serviceStatus: ServiceStatus): Long

    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.serviceStatus != 'CANCELLED'")
    fun countNonCancelledSubscriptions(): Long

    @Query(
        "SELECT COUNT(s) FROM Subscription s WHERE s.subscriptionDatetime >= :startDate AND s.subscriptionDatetime < :endDate"
    )
    fun countBySubscriptionDatetimeBetween(startDate: LocalDateTime, endDate: LocalDateTime): Long

    @Query(
        """
        SELECT s FROM Subscription s
        INNER JOIN FETCH s.plan
        WHERE s.serviceStatus != 'CANCELLED' AND s.plan IS NOT NULL
        """
    )
    fun findAllWithPlanForMassBilling(): List<Subscription>

    @Query(
        """
        SELECT s.id FROM Subscription s
        INNER JOIN s.plan
        WHERE s.serviceStatus != 'CANCELLED' AND s.plan IS NOT NULL
        """
    )
    fun findBillableSubscriptionIdsForMassBilling(): List<Int>

    @Query(
        """
        SELECT s FROM Subscription s
        INNER JOIN FETCH s.plan
        WHERE s.id = :id
        """
    )
    fun findWithPlanByIdForMassBilling(id: Int): Optional<Subscription>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE Subscription s SET
            s.serviceStatus = 'ACTIVE',
            s.isPaymentCommit = false,
            s.paymentCommitmentDateDatetime = null,
            s.isReactivation = false,
            s.reactivationDateDatetime = null
        WHERE s.id = :id
        """
    )
    fun applyMassBillingInvoiceSubscriptionState(id: Int)

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE Subscription s SET
            s.serviceStatus = 'CANCELLED',
            s.cancellationDateDatetime = :cancellationAt,
            s.isPaymentCommit = false,
            s.paymentCommitmentDateDatetime = null,
            s.isReactivation = false,
            s.reactivationDateDatetime = null
        WHERE s.id = :id
        """
    )
    fun applyMassBillingCancellation(id: Int, cancellationAt: LocalDateTime)

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

    @Query("""
        SELECT s FROM Subscription s
        WHERE REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(s.phone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '') = :normalizedPhone
        OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(s.phone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '') = CONCAT('51', :normalizedPhone)
    """)
    fun findByNormalizedPhone(normalizedPhone: String): List<Subscription>

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
        SELECT s FROM Subscription s
        WHERE s.serviceStatus = 'CANCELLED'
        AND s.fiberOnu IS NOT NULL
        AND (
            UPPER(s.fiberOnu.sn) = UPPER(:sn)
            OR UPPER(s.fiberOnu.sn) LIKE CONCAT('%', UPPER(:suffix))
        )
        """
    )
    fun findCancelledByFiberOnuSn(sn: String, suffix: String): List<Subscription>

    @Query(
        """
        SELECT s FROM Subscription s
        WHERE s.serviceStatus = 'ACTIVE'
        AND s.fiberOnu IS NOT NULL
        AND (
            UPPER(s.fiberOnu.sn) = UPPER(:sn)
            OR UPPER(s.fiberOnu.sn) LIKE CONCAT('%', UPPER(:suffix))
        )
        """
    )
    fun findActiveByFiberOnuSn(sn: String, suffix: String): List<Subscription>

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
          AND NOT (
            ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.latitude')) AS DECIMAL(12, 8)), 6) = -11.233708
            AND ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.longitude')) AS DECIMAL(12, 8)), 6) = -77.376278
          )
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
          AND NOT (
            ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.latitude')) AS DECIMAL(12, 8)), 6) = -11.233708
            AND ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.longitude')) AS DECIMAL(12, 8)), 6) = -77.376278
          )
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
          AND NOT (
            ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.latitude')) AS DECIMAL(12, 8)), 6) = -11.233708
            AND ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.longitude')) AS DECIMAL(12, 8)), 6) = -77.376278
          )
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
        value = """
        SELECT
            s.id,
            COALESCE(NULLIF(TRIM(s.first_name), ''), s.business_name, ''),
            COALESCE(s.last_name, ''),
            sector.name,
            SUM(p.amount_to_pay)
        FROM subscription s
        INNER JOIN payment p ON p.subscription_id = s.id
            AND p.paid = false
            AND p.billing_date_datetime >= :dateFrom
            AND p.billing_date_datetime < :dateToExclusive
        INNER JOIN place sector ON LOWER(TRIM(sector.name)) = LOWER(TRIM(:placeName))
            AND sector.area IS NOT NULL
        WHERE s.service_status = 'ACTIVE'
          AND s.location IS NOT NULL
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.latitude')) AS DECIMAL(12, 8)) != 0
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.longitude')) AS DECIMAL(12, 8)) != 0
          AND NOT (
            ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.latitude')) AS DECIMAL(12, 8)), 6) = -11.233708
            AND ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.longitude')) AS DECIMAL(12, 8)), 6) = -77.376278
          )
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
        GROUP BY s.id, s.first_name, s.last_name, s.business_name, sector.name
        HAVING SUM(p.amount_to_pay) > 0
        """,
        nativeQuery = true,
    )
    fun findSweepEligibleDebtorRowsInsidePlacePolygon(
        @Param("placeName") placeName: String,
        @Param("dateFrom") dateFrom: LocalDateTime,
        @Param("dateToExclusive") dateToExclusive: LocalDateTime,
    ): List<Array<Any>>

    @Query(
        value = """
        SELECT COUNT(*)
        FROM (
            SELECT s.id
            FROM subscription s
            INNER JOIN payment p ON p.subscription_id = s.id
                AND p.paid = false
                AND p.billing_date_datetime >= :dateFrom
                AND p.billing_date_datetime < :dateToExclusive
            INNER JOIN place sector ON LOWER(TRIM(sector.name)) = LOWER(TRIM(:placeName))
                AND sector.area IS NOT NULL
            WHERE s.service_status = 'ACTIVE'
              AND s.location IS NOT NULL
              AND ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.latitude')) AS DECIMAL(12, 8)), 6) = -11.233708
              AND ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.longitude')) AS DECIMAL(12, 8)), 6) = -77.376278
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
            GROUP BY s.id
            HAVING SUM(p.amount_to_pay) > 0
        ) omitted
        """,
        nativeQuery = true,
    )
    fun countSweepDefaultLocationDebtorsInsidePlacePolygon(
        @Param("placeName") placeName: String,
        @Param("dateFrom") dateFrom: LocalDateTime,
        @Param("dateToExclusive") dateToExclusive: LocalDateTime,
    ): Long

    @Query(
        value = """
        SELECT
            s.id,
            COALESCE(NULLIF(TRIM(s.first_name), ''), s.business_name, ''),
            COALESCE(s.last_name, ''),
            COALESCE(NULLIF(TRIM(pl.name), ''), 'Sin sector'),
            SUM(p.amount_to_pay)
        FROM subscription s
        INNER JOIN payment p ON p.subscription_id = s.id
            AND p.paid = false
            AND p.billing_date_datetime >= :dateFrom
            AND p.billing_date_datetime < :dateToExclusive
        LEFT JOIN place pl ON s.place_id = pl.id
        WHERE s.service_status = 'ACTIVE'
          AND s.location IS NOT NULL
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.latitude')) AS DECIMAL(12, 8)) != 0
          AND CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.longitude')) AS DECIMAL(12, 8)) != 0
          AND NOT (
            ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.latitude')) AS DECIMAL(12, 8)), 6) = -11.233708
            AND ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.longitude')) AS DECIMAL(12, 8)), 6) = -77.376278
          )
          AND ST_Contains(
                ST_GeomFromText(:selectionPolygonWkt, 4326),
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
        GROUP BY s.id, s.first_name, s.last_name, s.business_name, pl.name
        HAVING SUM(p.amount_to_pay) > 0
        """,
        nativeQuery = true,
    )
    fun findSweepEligibleDebtorRowsInsideSelectionPolygon(
        @Param("selectionPolygonWkt") selectionPolygonWkt: String,
        @Param("dateFrom") dateFrom: LocalDateTime,
        @Param("dateToExclusive") dateToExclusive: LocalDateTime,
    ): List<Array<Any>>

    @Query(
        value = """
        SELECT COUNT(*)
        FROM (
            SELECT s.id
            FROM subscription s
            INNER JOIN payment p ON p.subscription_id = s.id
                AND p.paid = false
                AND p.billing_date_datetime >= :dateFrom
                AND p.billing_date_datetime < :dateToExclusive
            LEFT JOIN place pl ON s.place_id = pl.id
            WHERE s.service_status = 'ACTIVE'
              AND s.location IS NOT NULL
              AND ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.latitude')) AS DECIMAL(12, 8)), 6) = -11.233708
              AND ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.longitude')) AS DECIMAL(12, 8)), 6) = -77.376278
              AND ST_Contains(
                    ST_GeomFromText(:selectionPolygonWkt, 4326),
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
            GROUP BY s.id
            HAVING SUM(p.amount_to_pay) > 0
        ) omitted
        """,
        nativeQuery = true,
    )
    fun countSweepDefaultLocationDebtorsInsideSelectionPolygon(
        @Param("selectionPolygonWkt") selectionPolygonWkt: String,
        @Param("dateFrom") dateFrom: LocalDateTime,
        @Param("dateToExclusive") dateToExclusive: LocalDateTime,
    ): Long

    @Query(
        value = """
        SELECT s.id, pl.name
        FROM subscription s
        INNER JOIN payment p ON p.subscription_id = s.id
            AND p.paid = false
            AND p.billing_date_datetime >= :dateFrom
            AND p.billing_date_datetime < :dateToExclusive
        INNER JOIN place pl ON s.place_id = pl.id
        WHERE s.service_status = 'ACTIVE'
          AND s.location IS NOT NULL
          AND ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.latitude')) AS DECIMAL(12, 8)), 6) = -11.233708
          AND ROUND(CAST(JSON_UNQUOTE(JSON_EXTRACT(s.location, '$.longitude')) AS DECIMAL(12, 8)), 6) = -77.376278
          AND TRIM(pl.name) != ''
        GROUP BY s.id, pl.name
        HAVING SUM(p.amount_to_pay) > 0
        """,
        nativeQuery = true,
    )
    fun findSweepDefaultLocationDebtorIdsWithPlace(
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

    @Query(
        value = """
        SELECT s.*
        FROM subscription s
        WHERE s.is_service_cut_off = true
          AND s.phone IS NOT NULL
          AND s.phone <> ''
          AND (
              (
                  CHAR_LENGTH(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(s.phone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '')) = 9
                  AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(s.phone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '') LIKE '9%'
              )
              OR
              (
                  CHAR_LENGTH(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(s.phone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '')) = 11
                  AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(s.phone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '') LIKE '519%'
              )
          )
        ORDER BY s.id ASC
        LIMIT :limit
    """,
        nativeQuery = true
    )
    fun findCutOffCandidates(@Param("limit") limit: Int): List<Subscription>

    @Query(
        value = """
        SELECT
            s.id AS subscription_id,
            s.first_name,
            s.last_name,
            s.phone,
            s.subscription_date_datetime
        FROM subscription s
        WHERE s.service_status = 'ACTIVE'
          AND s.subscription_date_datetime IS NOT NULL
          AND s.subscription_date_datetime >= :since
        """ + WhatsAppCandidateSql.PERUVIAN_PHONE_FILTER + """
        ORDER BY s.subscription_date_datetime DESC, s.id DESC
        LIMIT :limit
    """,
        nativeQuery = true
    )
    fun findWelcomeCandidateRows(
        @Param("since") since: LocalDateTime,
        @Param("limit") limit: Int
    ): List<Array<Any>>

    @Query(
        value = """
        SELECT
            s.id AS subscription_id,
            s.first_name AS first_name,
            s.last_name AS last_name,
            s.phone AS phone,
            s.service_status AS service_status,
            s.installation_type AS installation_type,
            s.price AS subscription_price,
            s.subscription_date_datetime AS subscription_date_datetime,
            p.name AS plan_name,
            p.price AS plan_price,
            p.download_speed AS download_speed,
            p.upload_speed AS upload_speed
        FROM subscription s
        LEFT JOIN plan p ON p.id = s.plan_id
        WHERE s.id = :subscriptionId
        """,
        nativeQuery = true
    )
    fun findWhatsAppSubscriptionRowById(@Param("subscriptionId") subscriptionId: Int): List<Array<Any>>

    @Query(
        """
        SELECT s.id AS id, s.firstName AS firstName, s.lastName AS lastName
        FROM Subscription s
        WHERE s.id IN :ids
        """
    )
    fun findNameProjectionsByIdIn(@Param("ids") ids: Collection<Int>): List<SubscriptionNameProjection>
}

interface SubscriptionNameProjection {
    fun getId(): Int
    fun getFirstName(): String?
    fun getLastName(): String?
}
