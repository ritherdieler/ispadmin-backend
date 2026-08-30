#!/usr/bin/env python3
"""Exercise V35 only, twice, in a disposable MySQL container without network/host ports."""
import subprocess
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NAME = 'gigafiber-health-test-' + uuid.uuid4().hex[:10]


def run(*args, text=None, check=True):
    return subprocess.run(args, input=text, text=True, capture_output=True, check=check, timeout=60)


def sql(statement):
    result = run('docker', 'exec', '-i', NAME, 'mysql', '-uroot', '-N', '-B', 'servicehealth', text=statement, check=False)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout.strip()


def main():
    try:
        run('docker', 'run', '-d', '--name', NAME, '--network', 'none', '--tmpfs', '/var/lib/mysql',
            '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes', '-e', 'MYSQL_DATABASE=servicehealth', 'mysql:8')
        for _ in range(45):
            result = run('docker', 'exec', NAME, 'mysql', '--protocol=TCP', '-h127.0.0.1', '-uroot', 'servicehealth', '-e', 'SELECT 1', check=False)
            if result.returncode == 0:
                break
            time.sleep(1)
        if result.returncode != 0:
            raise RuntimeError('MySQL fixture did not initialize: ' + result.stderr)
        migration = ROOT.joinpath('src/main/resources/db/migration/V35__convergent_service_health.sql').read_text()
        sql(migration)
        sql(migration)
        assert sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='servicehealth'") == '16'
        assert sql('SELECT COUNT(*) FROM service_health_cursor') == '6'
        sql("""INSERT INTO acs_wifi_count_sample
            (subscription_id,device_id,inform_at,collected_at,quality_status)
            VALUES (1,'fixture-device','2026-08-30 12:00:00','2026-08-30 12:00:01','MISSING');""")
        duplicate = run('docker', 'exec', '-i', NAME, 'mysql', '-uroot', 'servicehealth', text="""
            INSERT INTO acs_wifi_count_sample(subscription_id,device_id,inform_at,collected_at,quality_status)
            VALUES(1,'fixture-device','2026-08-30 12:00:00','2026-08-30 12:00:02','FRESH');""", check=False)
        assert duplicate.returncode != 0 and 'Duplicate entry' in duplicate.stderr
        sql("UPDATE acs_wifi_count_sample SET associated_device_count=0,observed_at='2026-08-30 12:00:02',quality_status='FRESH' WHERE subscription_id=1")
        assert sql('SELECT COUNT(*),SUM(associated_device_count),SUM(lan_device_count IS NULL) FROM acs_wifi_count_sample') == '1\t0\t1'
        sql("""INSERT INTO service_traffic_evidence(event_id,subscription_id,router_id,event_status,anomaly_type,observed_at,coverage_pct,confidence)
            VALUES(1,1,10,'OPEN','TRAFFIC_SPIKE','2026-08-30 12:00:00',95,0.8);
            UPDATE service_traffic_evidence SET event_status='CLOSED',observed_at='2026-08-30 12:01:00' WHERE event_id=1;
            UPDATE service_traffic_evidence SET event_status='OPEN',observed_at='2026-08-30 12:02:00' WHERE event_id=1;""")
        assert sql("SELECT COUNT(*),MAX(event_status),MAX(anomaly_type) FROM service_traffic_evidence") == '1\tOPEN\tTRAFFIC_SPIKE'
        print('PASS: V35 applied twice; 16 tables; 6 coordinators; reading uniqueness; partial completion; null semantics; mutable traffic evidence')
    finally:
        run('docker', 'rm', '-f', NAME, check=False)


if __name__ == '__main__':
    main()
