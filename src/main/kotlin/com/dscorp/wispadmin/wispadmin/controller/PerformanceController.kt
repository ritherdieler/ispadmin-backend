package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.util.MethodMetrics
import com.dscorp.wispadmin.wispadmin.util.PerformanceMonitor
import org.springframework.web.bind.annotation.*

/**
 * Controlador para obtener métricas de rendimiento del dashboard
 */
@RestController
@RequestMapping("/api/performance")
class PerformanceController(
    private val performanceMonitor: PerformanceMonitor
) {
    
    /**
     * Obtiene todas las métricas de rendimiento
     */
    @GetMapping("/metrics")
    fun getPerformanceMetrics(): Map<String, MethodMetrics> {
        return performanceMonitor.getAllMetrics()
    }
    
    /**
     * Obtiene el resumen de rendimiento en formato texto
     */
    @GetMapping("/summary")
    fun getPerformanceSummary(): String {
        val metrics = performanceMonitor.getAllMetrics()
        val sortedMethods = metrics.values.sortedByDescending { it.totalTime }
        
        val summary = StringBuilder()
        summary.appendLine("=" * 80)
        summary.appendLine("📊 RESUMEN DE RENDIMIENTO - DASHBOARD SERVICE")
        summary.appendLine("=" * 80)
        
        summary.appendLine("🔍 MÉTODOS MÁS LENTOS:")
        sortedMethods.take(10).forEach { metric ->
            summary.appendLine("  📈 ${metric.methodName}: ${metric.totalTime}ms total | ${metric.callCount} llamadas | ${String.format("%.2f", metric.averageTime)}ms promedio")
        }
        
        summary.appendLine("")
        summary.appendLine("📋 TODOS LOS MÉTODOS:")
        sortedMethods.forEach { metric ->
            summary.appendLine("  ⏱️  ${metric.methodName}: ${metric.totalTime}ms total | ${metric.callCount} llamadas | ${String.format("%.2f", metric.averageTime)}ms promedio")
        }
        
        val totalTime = metrics.values.sumOf { it.totalTime }
        val totalCalls = metrics.values.sumOf { it.callCount }
        summary.appendLine("")
        summary.appendLine("🎯 TOTAL: ${totalTime}ms | ${totalCalls} llamadas | ${String.format("%.2f", if (totalCalls > 0) totalTime.toDouble() / totalCalls else 0.0)}ms promedio general")
        summary.appendLine("=" * 80)
        
        return summary.toString()
    }
    
    /**
     * Limpia todas las métricas de rendimiento
     */
    @PostMapping("/clear")
    fun clearMetrics(): String {
        performanceMonitor.clearMetrics()
        return "Métricas de rendimiento limpiadas exitosamente"
    }
    
    /**
     * Obtiene métricas específicas de un método
     */
    @GetMapping("/method/{methodName}")
    fun getMethodMetrics(@PathVariable methodName: String): MethodMetrics? {
        return performanceMonitor.getAllMetrics()[methodName]
    }
}

/**
 * Extensión para repetir strings
 */
private operator fun String.times(n: Int): String = this.repeat(n)





