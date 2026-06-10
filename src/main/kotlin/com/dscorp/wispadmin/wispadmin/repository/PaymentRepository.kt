package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime
interface PaymentRepository : JpaRepository<Payment, Int> {
    @Query("select p from Payment p where p.subscription.id = :subscriptionId and p.billingDateDatetime between :startDate and :endDate")
    fun findBySubscriptionFiltered(
        subscriptionId: Int,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<Payment>

    @Query(
        value = """
        SELECT COALESCE(SUM(p.amount_to_pay), 0)
        FROM payment p
        INNER JOIN subscription s ON p.subscription_id = s.id
        WHERE p.paid = false
          AND p.billing_date_datetime >= CAST(DATE_FORMAT(CURRENT_DATE() - INTERVAL 1 MONTH, '%Y-%m-01 00:00:00') AS DATETIME)
          AND p.billing_date_datetime < CAST(DATE_FORMAT(CURRENT_DATE(), '%Y-%m-01 00:00:00') AS DATETIME)
        """,
        nativeQuery = true
    )
    fun calculateTotalToCollectForCurrentMonth(): Double


    @Query(
        value = """
        SELECT COALESCE(SUM(p.discount_amount), 0)
        FROM payment p
        WHERE p.paid = true
          AND p.billing_date_datetime >= CAST(DATE_FORMAT(CURRENT_DATE() - INTERVAL 1 MONTH, '%Y-%m-01 00:00:00') AS DATETIME)
          AND p.billing_date_datetime < CAST(DATE_FORMAT(CURRENT_DATE(), '%Y-%m-01 00:00:00') AS DATETIME)
        """,
        nativeQuery = true
    )
    fun getTotalDiscountsForCurrentMonth(): Double

    @Query(
        value = """
        SELECT COALESCE(SUM(p.amount_to_pay), 0)
        FROM payment p
        INNER JOIN subscription s ON p.subscription_id = s.id
        WHERE p.billing_date_datetime >= CAST(DATE_FORMAT(CURRENT_DATE() - INTERVAL 1 MONTH, '%Y-%m-01 00:00:00') AS DATETIME)
          AND p.billing_date_datetime < CAST(DATE_FORMAT(CURRENT_DATE(), '%Y-%m-01 00:00:00') AS DATETIME)
        """,
        nativeQuery = true
    )
    fun getGrossRevenueForCurrentMonth(): Double

    @Query(
        value = """
        SELECT COALESCE(SUM(p.amount_paid), 0)
        FROM payment p
        WHERE p.paid = true
          AND p.billing_date_datetime >= CAST(DATE_FORMAT(CURRENT_DATE() - INTERVAL 1 MONTH, '%Y-%m-01 00:00:00') AS DATETIME)
          AND p.billing_date_datetime < CAST(DATE_FORMAT(CURRENT_DATE(), '%Y-%m-01 00:00:00') AS DATETIME)
        """,
        nativeQuery = true
    )
    fun getTotalRaisedForCurrentMonth(): Double


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


    @Query("SELECT p from Payment p where p.billingDateDatetime between :startDate and :endDate")
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
    WHERE p.billing_date_datetime BETWEEN :startDate AND :endDate
    GROUP BY MONTH(p.billing_date_datetime), YEAR(p.billing_date_datetime), p.method
    ORDER BY year DESC, month DESC
""", nativeQuery = true)
    fun getPaymentMethodStatisticsOptimized(
        startDate: java.time.LocalDateTime,
        endDate: java.time.LocalDateTime
    ): List<Array<Any>>

    fun findBySubscriptionIdOrderByBillingDateDatetimeDesc(subscriptionId: Int): List<Payment>

    fun existsBySubscriptionIdAndBillingDateDatetimeBetween(subscriptionId: Int, startDate: LocalDateTime, endDate: LocalDateTime): Boolean

    //metodo para verificar si existen pagos con la misma fecha de facturacion
    fun existsByBillingDateDatetimeAndSubscriptionId(billingDateDatetime: LocalDateTime, subscriptionId: Int): Boolean

    @Query("SELECT distinct p.electronicPayerName from Payment p where p.subscription.id = :subscriptionId and p.electronicPayerName is not null")
    fun getElectronicPayers(subscriptionId: Int): List<String>?



    @Query(
        value = """
            SELECT 
                SUM(p.amount_to_pay) AS totalCharged,
                UNIX_TIMESTAMP(DATE(MIN(p.billing_date_datetime))) * 1000 AS billingDate
            FROM payment p
            WHERE p.billing_date_datetime IS NOT NULL
            GROUP BY DATE(p.billing_date_datetime)
            ORDER BY DATE(p.billing_date_datetime) DESC
            LIMIT 6
        """,
        nativeQuery = true
    )
    fun getTop6GrossRevenueHistory(): List<Map<String, Any>>


}
