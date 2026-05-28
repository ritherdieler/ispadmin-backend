# Sistema de Logs para Tareas Programadas (Scheduled Tasks)

## 📋 Descripción

Sistema completo de registro persistente en base de datos para las tareas programadas que realizan operaciones en Mikrotik. Cada vez que se ejecuta una tarea programada, se guarda un registro detallado con información sobre el total de registros procesados, creados, errores, etc.

## 🎯 Tareas Programadas Monitoreadas

### 1. Corte de Servicio de Internet (CUT_INTERNET_SERVICE)
- **Scheduler**: `CutServiceMonthlyTaskScheduler`
- **Programación**: 
  - Día 16 de cada mes (lunes a viernes) a las 00:00
  - Martes siguiente si el día 16 cae en fin de semana
- **Función**: Corta el servicio de internet a deudores y suscripciones canceladas mediante Address List en Mikrotik

### 2. Generación de Address List para Cancelados (GENERATE_ADDRESS_LIST_CANCELLED)
- **Scheduler**: `CancelledSubscriptionQueueAndAddressListScheduledTasksService`
- **Programación**: Día 1 de cada mes a las 01:00
- **Función**: Genera Address List en Mikrotik para suscripciones canceladas

### 3. Creación de Queues (CREATE_SUBSCRIPTIONS_QUEUE)
- **Scheduler**: `CancelledSubscriptionQueueAndAddressListScheduledTasksService`
- **Programación**: Día 1 de cada mes a las 01:00 (después de Address List)
- **Función**: Crea queues simples en Mikrotik para todas las suscripciones activas

## 🗂️ Estructura de Base de Datos

### Tabla: `scheduled_task_logs`

```sql
CREATE TABLE scheduled_task_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    task_type VARCHAR(100) NOT NULL,
    execution_date DATETIME NOT NULL,
    processed_count INT NOT NULL DEFAULT 0,
    created_count INT DEFAULT 0,
    deleted_count INT DEFAULT 0,
    already_exists_count INT DEFAULT 0,
    error_count INT DEFAULT 0,
    queues_generated INT DEFAULT 0,
    omitted_by_tv_cable INT DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    message TEXT,
    detailed_result LONGTEXT,
    error_message TEXT,
    INDEX idx_task_type (task_type),
    INDEX idx_execution_date (execution_date),
    INDEX idx_status (status)
);
```

### Campos

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | INT | ID único del registro |
| `task_type` | VARCHAR(100) | Tipo de tarea (ENUM) |
| `execution_date` | DATETIME | Fecha y hora de ejecución |
| `processed_count` | INT | Total de registros procesados |
| `created_count` | INT | Total de registros creados |
| `deleted_count` | INT | Total de registros eliminados |
| `already_exists_count` | INT | Total de registros que ya existían |
| `error_count` | INT | Total de errores encontrados |
| `queues_generated` | INT | Total de queues generadas (solo para CREATE_SUBSCRIPTIONS_QUEUE) |
| `omitted_by_tv_cable` | INT | Total omitidos por ser solo TV cable |
| `status` | VARCHAR(50) | Estado de ejecución (SUCCESS, PARTIAL_SUCCESS, FAILED) |
| `message` | TEXT | Mensaje descriptivo del resultado |
| `detailed_result` | LONGTEXT | Resultado detallado en formato JSON |
| `error_message` | TEXT | Mensaje de error si hubo falla |

## 📡 API Endpoints

### 1. Obtener Logs Recientes
```http
GET /api/scheduled-task-logs/recent?limit=50
```

**Parámetros:**
- `limit` (opcional): Número máximo de registros a retornar (default: 50)

**Respuesta:**
```json
[
  {
    "id": 1,
    "taskType": "CUT_INTERNET_SERVICE",
    "taskTypeName": "Corte de Servicio de Internet",
    "executionDate": "2025-10-16T00:00:00",
    "processedCount": 45,
    "createdCount": 40,
    "deletedCount": 35,
    "alreadyExistsCount": 0,
    "errorCount": 5,
    "queuesGenerated": 0,
    "omittedByTvCable": 0,
    "status": "PARTIAL_SUCCESS",
    "statusName": "Parcialmente Exitoso",
    "message": "Corte de servicio ejecutado: 40 agregados, 35 eliminados, 5 errores",
    "errorMessage": "Se encontraron 5 errores durante la ejecución"
  }
]
```

### 2. Obtener Logs por Tipo de Tarea
```http
GET /api/scheduled-task-logs/by-task-type/{taskType}
```

**Parámetros:**
- `taskType`: Tipo de tarea (CUT_INTERNET_SERVICE, GENERATE_ADDRESS_LIST_CANCELLED, CREATE_SUBSCRIPTIONS_QUEUE)

### 3. Obtener Logs por Rango de Fechas
```http
GET /api/scheduled-task-logs/by-date-range?startDate=2025-10-01T00:00:00&endDate=2025-10-31T23:59:59
```

**Parámetros:**
- `startDate`: Fecha de inicio (formato ISO 8601)
- `endDate`: Fecha de fin (formato ISO 8601)

### 4. Obtener Tipos de Tareas
```http
GET /api/scheduled-task-logs/task-types
```

**Respuesta:**
```json
[
  {
    "value": "CUT_INTERNET_SERVICE",
    "label": "Corte de Servicio de Internet"
  },
  {
    "value": "GENERATE_ADDRESS_LIST_CANCELLED",
    "label": "Generación de Address List (Cancelados)"
  },
  {
    "value": "CREATE_SUBSCRIPTIONS_QUEUE",
    "label": "Creación de Queues de Suscripciones"
  }
]
```

