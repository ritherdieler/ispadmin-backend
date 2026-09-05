# Hotfix prod — deuda y facturas pendientes en el buscador

**Fecha:** 2026-09-03  
**Rama:** `hotfix/search-debt-hydrate` (desde `4c1cc1f` = última release prod)  
**Síntoma:** las cards del buscador de suscripciones en Android no muestran deuda ni cantidad de facturas pendientes.

## Causa

`GET /subscription/search` hidrataba con `findAllById` y `toDto()` **sin** cargar `payments`.

Desde `8ae6a52` (`initializedPayments()`), si la colección LAZY no está inicializada `toDto()` devuelve `pendingInvoiceQuantity=0` y `totalDebt=0.0`. La UI Android oculta el bloque de deuda cuando `totalDebt > 0` es falso.

El guard lazy es correcto para TR-069 async; el fallo es el search sin hidratar payments.

## Fix backend

- `SubscriptionSearchService.hydrate` carga payments por lote con `PaymentRepository.findBySubscriptionIdInFetchResponsible` (mismo patrón que `SubscriptionService.findAllForListing`) y los asigna **antes** de `toDto()`.
- No se revierte `initializedPayments()`.

## Android

Sin cambios. El mapeo `pendingInvoiceQuantity` / `totalDebt` ya era correcto.

## Deploy

- Backend: `./scripts/deploy.sh --env prod` desde `hotfix/search-debt-hydrate` @ `83de8fe` → `APP_RELEASE=1.0.3+83de8fe` (VPS 2026-09-03).
- Verificado: Tomcat UP, health 200, WAR en webapps, `APP_RELEASE=1.0.3+83de8fe`.
