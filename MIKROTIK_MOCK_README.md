# Configuración de MikroTik en Desarrollo

## Objetivo

Esta guía define cómo configurar el backend para trabajar con MikroTik en desarrollo en tres modos:

- Mock puro (sin conexión real)
- Dispositivo real `mikrotik_test`
- Dispositivo real por IP específica (override)

## Propiedades

Las propiedades se configuran en `application-dev.properties`:

```properties
mikrotik.connection.mock.enabled=true
mikrotik.connection.override.ip=
mikrotik.connection.override.username=
mikrotik.connection.override.password=
```

## Precedencia de configuración

El backend evalúa las propiedades en este orden:

1. Si `mikrotik.connection.override.ip` tiene valor, usa esa IP y no usa `mikrotik_test`.
2. Si `override.ip` está vacío y `mikrotik.connection.mock.enabled=true`, usa mock puro (sin conexión real).
3. Si `override.ip` está vacío y `mikrotik.connection.mock.enabled=false`, usa el dispositivo real recibido por flujo de negocio (por ejemplo `hostDevice`).

## Modos de uso

### 1) Mock puro (sin equipo físico)

```properties
mikrotik.connection.mock.enabled=true
mikrotik.connection.override.ip=
mikrotik.connection.override.username=
mikrotik.connection.override.password=
```

Comportamiento:

- No abre sockets a MikroTik.
- Devuelve respuestas simuladas para operaciones comunes.
- Ideal para pruebas funcionales del backend y frontend sin infraestructura de red.

### 2) MikroTik real por override de IP

```properties
mikrotik.connection.mock.enabled=true
mikrotik.connection.override.ip=192.168.1.254
mikrotik.connection.override.username=admin
mikrotik.connection.override.password=admin123
```

Comportamiento:

- Fuerza conexión real a la IP indicada.
- Tiene prioridad sobre el modo mock.
- Si usuario/password están vacíos, usa fallback de credenciales del dispositivo del flujo.

### 3) MikroTik real por flujo normal

```properties
mikrotik.connection.mock.enabled=false
mikrotik.connection.override.ip=
mikrotik.connection.override.username=
mikrotik.connection.override.password=
```

Comportamiento:

- Usa el dispositivo real del flujo (`hostDevice`, `networkDevice`, etc.).
- No usa respuestas simuladas.

## Sobre `mikrotik_test`

Con el comportamiento actual:

- `mikrotik_test` no se utiliza cuando estás en mock puro.
- Si quieres probar con un equipo real fijo, usa `override.ip`.
- Si quieres que el backend vuelva a usar explícitamente `mikrotik_test` como modo dedicado, se puede agregar un flag adicional para ese comportamiento.

## Operaciones cubiertas por el mock puro

El mock actual cubre rutas de lectura y monitoreo frecuentes (interfaces, recursos de sistema, listas de deudores, reglas de firewall) y además evita aperturas de conexión real en rutas que usan ejecución directa de comandos.

## Recomendación para desarrollo diario

- Sin equipo disponible: usa mock puro.
- Con equipo de laboratorio estable: usa override por IP.
- Pruebas de integración por flujo real: desactiva mock y deja override vacío.