## 🔧 Componentes del Sistema

### 1. Entidad
- **Archivo**: `ScheduledTaskLog.kt`
- **Ubicación**: `com.dscorp.wispadmin.wispadmin.data.model`
- **Enums**: `ScheduledTaskType`, `TaskExecutionStatus`

### 2. Repositorio
- **Archivo**: `ScheduledTaskLogRepository.kt`
- **Ubicación**: `com.dscorp.wispadmin.wispadmin.repository`
- **Métodos**:
  - `findByTaskTypeOrderByExecutionDateDesc()`
  - `findRecentLogs(limit: Int)`
  - `findByExecutionDateBetween()`
  - `findByTaskTypeAndDateRange()`

### 3. Servicio
- **Archivo**: `ScheduledTaskLogService.kt`
- **Ubicación**: `com.dscorp.wispadmin.wispadmin.service`
- **Métodos principales**:
  - `logCutInternetService(result: CutServiceResultDto)`
  - `logAddressListGeneration(result: AddressListGenerationResultDto)`
  - `logQueueCreation(result: QueueCreationStats)`
  - `logTaskError(taskType: ScheduledTaskType, errorMessage: String)`

### 4. Controller
- **Archivo**: `ScheduledTaskLogController.kt`
- **Ubicación**: `com.dscorp.wispadmin.wispadmin.controller`

### 5. DTOs
- **CutServiceResultDto**: Resultado del corte de servicio
- **ScheduledTaskLogDto**: DTO para respuesta de API

## 📊 Estados de Ejecución

| Estado | Descripción |
|--------|-------------|
| `SUCCESS` | Ejecución completamente exitosa sin errores |
| `PARTIAL_SUCCESS` | Ejecución con algunos errores pero mayoría exitosa |
| `FAILED` | Ejecución completamente fallida |

## 🔍 Ejemplo de Uso

### Arquitectura de Logging

El sistema sigue el principio de **responsabilidad única**:
- Los **métodos del servicio** (`SubscriptionService`) se encargan de guardar los logs automáticamente
- Los **schedulers** solo invocan los métodos y manejan excepciones globales

### En el Servicio (SubscriptionService)
```kotlin
@Transactional
fun cutInternetService(): CutServiceResultDto {
    // ... lógica de procesamiento ...
    
    val result = CutServiceResultDto(
        processedCount = subscriptionsToProcess.size,
        createdCount = createdCount,
        deletedCount = deletedCount,
        errorCount = errorCount,
        message = "...",
        debtorsCount = debtors.size,
        cancelledCount = cancelledSubscriptions.size
    )
    
    // ✅ El servicio guarda el log automáticamente
    scheduledTaskLogService.logCutInternetService(result)
    
    return result
}
```

### En el Scheduler (CutServiceMonthlyTaskScheduler)
```kotlin
@Scheduled(cron = "0 0 0 16 * MON-FRI")
fun executeOn16thIfWeekday() {
    try {
        logger.info("🔄 Iniciando tarea programada: Corte de servicio mensual")
        val result = subscriptionService.cutInternetService() // El log se guarda automáticamente
        logger.info("✅ Tarea completada exitosamente")
    } catch (e: Exception) {
        logger.error("❌ Error en tarea programada: ${e.message}")
        // Solo se guarda log de error si falla completamente la ejecución
        scheduledTaskLogService.logTaskError(
            ScheduledTaskType.CUT_INTERNET_SERVICE,
            e.message ?: "Error desconocido"
        )
    }
}
```

## 📈 Ventajas del Sistema

1. **Trazabilidad Completa**: Registro histórico de todas las ejecuciones
2. **Auditoría**: Información detallada de cada operación
3. **Monitoreo**: Identificación rápida de errores y problemas
4. **Estadísticas**: Análisis de tendencias y patrones
5. **Debugging**: Información detallada en JSON para diagnóstico
6. **API REST**: Acceso programático a los logs desde el backoffice
7. **Separación de Responsabilidades**: Los servicios gestionan su propio logging
8. **Reutilizable**: Los métodos del servicio pueden ser invocados desde cualquier lugar y siempre registrarán sus operaciones
9. **Consistencia**: No importa desde dónde se llame el método, siempre se guardará el log

## 🚀 Despliegue

### Migración de Base de Datos
La tabla se crea automáticamente con Hibernate (`spring.jpa.hibernate.ddl-auto=update`), pero también existe un script SQL manual en:
```
src/main/resources/db/migration/V1__create_scheduled_task_logs_table.sql
```

### Verificación
Para verificar que el sistema está funcionando:
1. Esperar a que se ejecute una tarea programada
2. Consultar la tabla `scheduled_task_logs`
3. O usar el endpoint: `GET /api/scheduled-task-logs/recent`

## 📝 Notas Importantes

- Los logs se guardan automáticamente en cada ejecución
- El campo `detailed_result` contiene el objeto JSON completo del resultado
- Los errores individuales no detienen el proceso de logging
- Los logs persisten incluso si la tarea falla completamente
- Se recomienda implementar un proceso de limpieza periódica de logs antiguos

## 🔐 Seguridad

Los endpoints del controller pueden requerir autenticación/autorización según la configuración de seguridad de Spring Security en el proyecto.

## 📚 Referencias

- **Documentación de Scheduled Tasks**: [Spring Scheduled Tasks](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#scheduling)
- **Cron Expressions**: [Cron Expression Generator](https://crontab.guru/)

