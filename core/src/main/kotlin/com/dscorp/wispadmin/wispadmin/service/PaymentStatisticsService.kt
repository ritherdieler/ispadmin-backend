package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.dto.PaymentMethodStatisticsDto
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Servicio optimizado para estadisticas de metodos de pago.
 */
@Service
class PaymentStatisticsService(
    private val paymentRepository: PaymentRepository
) {

    /**
     * Obtiene el porcentaje mensual de pagos digitales dentro del rango enviado.
     * Mantiene la entrada en milisegundos por compatibilidad con los controladores existentes.
     */
    fun getPaymentMethodStatisticsOptimized(startDate: Long, endDate: Long): Map<String, Double> {
        return getPaymentMethodStatisticsOptimized(
            startDate.toLocalDateTime(),
            endDate.toLocalDateTime()
        )
    }

    /**
     * Obtiene el porcentaje mensual de pagos digitales usando fechas normales.
     */
    fun getPaymentMethodStatisticsOptimized(startDate: LocalDateTime, endDate: LocalDateTime): Map<String, Double> {
        val statistics = getDetailedPaymentMethodStatistics(startDate, endDate)

        return statistics
            .groupBy { it.getMonthNameShort() }
            .mapValues { (_, monthStats) ->
                val totalPayments = monthStats.sumOf { it.totalPayments }
                val digitalPayments = monthStats.sumOf { it.digitalPayments }

                if (totalPayments > 0) {
                    (digitalPayments.toDouble() / totalPayments) * 100
                } else {
                    0.0
                }
            }
    }

    /**
     * Obtiene estadisticas detalladas por metodo de pago dentro del rango enviado.
     */
    fun getDetailedPaymentMethodStatistics(startDate: Long, endDate: Long): List<PaymentMethodStatisticsDto> {
        return getDetailedPaymentMethodStatistics(
            startDate.toLocalDateTime(),
            endDate.toLocalDateTime()
        )
    }

    /**
     * Obtiene estadisticas detalladas por metodo de pago usando fechas normales.
     */
    fun getDetailedPaymentMethodStatistics(startDate: LocalDateTime, endDate: LocalDateTime): List<PaymentMethodStatisticsDto> {
        val rawResults = paymentRepository.getPaymentMethodStatisticsOptimized(startDate, endDate)

        return rawResults.map { row ->
            PaymentMethodStatisticsDto(
                month = (row[0] as? Number)?.toInt() ?: 0,
                year = (row[1] as? Number)?.toInt() ?: 0,
                method = row[2] as? String ?: "Desconocido",
                totalPayments = (row[3] as? Number)?.toLong() ?: 0L,
                paidPayments = (row[4] as? Number)?.toLong() ?: 0L,
                digitalPayments = (row[5] as? Number)?.toLong() ?: 0L
            )
        }
    }

    /**
     * Obtiene el porcentaje promedio de pagos digitales dentro del rango enviado.
     */
    fun getAverageDigitalPaymentPercentage(startDate: Long, endDate: Long): Double {
        val statistics = getDetailedPaymentMethodStatistics(startDate, endDate)
        val totalPayments = statistics.sumOf { it.totalPayments }
        val totalDigitalPayments = statistics.sumOf { it.digitalPayments }

        return if (totalPayments > 0) {
            (totalDigitalPayments.toDouble() / totalPayments) * 100
        } else {
            0.0
        }
    }

    /**
     * Convierte los milisegundos recibidos por APIs antiguas al tipo datetime usado por la entidad Payment.
     */
    private fun Long.toLocalDateTime(): LocalDateTime {
        return Instant.ofEpochMilli(this)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
    }
}
