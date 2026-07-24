# Documentación local de planes

Esta carpeta guarda la documentación de cada plan que se implementa en el proyecto.

## Reglas

- **No hacer commit** de archivos en `local-docs/` (está en `.gitignore`).
- **No va a producción**: no forma parte del WAR ni del despliegue Maven.
- Uso exclusivamente local para el equipo / IA al retomar trabajo.

## Convención de nombres

```
local-docs/plans/YYYY-MM-DD_<nombre-corto-del-plan>.md
```

Ejemplo: `2026-07-21_backoffice-whatsapp-section.md`

## Contenido mínimo de cada documento

1. Resumen del plan y alcance acordado
2. Rama(s) de trabajo (backend / frontend)
3. Endpoints o cambios principales
4. Archivos tocados
5. Cómo probar
6. Pendientes post-implementación (deploy, config Meta, etc.)
