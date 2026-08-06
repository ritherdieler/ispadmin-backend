package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime
import org.springframework.data.repository.query.Param

interface PaymentRepository : JpaRepository<Payment, Int> {
    @Query("select p from Payment p where p.subscription.id = :subscriptionId and p.billingDateDatetime between :startDate and :endDate")
    fun findBySubscriptionFiltered(
        subscriptionId: Int,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Payment>

    // Facturas pendientes aptas para recordatorio manual desde backoffice.
    @Query(
        value = """
        SELECT p.*
        FROM payment p
        """ + WhatsAppCandidateSql.OLDEST_UNPAID_PAYMENT_PER_SUBSCRIPTION_JOIN + """
        INNER JOIN subscription s ON s.id = p.subscription_id
        WHERE 1=1
        """ + WhatsAppCandidateSql.PERUVIAN_PHONE_FILTER + """
        ORDER BY p.billing_date_datetime ASC, p.id ASC
        LIMIT :limit
    """,
        nativeQuery = true
    )
    fun findReminderCandidatePayments(
        @Param("limit") limit: Int
    ): List<Payment>

    @Query(
        value = """
        SELECT
            p.id AS payment_id,
            s.id AS subscription_id,
            s.first_name,
            s.last_name,
            s.phone,
            p.amount_to_pay,
            p.amount_paid,
            p.billing_date_datetime,
            p.payment_date_datetime
        FROM payment p
        """ + WhatsAppCandidateSql.OLDEST_UNPAID_PAYMENT_PER_SUBSCRIPTION_JOIN + """
        INNER JOIN subscription s ON s.id = p.subscription_id
        ORDER BY p.billing_date_datetime ASC, p.id ASC
        LIMIT :limit
    """,
        nativeQuery = true
    )
    fun findReminderCandidatePaymentRows(
        @Param("limit") limit: Int
    ): List<Array<Any>>

    @Query(
        value = """
        SELECT
            p.id AS payment_id,
            s.id AS subscription_id,
            s.first_name,
            s.last_name,
            s.phone,
            p.amount_to_pay,
            p.amount_paid,
            p.billing_date_datetime,
            p.payment_date_datetime
        FROM payment p
        INNER JOIN subscription s ON s.id = p.subscription_id
        WHERE p.paid = true
          AND p.payment_date_datetime IS NOT NULL
          AND p.payment_date_datetime >= :since
        """ + WhatsAppCandidateSql.PERUVIAN_PHONE_FILTER + """
        ORDER BY p.payment_date_datetime DESC, p.id DESC
        LIMIT :limit
    """,
        nativeQuery = true
    )
    fun findValidationCandidatePaymentRows(
        @Param("since") since: LocalDateTime,
        @Param("limit") limit: Int
    ): List<Array<Any>>

    @Query(
        value = """
        SELECT
            p.id AS payment_id,
            s.id AS subscription_id,
            s.first_name,
            s.last_name,
            s.phone,
            p.amount_to_pay,
            p.amount_paid,
            p.billing_date_datetime,
            p.payment_date_datetime
        FROM payment p
        INNER JOIN subscription s ON s.id = p.subscription_id
        WHERE p.paid = true
          AND p.payment_date_datetime IS NOT NULL
          AND p.payment_date_datetime >= :since
        ORDER BY p.payment_date_datetime DESC, p.id DESC
    """,
        nativeQuery = true
    )
    fun findAllValidationCandidatePaymentRows(
        @Param("since") since: LocalDateTime
    ): List<Array<Any>>

    @Query(
        """
        SELECT p FROM Payment p
        WHERE p.subscription.id = :subscriptionId
          AND p.paid = false
        ORDER BY p.billingDateDatetime ASC
        """
    )
    fun findUnpaidBySubscriptionIdOrderByBillingDateDatetimeAsc(subscriptionId: Int): List<Payment>

    @Query(
        value = """
        SELECT
            p.id AS payment_id,
            p.amount_to_pay,
            p.billing_date_datetime
        FROM payment p
        WHERE p.subscription_id = :subscriptionId
          AND p.paid = false
        ORDER BY p.billing_date_datetime ASC
        LIMIT 1
        """,
        nativeQuery = true
    )
    fun findOldestUnpaidPaymentRow(@Param("subscriptionId") subscriptionId: Int): List<Array<Any>>

    @Query(
        value = """
        SELECT
            p.id AS payment_id,
            s.id AS subscription_id,
            s.first_name,
            s.last_name,
            s.phone,
            p.amount_to_pay,
            p.amount_paid,
            p.billing_date_datetime,
            p.payment_date_datetime,
            p.paid
        FROM payment p
        INNER JOIN subscription s ON s.id = p.subscription_id
        WHERE p.id = :paymentId
        """,
        nativeQuery = true
    )
    fun findWhatsAppPaymentRowById(@Param("paymentId") paymentId: Int): List<Array<Any>>

    @Query(
        value = """
        SELECT COALESCE(SUM(p.amount_to_pay), 0)
        FROM payment p
        WHERE p.paid = false
          AND p.billing_date_datetime >= :startDate
          AND p.billing_date_datetime < :endDate
        """,
        nativeQuery = true
    )
    fun calculateTotalToCollectBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Double


    @Query(
        value = """
        SELECT COALESCE(SUM(p.discount_amount), 0)
        FROM payment p
        WHERE p.paid = true
          AND p.billing_date_datetime >= :startDate
          AND p.billing_date_datetime < :endDate
        """,
        nativeQuery = true
    )
    fun getTotalDiscountsBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Double

    @Query(
        value = """
        SELECT COALESCE(SUM(p.amount_to_pay), 0)
        FROM payment p
        WHERE p.billing_date_datetime >= :startDate
          AND p.billing_date_datetime < :endDate
        """,
        nativeQuery = true
    )
    fun getGrossRevenueBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Double

    @Query(
        value = """
        SELECT COALESCE(SUM(p.amount_paid), 0)
        FROM payment p
        WHERE p.paid = true
          AND p.billing_date_datetime >= :startDate
          AND p.billing_date_datetime < :endDate
        """,
        nativeQuery = true
    )
    fun getTotalRaisedBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Double


    @Query(
        "SELECT COUNT(p) FROM Payment as p WHERE p.subscription.id = :subscriptionId AND  p.paid = false"
    )
    fun findPendingPaymentsBySubscriptionId(subscriptionId: Int): Int


//    @Query(
//        """
//        SELECT COALESCE(SUM(p.amountPaid - p.discountAmount),0)
//        FROM Payment p
//        WHERE YEAR(FROM_UNIXTIME(p.billingDate / 1000)) = YEAR(CURRENT_DATE())
//        AND MONTH(FROM_UNIXTIME(p.billingDate / 1000)) = MONTH(CURRENT_DATE())
//        AND p.paid = true
//        """
//    )


    @Query("SELECT p from Payment p where p.billingDateDatetime >= :startDate and p.billingDateDatetime < :endDate")
    fun getLasMonthsPaymentMethodStatics(startDate: LocalDateTime, endDate: LocalDateTime): List<Payment>
    
    /**
     * Consulta optimizada para obtener estadísticas de métodos de pago agrupadas por mes
     * Esta consulta hace el trabajo en la base de datos en lugar de en memoria
     */
    @Query("""
    SELECT 
        MONTH(p.billing_date_datetime) as month,
        YEAR(p.billing_date_datetime) as year,
        p.method,
        COUNT(p.id) as totalPayments,
        SUM(CASE WHEN p.paid = true THEN 1 ELSE 0 END) as paidPayments,
        SUM(CASE WHEN p.paid = true AND p.method IN ('Plin', 'Yape', 'Transferencia') THEN 1 ELSE 0 END) as digitalPayments
    FROM payment p 
    WHERE p.billing_date_datetime >= :startDate
      AND p.billing_date_datetime < :endDate
    GROUP BY MONTH(p.billing_date_datetime), YEAR(p.billing_date_datetime), p.method
    ORDER BY year DESC, month DESC
""", nativeQuery = true)
    fun getPaymentMethodStatisticsOptimized(
        startDate: java.time.LocalDateTime,
        endDate: java.time.LocalDateTime
    ): List<Array<Any>>

    fun findBySubscriptionIdOrderByBillingDateDatetimeDesc(subscriptionId: Int): List<Payment>

    fun findTop5BySubscriptionIdOrderByBillingDateDatetimeDesc(subscriptionId: Int): List<Payment>

    fun findBySubscriptionIdIn(subscriptionIds: Collection<Int>): List<Payment>

    fun findBySubscriptionIdInAndPaidTrueAndPaymentDateDatetimeBetween(
        subscriptionIds: Collection<Int>,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Payment>

    @Query("SELECT p FROM Payment p LEFT JOIN FETCH p.responsible WHERE p.subscription.id IN :ids")
    fun findBySubscriptionIdInFetchResponsible(@Param("ids") ids: Collection<Int>): List<Payment>

    fun existsBySubscriptionIdAndBillingDateDatetimeBetween(subscriptionId: Int, startDate: LocalDateTime, endDate: LocalDateTime): Boolean

    fun existsBySubscriptionIdAndBillingDateDatetimeGreaterThanEqualAndBillingDateDatetimeLessThan(
        subscriptionId: Int,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Boolean
    //metodo para verificar si existen pagos con la misma fecha de facturacion
    fun existsByBillingDateDatetimeAndSubscriptionId(billingDateDatetime: LocalDateTime, subscriptionId: Int): Boolean

    @Query("SELECT distinct p.electronicPayerName from Payment p where p.subscription.id = :subscriptionId and p.electronicPayerName is not null")
    fun getElectronicPayers(subscriptionId: Int): List<String>?



    @Query(
        value = """
        SELECT
            COALESCE(SUM(p.amount_to_pay), 0) AS totalCharged,
            DATE_FORMAT(MIN(p.billing_date_datetime), '%Y-%m-01') AS billingMonth
        FROM payment p
        WHERE p.billing_date_datetime IS NOT NULL
        GROUP BY YEAR(p.billing_date_datetime), MONTH(p.billing_date_datetime)
        ORDER BY YEAR(p.billing_date_datetime) DESC, MONTH(p.billing_date_datetime) DESC
        LIMIT 6
    """,
        nativeQuery = true
    )
    fun getTop6GrossRevenueHistory(): List<Map<String, Any>>

}
