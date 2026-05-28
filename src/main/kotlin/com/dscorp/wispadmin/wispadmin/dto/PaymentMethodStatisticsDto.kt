package com.dscorp.wispadmin.wispadmin.dto

/**
 * DTO para estadísticas de métodos de pago optimizadas
 */
data class PaymentMethodStatisticsDto(
    val month: Int,
    val year: Int,
    val method: String,
    val totalPayments: Long,
    val paidPayments: Long,
    val digitalPayments: Long
) {
    /**
     * Calcula el porcentaje de pagos digitales
     */
    fun getDigitalPaymentPercentage(): Double {
        return if (totalPayments > 0) {
            (digitalPayments.toDouble() / totalPayments) * 100
        } else 0.0
    }
    
    /**
     * Obtiene el nombre del mes en español
     */
    fun getMonthName(): String {
        return when (month) {
            1 -> "Enero"
            2 -> "Febrero"
            3 -> "Marzo"
            4 -> "Abril"
            5 -> "Mayo"
            6 -> "Junio"
            7 -> "Julio"
            8 -> "Agosto"
            9 -> "Setiembre"
            10 -> "Octubre"
            11 -> "Noviembre"
            12 -> "Diciembre"
            else -> "Desconocido"
        }
    }
    
    /**
     * Obtiene el nombre del mes abreviado para el dashboard
     */
    fun getMonthNameShort(): String {
        return getMonthName().substring(0, 3).replaceFirstChar { it.uppercase() } + "."
    }
}





