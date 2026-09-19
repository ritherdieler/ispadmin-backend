-- One-shot: one occupied subscription per ONU suffix in Core.
-- Keepers = olt_mgr_onu.name except TPLG31B241F0 (serial fantasma: quitar SN a ambas ACTIVE).
-- Also drop CANCELLED rows that still hold a SN used by ACTIVE/CUT_OFF/SUSPENDED.
-- Does not delete ONU on OLT, queues, or IPs.

START TRANSACTION;

UPDATE ispadmin.identity_link il
INNER JOIN ispadmin.subscription s ON s.id = il.subscription_id
SET il.valid_to = UTC_TIMESTAMP()
WHERE il.valid_to IS NULL
  AND il.kind = 'ONU'
  AND (
    s.id IN (1, 1679, 1801, 1695, 1815, 1837, 1458, 1459)
    OR (
      s.service_status = 'CANCELLED'
      AND s.fiber_onu_sn IS NOT NULL
      AND s.fiber_onu_sn <> ''
      AND EXISTS (
        SELECT 1
        FROM ispadmin.subscription a
        WHERE a.id <> s.id
          AND a.service_status IN ('ACTIVE', 'CUT_OFF', 'SUSPENDED')
          AND a.fiber_onu_sn IS NOT NULL
          AND a.fiber_onu_sn <> ''
          AND RIGHT(UPPER(s.fiber_onu_sn), 6) = RIGHT(UPPER(a.fiber_onu_sn), 6)
      )
    )
  );

UPDATE ispadmin.subscription
SET fiber_onu_sn = NULL, tr069_device_id = NULL
WHERE id IN (1, 1679, 1801, 1695, 1815, 1837, 1458, 1459);

UPDATE ispadmin.subscription c
INNER JOIN ispadmin.subscription a
  ON a.id <> c.id
 AND a.service_status IN ('ACTIVE', 'CUT_OFF', 'SUSPENDED')
 AND a.fiber_onu_sn IS NOT NULL
 AND a.fiber_onu_sn <> ''
 AND c.fiber_onu_sn IS NOT NULL
 AND c.fiber_onu_sn <> ''
 AND RIGHT(UPPER(c.fiber_onu_sn), 6) = RIGHT(UPPER(a.fiber_onu_sn), 6)
SET c.fiber_onu_sn = NULL, c.tr069_device_id = NULL
WHERE c.service_status = 'CANCELLED';

UPDATE ispadmin.service_identity_conflict
SET
  status = 'RESOLVED',
  resolved_at = UTC_TIMESTAMP(),
  resolution = CASE id
    WHEN 1 THEN 'SN HWTC15F5F5A6 kept on #1846 (OLT Edith); released #1837 ACTIVE Delia'
    WHEN 2 THEN 'SN HWTC15F5FBB6 kept on #2259 ACTIVE; released #1477 CANCELLED'
    WHEN 4 THEN 'SN HWTC15F60F66 kept on #1089 ACTIVE; released #1900 CANCELLED'
    WHEN 5 THEN 'SN VSOL0086DE39 kept on #2214 ACTIVE; released #1806 CANCELLED'
    WHEN 6 THEN 'SN VSOL0086B1E9 kept on #2355 ACTIVE; released #1764 CANCELLED'
    WHEN 7 THEN 'SN VSOL00871AC9 kept on #1816 (OLT Zenon); released #1815 ACTIVE Fundo Pamajosa'
    WHEN 12 THEN 'SN TPLG21380C20 kept on #2165 (OLT Liseth); released #1801 ACTIVE Yanet and #1664 CANCELLED'
    WHEN 13 THEN 'SN TPLGE6EC8818 kept on #1198 (OLT Basilio); released #1 ACTIVE Tomasa and #1440 CANCELLED'
    WHEN 14 THEN 'SN TPLG2137FA68 kept on #1305 (OLT Agricola); released #1679 ACTIVE Roberto'
    WHEN 15 THEN 'SN HWTC15F5B516 kept on #1981 ACTIVE; released #1887 CANCELLED'
    WHEN 16 THEN 'SN HWTC15F5DF86 kept on #1729 ACTIVE; released #1913 CANCELLED'
    WHEN 17 THEN 'SN TPLG8B982518 kept on #1253 ACTIVE; released #1272 CANCELLED'
    WHEN 19 THEN 'SN HWTC15F5B776 kept on #1922 ACTIVE; released #1853 CANCELLED'
    WHEN 21 THEN 'SN VSOL00872799 kept on #1964 ACTIVE; released #1791 CANCELLED'
    WHEN 22 THEN 'SN HWTC15F5F656 kept on #1939 ACTIVE; released #1938 CANCELLED'
    WHEN 24 THEN 'SN HWTC15F62346 kept on #2206 ACTIVE; released #1860 CANCELLED'
    WHEN 25 THEN 'SN HWTC15F5DD16 kept on #2108 ACTIVE; released #1874 CANCELLED'
    WHEN 27 THEN 'SN TPLG4A3BF818 kept on #2085 ACTIVE; released #1561 CANCELLED'
    WHEN 28 THEN 'SN HWTC15F5D926 kept on #744 ACTIVE; released #2072 CANCELLED'
    WHEN 29 THEN 'SN HWTCC6F996AA kept on #1428 ACTIVE; released #1423 CANCELLED'
    WHEN 30 THEN 'SN TPLG54740DD0 kept on #1769 ACTIVE; released #1244 CANCELLED'
    WHEN 32 THEN 'SN HWTC15F5D9A6 kept on #2208 ACTIVE; released #1921 CANCELLED'
    ELSE resolution
  END
WHERE id IN (1, 2, 4, 5, 6, 7, 12, 13, 14, 15, 16, 17, 19, 21, 22, 24, 25, 27, 28, 29, 30, 32)
  AND status = 'OPEN';

COMMIT;
