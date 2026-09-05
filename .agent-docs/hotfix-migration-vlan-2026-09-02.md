# Hotfix prod — migración a fibra exige VLAN desde la app

**Fecha:** 2026-09-02  
**Rama:** `hotfix/migration-vlan-from-app` (desde `dfd2e4c` = `APP_RELEASE` en VPS)  
**Síntoma:** `PUT /subscription/migration` → HTTP 500  
**Error:** `vlan es obligatoria (enviada desde la app)`

## Causa

`migrateToFiber` llamaba `fiberInstallationStrategy.resolveVlan(subscription)`, que lee `subscription.vlan`. En migraciones wireless→fibra ese campo viene vacío. La app (`MigrationRequest`) no enviaba `vlan`.

## Fix backend

- `MigrationRequest.vlan` (opcional).
- `SubscriptionVlanRules.resolveMigrationVlan`: usa la VLAN de la app; si falta, default `"100"` (compat con APK antiguas).
- `migrateToFiber` setea `subscription.vlan` y autoriza ONU con esa VLAN **sin** `assertPoolAligned` sobre la IP wireless previa.

## Fix Android

- `MigrationRequest.vlan`
- Formulario de migración: selector VLAN (default `100`) y lo envía en el body.

## Deploy / distribución

- Backend: `./scripts/deploy.sh --env prod` desde la rama hotfix.
- Android: bump + Firebase App Distribution (`scripts/firebase-distribute-prod-debug.sh`).
