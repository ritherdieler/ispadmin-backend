-- One-shot: unique fiber_onu_sn for the 7 VSOL pairs from the VLAN 1000 lote.
-- Keeper = OLT olt_mgr_onu.name (or the remaining ACTIVE when the other is CANCELLED).
-- Does not delete ONU on OLT, queues, or IPs.

START TRANSACTION;

UPDATE ispadmin.subscription SET fiber_onu_sn = NULL WHERE id IN (
  1783,
  1852,
  1883,
  1977,
  1808,
  1870,
  1847
);

UPDATE ispadmin.identity_link
SET valid_to = UTC_TIMESTAMP()
WHERE valid_to IS NULL
  AND kind = 'ONU'
  AND subscription_id IN (1783, 1852, 1883, 1977, 1808, 1870, 1847);

UPDATE ispadmin.service_identity_conflict
SET
  status = 'RESOLVED',
  resolved_at = UTC_TIMESTAMP(),
  resolution = CASE id
    WHEN 8 THEN 'SN VSOL0086F109 kept on #1982 (ACTIVE + OLT Kety); released #1783 CANCELLED'
    WHEN 9 THEN 'SN HWTC15F5C4F6 kept on #2011 (ACTIVE + OLT Melquiades); released #1852 CANCELLED'
    WHEN 11 THEN 'SN HWTC15F5D5F6 kept on #2219 (ACTIVE + OLT Alejandrina); released #1883 CANCELLED'
    WHEN 10 THEN 'SN HWTC15F5F986 kept on #1912 ACTIVE Basilio; released #1977 CANCELLED (OLT name still Yoseph)'
    WHEN 26 THEN 'SN VSOL0086D819 kept on #2070 (OLT Martha); released #1808 ACTIVE Herlinda'
    WHEN 23 THEN 'SN HWTC15F61FA6 kept on #2137 (OLT Deysi); released #1870 ACTIVE Alisson'
    WHEN 3 THEN 'SN HWTC15F62436 kept on #650 (OLT Juan Bartolo); released #1847 ACTIVE Nahin'
    ELSE resolution
  END
WHERE id IN (3, 8, 9, 10, 11, 23, 26)
  AND status = 'OPEN';

COMMIT;
