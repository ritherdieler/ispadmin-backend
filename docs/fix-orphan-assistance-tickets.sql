-- Limpia tickets de asistencia que apuntan a suscripciones inexistentes (dev/piloto).
-- Ejecutar contra ispadmin_dev antes del piloto del Mapa Inteligente.

-- Diagnostico
SELECT t.id, t.status, t.subscription_id, t.category, t.created_at
FROM assistance_ticket t
LEFT JOIN subscription s ON s.id = t.subscription_id
WHERE t.subscription_id IS NOT NULL
  AND s.id IS NULL;

-- Opcion A: anular referencia huerfana (conserva el ticket)
UPDATE assistance_ticket t
LEFT JOIN subscription s ON s.id = t.subscription_id
SET t.subscription_id = NULL
WHERE t.subscription_id IS NOT NULL
  AND s.id IS NULL;
