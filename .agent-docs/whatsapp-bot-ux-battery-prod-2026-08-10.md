# Batería UX bot WhatsApp en prod — 2026-08-10

Cliente: `51982014925` (Saúl León Laguna)  
Canal: Safari (WhatsApp Web) + CRM (`dscorp`)  
Release: `1.0.3+f7f6fae`  
Horario de prueba: fuera de horario laboral (Lima).

## Resultado

| ID | Escenario | Resultado | Evidencia |
|----|-----------|----------|-----------|
| S0 | MENU | PASS | `Seleccione una opcion para continuar` + footer `Puede escribir MENU o ASESOR…` |
| S1 | Consultar deuda | PASS | `Estado de su cuenta` + cierre CCI/BCP/Yape + `envienos el comprobante` (usted) |
| S2 | Menú principal | PASS | Reenvía menú sin saludo tú |
| S3 | Registrar pago | PASS | `Para registrar su pago, envienos el comprobante…` / `Cuando lo recibamos…` |
| S4 | Texto basura en espera de voucher | PASS | `Aun no hemos recibido su comprobante…` |
| S5 | Enviar imagen comprobante | PASS | Safari: Adjuntar → Documento → File/DataTransfer (`voucher-test.jpg`) → Enviar. Bot: `Recibimos su comprobante. Gracias.` + after-hours + horario |
| S6 | Soft-ack post-voucher after-hours | PASS | Tras `ok` (9:14 p.m.) no hubo segundo soft-ack |
| S7 | Soporte / diagnóstico | PASS | `Seleccione el problema…` + `Por favor revise su modem…` (usted) |
| S8 | Cierre diagnóstico | PASS | **`Su caso quedo registrado`** + after-hours natural + horario en oración |
| S9 | ASESOR (menú/texto) | PASS | **`Su solicitud quedo registrada`** (no caso) + after-hours natural |
| S10 | ACK `gracias` after-hours puro | PARCIAL | Con menú pendiente reenvía menú (esperado por FSM); ACK corto after-hours no ejercitado sin voucher |
| S11 | Menú pasado / Ver opciones | PASS | Listas antiguas/recientes abren y aceptan selección |
| S12 | Comandos texto MENU/ASESOR/averia | PASS | Navegación coherente |

## Hallazgo clave de coherencia

- Menú asesor → **solicitud**
- Cierre soporte → **caso**
Confirmado en prod.

## Notas operativas

- Hubo silencio por reply de operador (8:21); para la batería se bajó temporalmente `WHATSAPP_AUTO_REPLY_OPERATOR_SILENCE_MINUTES=1` y luego se restauró a `45` con redeploy WAR.
- Cursor browser no sirve para login WA; se usó Safari + JS Apple Events.
- Adjunto de comprobante: flujo `Adjuntar` → `Documento` → inyectar `File` en `input[type=file]` → `Enviar`.
