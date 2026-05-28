package com.dscorp.wispadmin.wispadmin.repository;

import com.dscorp.wispadmin.wispadmin.data.model.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PaymentRepository : JpaRepository<Payment, Int> {
    @Query("select p from Payment p where p.subscription.id = :subscriptionId and p.billingDate between :startDate and :endDate")
    fun findBySubscriptionFiltered(subscriptionId: Int, startDate: Long, endDate: Long): List<Payment>

    @Query(
        """
    SELECT COALESCE(SUM(p.amountToPay), 0)
    FROM Subscription s
    JOIN s.payments p
    WHERE p.paid = false
    AND (
        (MONTH(CURRENT_DATE()) != 1 AND MONTH(FROM_UNIXTIME(p.billingDate/1000)) = MONTH(CURRENT_DATE())-1 AND YEAR(FROM_UNIXTIME(p.billingDate/1000)) = YEAR(CURRENT_DATE())) OR
        (MONTH(CURRENT_DATE()) = 1 AND MONTH(FROM_UNIXTIME(p.billingDate/1000)) = 12 AND YEAR(FROM_UNIXTIME(p.billingDate/1000)) = YEAR(CURRENT_DATE())-1)
    )
"""
    )
    fun calculateTotalToCollectForCurrentMonth(): Double


    @Query(
        """
    SELECT COALESCE(SUM(p.discountAmount), 0)
    FROM Payment p
    WHERE p.paid = true
    AND (
        (MONTH(CURRENT_DATE()) != 1 AND MONTH(FROM_UNIXTIME(p.billingDate/1000)) = MONTH(CURRENT_DATE())-1 AND YEAR(FROM_UNIXTIME(p.billingDate/1000)) = YEAR(CURRENT_DATE())) OR
        (MONTH(CURRENT_DATE()) = 1 AND MONTH(FROM_UNIXTIME(p.billingDate/1000)) = 12 AND YEAR(FROM_UNIXTIME(p.billingDate/1000)) = YEAR(CURRENT_DATE())-1)
    )
"""
    )
    fun getTotalDiscountsForCurrentMonth(): Double

    @Query(
        """
    SELECT COALESCE(SUM(p.amountToPay), 0)
    FROM Payment p
    inner join p.subscription s
    WHERE (
        (MONTH(CURRENT_DATE()) != 1 AND MONTH(FROM_UNIXTIME(p.billingDate/1000)) = MONTH(CURRENT_DATE())-1 AND YEAR(FROM_UNIXTIME(p.billingDate/1000)) = YEAR(CURRENT_DATE())) OR
        (MONTH(CURRENT_DATE()) = 1 AND MONTH(FROM_UNIXTIME(p.billingDate/1000)) = 12 AND YEAR(FROM_UNIXTIME(p.billingDate/1000)) = YEAR(CURRENT_DATE())-1)
    )
"""
    )
    fun getGrossRevenueForCurrentMonth(): Double

    @Query(
        """
    SELECT COALESCE(SUM(p.amountPaid), 0)
    FROM Payment p
    WHERE p.paid = true
    AND (
        (MONTH(CURRENT_DATE()) != 1 AND MONTH(FROM_UNIXTIME(p.billingDate/1000)) = MONTH(CURRENT_DATE())-1 AND YEAR(FROM_UNIXTIME(p.billingDate/1000)) = YEAR(CURRENT_DATE())) OR
        (MONTH(CURRENT_DATE()) = 1 AND MONTH(FROM_UNIXTIME(p.billingDate/1000)) = 12 AND YEAR(FROM_UNIXTIME(p.billingDate/1000)) = YEAR(CURRENT_DATE())-1)
    )
"""
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


    @Query("SELECT p from Payment p where  p.billingDate between :startDate and :endDate")
    fun getLasMonthsPaymentMethodStatics(startDate: Long, endDate: Long): List<Payment>
    
    /**
     * Consulta optimizada para obtener estadísticas de métodos de pago agrupadas por mes
     * Esta consulta hace el trabajo en la base de datos en lugar de en memoria
     */
    @Query("""
        SELECT 
            MONTH(FROM_UNIXTIME(p.billing_date/1000)) as month,
            YEAR(FROM_UNIXTIME(p.billing_date/1000)) as year,
            p.method,
            COUNT(p.id) as totalPayments,
            SUM(CASE WHEN p.paid = true THEN 1 ELSE 0 END) as paidPayments,
            SUM(CASE WHEN p.paid = true AND p.method IN ('Plin', 'Yape', 'Transferencia') THEN 1 ELSE 0 END) as digitalPayments
        FROM payment p 
        WHERE p.billing_date BETWEEN :startDate AND :endDate
        GROUP BY MONTH(FROM_UNIXTIME(p.billing_date/1000)), YEAR(FROM_UNIXTIME(p.billing_date/1000)), p.method
        ORDER BY year DESC, month DESC
    """, nativeQuery = true)
    fun getPaymentMethodStatisticsOptimized(startDate: Long, endDate: Long): List<Array<Any>>

    fun findBySubscriptionIdOrderByBillingDateDesc(subscriptionId: Int): List<Payment>

    fun existsBySubscriptionIdAndBillingDateBetween(subscriptionId: Int, startDate: Long, endDate: Long): Boolean

    //metodo para verificar si existen pagos con la misma fecha de facturacion
    fun existsByBillingDateAndSubscriptionId(billingDate: Long, subscriptionId: Int): Boolean

    @Query("SELECT distinct p.electronicPayerName from Payment p where p.subscription.id = :subscriptionId and p.electronicPayerName is not null")
    fun getElectronicPayers(subscriptionId: Int): List<String>?



    @Query(
        value = "SELECT p.billing_date as billingDate, SUM(p.amount_to_pay) as totalCharged from payment p group by p.billing_date order by p.billing_date desc LIMIT 6",
        nativeQuery = true
    )
    fun getTop6GrossRevenueHistory(): List<Map<Double, Long>>


}