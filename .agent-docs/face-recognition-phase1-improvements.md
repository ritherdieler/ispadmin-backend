# Mejoras Fase 1 — Reconocimiento Facial

Fecha: 2026-06-24

## Implementado

### Template maestro (MASTER)
- Nuevo angulo `FaceAngle.MASTER` en `face_data`.
- En enrolamiento multi-angulo se guarda embedding promedio L2-normalizado de FRONT+LEFT+RIGHT.
- Usuarios existentes sin MASTER reciben template virtual en cache (`FaceVerifyService.augmentWithMasterEmbeddings`).

### Liveness pasivo
- `FaceLivenessService`: varianza Laplaciana + varianza de color en region central.
- Integrado en `FacePhotoDescriptorService.generateDescriptor` y `generateDescriptorCandidates`.
- Config: `face.recognition.liveness.*` (enabled, min-laplacian-variance, min-color-variance).

### Matching ponderado por calidad
- `FacePhotoQualityService` expone `qualityScore` (0-1).
- `FaceVerifyService` aplica peso suave al score del probe cuando `quality-weighted-matching=true`.

### Calibracion ROC
- Script: `scripts/calibrate-face-thresholds.py`
- Uso: `python3 scripts/calibrate-face-thresholds.py logs/wispadmin-dev/wispadmin-face-metrics.log`

## Propiedades nuevas (dev/prod)

```properties
face.recognition.matching.quality-weighted-matching=true
face.recognition.matching.quality-weight-min=0.85
face.recognition.liveness.enabled=true
face.recognition.liveness.min-laplacian-variance=35
face.recognition.liveness.min-color-variance=12
```

## Despliegue produccion (2026-06-24)

- Comando: `bash scripts/deploy.sh --deploy`
- VPS: `http://212.85.13.47:8080/ispadmin/` -> HTTP 200
- WAR: ~232 MB, contenedor `tomcat9027`
- Verificado: `GET /api/face-data/admin/embedding-inventory` -> embeddings 512-D activos

## Archivos clave

- `util/FaceEmbeddingMath.kt`
- `service/FaceLivenessService.kt`
- `service/FaceVerifyService.kt` (MASTER virtual + quality weight)
- `controller/FaceDataController.kt` (MASTER persistido)
- `scripts/calibrate-face-thresholds.py`
