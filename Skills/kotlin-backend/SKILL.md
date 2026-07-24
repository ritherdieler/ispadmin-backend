---
name: kotlin-backend
description: Reglas y directrices de optimización y seguridad HTTPS para el backend de mapas en Spring Boot
globs:
  - "src/main/kotlin/**"
---

## Reglas para la IA

- Usa Clean Architecture separando controladores de servicios de datos.
- Toda llamada a APIs de mapas externas (Google/OSRM) debe encapsularse de forma segura sobre HTTPS usando `withContext(Dispatchers.IO)`.
- Valida los sectores geográficos usando tipos de datos inmutables (`data class`).
- Prohibido usar bloqueos de hilos (`Thread.sleep`), prioriza siempre suspensiones asíncronas nativas.
- Garantiza que todos los endpoints expuestos requieran conexiones cifradas TLS/HTTPS y manejen de forma segura las cabeceras de origen (CORS).
