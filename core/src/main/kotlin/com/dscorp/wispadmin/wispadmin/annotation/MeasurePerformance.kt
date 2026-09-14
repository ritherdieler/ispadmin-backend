package com.dscorp.wispadmin.wispadmin.annotation

/**
 * Anotación para marcar métodos que deben ser medidos en rendimiento
 * Se puede usar junto con AOP para medición automática
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MeasurePerformance(
    val methodName: String = "",
    val logLevel: String = "INFO"
)





