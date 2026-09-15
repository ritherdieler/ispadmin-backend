# Archify — lab local Core / Gateway / ACS

Generado con skill `archify` v2.17 (showcase).

| Artefacto | Ruta |
|-----------|------|
| Especificación | `lab-local.architecture.json` |
| HTML | `lab-local.html` |
| SHA-256 HTML | `3f01c28820ea44cce34bd1c1bf6bc86e17f6cffd8dd124d3cd4df8da659a7cd8` |

**Validación:** 9/9 checks, composition pass, 0 errors / 0 warnings.

**visual-check:** omitido (Chrome/Chromium no disponible en este entorno). Abrir `lab-local.html` en el navegador para revisión perceptual.

**Contenido:** stack canónico de pruebas locales (cliente → Core `:8082` → Gateway `:8080` → OLT; Gateway → ACS `:8091` → GenieACS `:7557` → ONU lab; Core → MK2 id 8).
