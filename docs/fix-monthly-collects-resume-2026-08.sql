-- Corrige filas rotas de monthly_collects_resume (dashboard Android "Recaudacion por mes").
-- Ciclo por mes M (date = ultimo dia de M): [primer_dia(M)-1 dia, primer_dia(M+1)-1 dia) en billing_date_datetime.
-- Mismas reglas que PaymentRepository / DashBoardService (solo billing_date_datetime).
--
-- Filas: UPDATE id 73 (jun 2026), 74 (jul 2026); DELETE duplicado id 71 (abril; conservar 72).
-- Ejecutar contra BD ispadmin en prod (tunel scripts/db-tunnel.sh). Revisar preflight antes de COMMIT.

USE ispadmin;

SELECT 'preflight' AS step,
       id,
       date,
       gross_income,
       total_raised,
       total_discount,
       total_receivables,
       ROUND(total_raised * 100 / NULLIF(gross_income, 0), 1) AS raised_pct,
       ROUND(total_discount * 100 / NULLIF(gross_income, 0), 1) AS discount_pct,
       ROUND(total_receivables * 100 / NULLIF(gross_income, 0), 1) AS receivables_pct
FROM monthly_collects_resume
WHERE id IN (71, 72, 73, 74)
ORDER BY id;

START TRANSACTION;

UPDATE monthly_collects_resume m
JOIN (
    SELECT
        COALESCE(SUM(p.amount_to_pay), 0) AS gross_income,
        COALESCE(SUM(CASE WHEN p.paid = TRUE THEN p.amount_paid ELSE 0 END), 0) AS total_raised,
        COALESCE(SUM(CASE WHEN p.paid = TRUE THEN p.discount_amount ELSE 0 END), 0) AS total_discount,
        COALESCE(SUM(CASE WHEN p.paid = FALSE THEN p.amount_to_pay ELSE 0 END), 0) AS total_receivables
    FROM payment p
    WHERE p.billing_date_datetime >= '2026-05-31 00:00:00'
      AND p.billing_date_datetime < '2026-06-30 00:00:00'
) AS calc
SET m.gross_income = calc.gross_income,
    m.total_raised = calc.total_raised,
    m.total_discount = calc.total_discount,
    m.total_receivables = calc.total_receivables
WHERE m.id = 73;

UPDATE monthly_collects_resume m
JOIN (
    SELECT
        COALESCE(SUM(p.amount_to_pay), 0) AS gross_income,
        COALESCE(SUM(CASE WHEN p.paid = TRUE THEN p.amount_paid ELSE 0 END), 0) AS total_raised,
        COALESCE(SUM(CASE WHEN p.paid = TRUE THEN p.discount_amount ELSE 0 END), 0) AS total_discount,
        COALESCE(SUM(CASE WHEN p.paid = FALSE THEN p.amount_to_pay ELSE 0 END), 0) AS total_receivables
    FROM payment p
    WHERE p.billing_date_datetime >= '2026-06-30 00:00:00'
      AND p.billing_date_datetime < '2026-07-31 00:00:00'
) AS calc
SET m.gross_income = calc.gross_income,
    m.total_raised = calc.total_raised,
    m.total_discount = calc.total_discount,
    m.total_receivables = calc.total_receivables
WHERE m.id = 74;

DELETE FROM monthly_collects_resume WHERE id = 71;

SELECT 'postflight' AS step,
       id,
       date,
       gross_income,
       total_raised,
       total_discount,
       total_receivables,
       ROUND(total_raised * 100 / NULLIF(gross_income, 0), 1) AS raised_pct,
       ROUND(total_discount * 100 / NULLIF(gross_income, 0), 1) AS discount_pct,
       ROUND(total_receivables * 100 / NULLIF(gross_income, 0), 1) AS receivables_pct
FROM monthly_collects_resume
WHERE id IN (72, 73, 74)
ORDER BY id;

SELECT 'april_duplicates' AS step, date, COUNT(*) AS cnt
FROM monthly_collects_resume
WHERE date = '2026-04-30 00:00:00'
GROUP BY date;

-- Si postflight OK (porcentajes 0-100, un solo abril): COMMIT;
-- Si algo falla: ROLLBACK;
