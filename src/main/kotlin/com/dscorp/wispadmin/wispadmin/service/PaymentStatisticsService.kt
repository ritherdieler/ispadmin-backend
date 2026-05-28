package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.dto.PaymentMethodStatisticsDto
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import org.springframework.stereotype.Service
import java.util.*

/**
 * Servicio optimizado para estadísticas de métodos de pago
 */
@Service
class PaymentStatisticsService(
    private val paymentRepository: PaymentRepository
) {
    
    /**
     * Obtiene estadísticas de métodos de pago optimizadas
     * Esta versión es mucho más rápida que la original
     */
    fun getPaymentMethodStatisticsOptimized(startDate: Long, endDate: Long): Map<String, Double> {
        val rawResults = paymentRepository.getPaymentMethodStatisticsOptimized(startDate, endDate)
        
        // Convertir resultados a DTOs
        val statistics = rawResults.map { row ->
            PaymentMethodStatisticsDto(
                month = (row[0] as? Number)?.toInt() ?: 0,
                year = (row[1] as? Number)?.toInt() ?: 0,
                method = row[2] as? String ?: "Desconocido",
                totalPayments = (row[3] as? Number)?.toLong() ?: 0L,
                paidPayments = (row[4] as? Number)?.toLong() ?: 0L,
                digitalPayments = (row[5] as? Number)?.toLong() ?: 0L
            )
        }
        
        // Agrupar por mes y calcular porcentajes
        val monthlyStats = statistics
            .groupBy { "${it.getMonthNameShort()}" }
            .mapValues { (_, monthStats) ->
                val totalPayments = monthStats.sumOf { it.totalPayments }
                val digitalPayments = monthStats.sumOf { it.digitalPayments }
                
                if (totalPayments > 0) {
                    (digitalPayments.toDouble() / totalPayments) * 100
                } else 0.0
            }
        
        return monthlyStats
    }
    
    /**
     * Obtiene estadísticas detalladas por método de pago
     */
    fun getDetailedPaymentMethodStatistics(startDate: Long, endDate: Long): List<PaymentMethodStatisticsDto> {
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
     * Obtiene el porcentaje promedio de pagos digitales en el período
     */
    fun getAverageDigitalPaymentPercentage(startDate: Long, endDate: Long): Double {
        val statistics = getDetailedPaymentMethodStatistics(startDate, endDate)
        
        val totalPayments = statistics.sumOf { it.totalPayments }
        val totalDigitalPayments = statistics.sumOf { it.digitalPayments }
        
        return if (totalPayments > 0) {
            (totalDigitalPayments.toDouble() / totalPayments) * 100
        } else 0.0
    }
}
