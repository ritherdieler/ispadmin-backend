# Sistema Mock para OLT en Desarrollo

## Descripción

Este sistema permite simular las interacciones con la OLT (Optical Line Terminal) en el ambiente de desarrollo, evitando afectar el dispositivo de producción durante las pruebas y desarrollo.

## Configuración

### Ambiente de Desarrollo
```properties
# application-dev.properties
olt.service.mock.enabled=true
olt.service.base-url=https://gigafiberperu.smartolt.com/api/
olt.service.api-key=8503d7e652ae44a79869f30bb473c75d
```

### Ambiente de Producción
```properties
# application-prod.properties
olt.service.mock.enabled=false
olt.service.base-url=https://gigafiberperu.smartolt.com/api/
olt.service.api-key=8503d7e652ae44a79869f30bb473c75d
```

## Arquitectura

### Componentes

1. **OltService (Interfaz)**: Define el contrato para todas las operaciones con la OLT
2. **RealOltService**: Implementación real que hace llamadas HTTP a la OLT de producción
3. **MockOltService**: Implementación mock que simula respuestas para desarrollo
4. **OltServiceConfig**: Configuración de Spring que inyecta el servicio correcto según el ambiente

### Operaciones Soportadas

- `getUnConfiguredOnus()`: Obtiene ONUs no configuradas
- `getOnuBySn(sn)`: Obtiene detalles de una ONU por serial
- `authorizeOnuInSmartOltWidthPostMethod(request)`: Autoriza una ONU en la OLT
- `moveOnu(request, onu, napBox)`: Mueve una ONU a un nuevo puerto
- `deleteOnu(externalId)`: Elimina una ONU de la OLT

## Funcionalidades del Mock

### Datos Simulados
- **ONUs no configuradas**: Lista inicial con 2 ONUs mock
- **Detalles de ONU**: Respuestas simuladas con datos realistas
- **Estado en memoria**: Mantiene estado de ONUs autorizadas y movidas

### Comportamiento Realista
- **Delays simulados**: Simula latencia de red (300ms - 1000ms)
- **Logging detallado**: Registra todas las operaciones para debugging
- **Validaciones**: Simula respuestas de éxito y error según el contexto

## Endpoints de Debug

### GET /debug/olt/status
Obtiene el estado actual del mock OLT:
```json
{
  "status": 200,
  "message": "Estado del Mock OLT",
  "data": {
    "mockEnabled": true,
    "authorizedOnus": 0,
    "unconfiguredOnus": 2,
    "onuDetails": 1
  }
}
```

### POST /debug/olt/reset
Resetea los datos mock a su estado inicial:
```json
{
  "status": 200,
  "message": "Datos mock reseteados exitosamente",
  "data": {
    "authorizedOnus": 0,
    "unconfiguredOnus": 2,
    "onuDetails": 1
  }
}
```

## Uso en Desarrollo

### 1. Verificar que el Mock está Activo
```bash
curl http://localhost:8080/debug/olt/status
```

### 2. Probar Operaciones
Las operaciones normales de ONU funcionarán igual, pero usando datos simulados:
- Registrar suscripciones con ONUs
- Mover ONUs entre puertos
- Eliminar ONUs
- Consultar ONUs no configuradas

### 3. Resetear Datos si es Necesario
```bash
curl -X POST http://localhost:8080/debug/olt/reset
```

## Ventajas

1. **Seguridad**: No afecta la OLT de producción
2. **Velocidad**: Respuestas instantáneas sin latencia de red
3. **Consistencia**: Datos predecibles para pruebas
4. **Debugging**: Logging detallado de todas las operaciones
5. **Flexibilidad**: Fácil de resetear y configurar

## Consideraciones

- El mock solo está disponible en ambiente de desarrollo
- Los datos se mantienen en memoria y se pierden al reiniciar la aplicación
- Las respuestas son simuladas y pueden no reflejar todos los casos edge de la OLT real
- Para pruebas más complejas, se recomienda usar el ambiente de staging con OLT real

## Testing

Se incluyen pruebas unitarias completas en `MockOltServiceTest.kt` que cubren:
- Obtención de ONUs no configuradas
- Consulta de ONUs por serial
- Autorización de ONUs
- Movimiento de ONUs
- Eliminación de ONUs
- Reset de datos mock
