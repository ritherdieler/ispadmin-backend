# DJL PyTorch native_helper fix (local dev)

## Error

`Failed to load PyTorch native library` with root cause:

`Invalid native_helper: ai.djl.pytorch.jni.NativeHelper`

## Cause

`DjlNativeBootstrap` set `ai.djl.pytorch.native_helper` to a class that does not exist in DJL 0.36.0.

## Fix

- Removed automatic `native_helper` assignment in `DjlNativeBootstrap` for Spring Boot (`spring-boot:run`).
- Added `PytorchNativeHelper` for external Tomcat only (`setenv.sh` / `install-djl-tomcat-libs.sh`).

## Verified (2026-06-16)

```
Detector facial DJL ultranet listo.
Motor facial DJL listo para generar descriptores.
```
