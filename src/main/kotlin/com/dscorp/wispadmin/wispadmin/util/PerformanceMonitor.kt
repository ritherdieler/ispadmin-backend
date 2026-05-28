package com.dscorp.wispadmin.wispadmin.util

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Monitor de rendimiento para medir tiempos de ejecución de métodos
 * Especialmente útil para identificar cuellos de botella en repositorios
 */
@Component
class PerformanceMonitor {
    
    private val logger = LoggerFactory.getLogger(PerformanceMonitor::class.java)
    private val methodTimes = ConcurrentHashMap<String, AtomicLong>()
    private val methodCounts = ConcurrentHashMap<String, AtomicLong>()
    
    /**
     * Mide el tiempo de ejecución de un método y lo registra
     * @param methodName Nombre del método a medir
     * @param block Bloque de código a ejecutar
     * @return Resultado del bloque ejecutado
     */
    fun <T> measureTime(methodName: String, block: () -> T): T {
        val startTime = System.currentTimeMillis()
        return try {
            val result = block()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            recordMethodTime(methodName, duration)
            logger.info("⏱️  [PERFORMANCE] $methodName: ${duration}ms")
            
            result
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            recordMethodTime(methodName, duration)
            logger.error("❌ [PERFORMANCE] $methodName: ${duration}ms (ERROR: ${e.message})")
            throw e
        }
    }
    
    /**
     * Registra el tiempo de ejecución de un método
     */
    private fun recordMethodTime(methodName: String, duration: Long) {
        methodTimes.computeIfAbsent(methodName) { AtomicLong(0) }.addAndGet(duration)
        methodCounts.computeIfAbsent(methodName) { AtomicLong(0) }.incrementAndGet()
    }
    
    /**
     * Obtiene el tiempo total acumulado para un método
     */
    fun getTotalTime(methodName: String): Long {
        return methodTimes[methodName]?.get() ?: 0
    }
    
    /**
     * Obtiene el número de llamadas para un método
     */
    fun getCallCount(methodName: String): Long {
        return methodCounts[methodName]?.get() ?: 0
    }
    
    /**
     * Obtiene el tiempo promedio para un método
     */
    fun getAverageTime(methodName: String): Double {
        val totalTime = getTotalTime(methodName)
        val callCount = getCallCount(methodName)
        return if (callCount > 0) totalTime.toDouble() / callCount else 0.0
    }
    
    /**
     * Imprime un resumen completo de todos los métodos medidos
     */
    fun printPerformanceSummary() {
        logger.info("=" * 80)
        logger.info("📊 RESUMEN DE RENDIMIENTO - DASHBOARD SERVICE")
        logger.info("=" * 80)
        
        val sortedMethods = methodTimes.entries
            .sortedByDescending { it.value.get() }
        
        logger.info("🔍 MÉTODOS MÁS LENTOS:")
        sortedMethods.take(10).forEach { (methodName, totalTime) ->
            val callCount = getCallCount(methodName)
            val averageTime = getAverageTime(methodName)
            logger.info("  📈 $methodName: ${totalTime.get()}ms total | $callCount llamadas | ${String.format("%.2f", averageTime)}ms promedio")
        }
        
        logger.info("")
        logger.info("📋 TODOS LOS MÉTODOS:")
        sortedMethods.forEach { (methodName, totalTime) ->
            val callCount = getCallCount(methodName)
            val averageTime = getAverageTime(methodName)
            logger.info("  ⏱️  $methodName: ${totalTime.get()}ms total | $callCount llamadas | ${String.format("%.2f", averageTime)}ms promedio")
        }
        
        val totalTime = methodTimes.values.sumOf { it.get() }
        val totalCalls = methodCounts.values.sumOf { it.get() }
        logger.info("")
        logger.info("🎯 TOTAL: ${totalTime}ms | $totalCalls llamadas | ${String.format("%.2f", if (totalCalls > 0) totalTime.toDouble() / totalCalls else 0.0)}ms promedio general")
        logger.info("=" * 80)
    }
    
    /**
     * Limpia todas las métricas almacenadas
     */
    fun clearMetrics() {
        methodTimes.clear()
        methodCounts.clear()
        logger.info("🧹 Métricas de rendimiento limpiadas")
    }
    
    /**
     * Obtiene un mapa con todas las métricas
     */
    fun getAllMetrics(): Map<String, MethodMetrics> {
        return methodTimes.keys.associateWith { methodName ->
            MethodMetrics(
                methodName = methodName,
                totalTime = getTotalTime(methodName),
                callCount = getCallCount(methodName),
                averageTime = getAverageTime(methodName)
            )
        }
    }
}

/**
 * Clase de datos para almacenar métricas de un método
 */
data class MethodMetrics(
    val methodName: String,
    val totalTime: Long,
    val callCount: Long,
    val averageTime: Double
)

/**
 * Extensión para repetir strings
 */
private operator fun String.times(n: Int): String = this.repeat(n)
