# Fixture E2E Mikrotik MK1 + Android

## Identificadores fijos (ispadmin_dev)

| Entidad | ID / valor |
|---------|------------|
| Usuario app | `labmk1` (ADMIN, misma contraseña hasheada que `dscorp` tras seed → usar `nohacker` en dev) |
| Suscripción | `900001` |
| Suscripción (cancel/reactivate) | `900002` / IP `192.168.250.2` |
| Pago pendiente | `900001` |
| IP en MK1 lista `deudores` | `192.168.250.1` |
| DNI | `900001001` |
| Router | `network_device.id = 1` |

## Reset (BD + MK1)

```bash
cp scripts/lab/mk1-e2e-lab.env.example scripts/lab/mk1-e2e-lab.env
# Editar MYSQL_PASSWORD y ADMIN_PASS

export MYSQL_PASSWORD='…' ADMIN_PASS='nohacker'
./scripts/mk1-lab-reset.sh
```

Hace: `scripts/lab/mk1-e2e-seed.sql`, quita/recrea entrada `deudores` en MK1 (REST PUT), valida `GET /subscription/900001`.

Flujos API extra (cancel, reactivate, plan, ip-pool, pago lab):

```bash
ISP_PASS=nohacker ./scripts/mk1-lab-all-flows.sh
```

Masivos MK1 (cortarDeudores, address-list cancelados, simple-queues):

```bash
ISP_PASS=nohacker RUN_MASS=true RUN_PAYMENT=false ./scripts/mk1-mikrotik-e2e-full.sh
```

La suscripción lab incluye `location` JSON y `equipment_condition=LOAN`. Si `location` es NULL, Hibernate falla al leer (corregido en `GeoLocationConverter` para valores null/blank).

## Android CLI E2E

Requisitos: emulador/dispositivo, backend en `127.0.0.1:8080`, `./gradlew :presentation:assembleDevDebug`.

```bash
cd "../IpsAdmin-android app"
export MYSQL_PASSWORD='…' ADMIN_PASS='nohacker' SPRING_DATASOURCE_PASSWORD='…'
chmod +x scripts/e2e_mk1_payment_lab.sh
./scripts/e2e_mk1_payment_lab.sh
```

Journey (referencia): `scripts/journeys/mk1-payment-lab.journey.xml`.

El script usa test tags donde `android layout` los expone; en menú contextual y selector de método de pago usa tap por texto (`Mostrar historial de pagos`, `Seleccionar método`, `Efectivo`).

Salida esperada: `E2E_MK1_PAYMENT_LAB_OK sub=900001 pay=900001` y `PASS: 192.168.250.1 not in deudores`.

## Repetir prueba

Ejecutar de nuevo `./scripts/mk1-lab-reset.sh` antes de cada corrida (marca pago como impagado y vuelve a poner la IP en `deudores`).

## Credenciales

No commitear `mk1-e2e-lab.env`. En dev, `labmk1` / `nohacker` es equivalente al hash copiado de `dscorp` en el seed SQL.
