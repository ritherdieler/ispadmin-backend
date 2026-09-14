# Optimización de rendimiento de Cursor (workspace Gigafiber)

Fecha: 2026-07-01

## Cambios aplicados

### 1. `.cursorignore` en los 4 repos
- `ispadmin-backend/.cursorignore`
- `IpsAdmin-android app/.cursorignore`
- `ispadmin-asistencias/.cursorignore`
- `ispadmin-backoffice/.cursorignore`

Excluye builds, dependencias, logs, modelos ML pesados y archivos del sistema.

### 2. `settings.json` de Cursor (usuario)
Archivo: `~/Library/Application Support/Cursor/User/settings.json`

- `files.watcherExclude`: deja de vigilar `node_modules`, `build`, `target`, `dist`, `.gradle`, `logs`
- `search.exclude`: excluye las mismas carpetas de búsqueda
- `typescript.tsserver.maxTsServerMemory`: 3072
- `typescript.disableAutomaticTypeAcquisition`: true
- `java.autobuild.enabled`: false
- `gradle.nestedProjects`: false
- `files.maxMemoryForLargeFilesMB`: 4096

Recargar ventana: `Cmd+Shift+P` → **Developer: Reload Window**

### 3. Limpieza de artefactos locales
Eliminados:
- `ispadmin-backend/logs/*` y `target/`
- `IpsAdmin-android app/presentation/build`, `build/` y `./gradlew clean`
- `ispadmin-asistencias/asistencia-frontend/dist/`
- `ispadmin-backoffice/dist/`

Tamaños aproximados tras limpieza:
| Proyecto | Antes | Después |
|---|---|---|
| backend | 677 MB | 232 MB |
| android | 939 MB | 98 MB |
| asistencias | 844 MB | 609 MB |
| backoffice | 586 MB | 584 MB |

### 6. Extensiones y funciones del IDE (manual)
Revisar en **Extensions** y desactivar por workspace (`Disable (Workspace)`) las que no uses en la sesión:
- GitLens (si no lo usas activamente)
- Duplicados de linters (ESLint + Prettier en varios frontends)
- Language servers redundantes con varios proyectos abiertos

En **Cursor Settings → Features**, desactivar indexing o background agents si no los necesitas.

### 7. Git y `.DS_Store`
- Añadido `.DS_Store` a `ispadmin-backend/.gitignore`
- Creado `ispadmin-asistencias/.gitignore` con `.DS_Store`

## No aplicado (por solicitud)
- Punto 4: cambiar reglas `.cursor/rules` de `alwaysApply` a globs
- Punto 5: cambiar canal de actualización de `dev` a `stable`

## Uso recomendado del agente
- Abrir solo el repo en el que trabajas cuando no necesites los 4
- Usar `@archivo` o `@carpeta` en lugar de exploraciones amplias del workspace
- Iniciar chats nuevos en tareas largas para no arrastrar contexto
