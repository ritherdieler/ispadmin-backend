package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import javax.sql.DataSource

internal object ProvisioningTestSchema {
    fun initialize(dataSource: DataSource) {
        ResourceDatabasePopulator(ClassPathResource("db/migration/V56__provisioning_v2_journal.sql")).execute(dataSource)
        val jdbc = JdbcTemplate(dataSource)
        jdbc.execute("ALTER TABLE provisioning_v2_operation ALTER COLUMN subscription_id DROP NOT NULL")
        jdbc.execute("ALTER TABLE provisioning_v2_operation ADD COLUMN phase VARCHAR(40) NOT NULL DEFAULT 'PROVISIONING'")
        jdbc.execute("ALTER TABLE provisioning_v2_operation ADD COLUMN operator_id BIGINT NULL")
        jdbc.execute("ALTER TABLE provisioning_v2_operation ADD COLUMN operator_username VARCHAR(128) NULL")
        jdbc.execute("ALTER TABLE provisioning_v2_operation ADD COLUMN registration_request_key VARCHAR(64) NULL")
        jdbc.execute("CREATE INDEX idx_provisioning_v2_phase_due ON provisioning_v2_operation (phase, state, next_attempt_at, lease_until)")
        jdbc.execute("CREATE INDEX idx_provisioning_v2_operator ON provisioning_v2_operation (environment, operator_id, state)")
        jdbc.execute("CREATE UNIQUE INDEX uk_provisioning_v2_operator_request ON provisioning_v2_operation (environment, operator_id, registration_request_key)")
        jdbc.execute("""CREATE TABLE provisioning_v2_operator_lock (
            operator_id BIGINT NOT NULL PRIMARY KEY,
            environment VARCHAR(32) NOT NULL,
            operation_id VARCHAR(64) NOT NULL UNIQUE,
            created_at_epoch_ms BIGINT NOT NULL
        )""")
        jdbc.execute("CREATE INDEX idx_provisioning_v2_operator_lock_operation ON provisioning_v2_operator_lock (environment, operation_id)")
    }
}
