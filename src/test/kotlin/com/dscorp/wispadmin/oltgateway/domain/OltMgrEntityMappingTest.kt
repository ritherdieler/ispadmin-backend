package com.dscorp.wispadmin.oltgateway.domain

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrAuditLog
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrCustomTemplate
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltPonPort
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltVlan
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuExtraVlan
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuServicePort
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuType
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrSpeedProfile
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrSyncRun
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrTask
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import javax.persistence.Table

class OltMgrEntityMappingTest {

    @ParameterizedTest
    @CsvSource(
        "com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt, olt_mgr_olt",
        "com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltModel, olt_mgr_olt_model",
        "com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrZone, olt_mgr_zone",
        "com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuType, olt_mgr_onu_type",
        "com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu, olt_mgr_onu",
        "com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent, olt_mgr_onu_status_current",
        "com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrTask, olt_mgr_task",
        "com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrAuditLog, olt_mgr_audit_log",
        "com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuServicePort, olt_mgr_onu_service_port",
        "com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuExtraVlan, olt_mgr_onu_extra_vlan",
        "com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrCustomTemplate, olt_mgr_custom_template",
        "com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrSpeedProfile, olt_mgr_speed_profile",
        "com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltPonPort, olt_mgr_olt_pon_port",
        "com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltVlan, olt_mgr_olt_vlan",
        "com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrSyncRun, olt_mgr_sync_run"
    )
    fun `entity maps to olt_mgr table`(className: String, expectedTable: String) {
        val clazz = Class.forName(className)
        val table = clazz.getAnnotation(Table::class.java)
        assertEquals(expectedTable, table.name)
    }

    @Test
    fun `core entities are loadable`() {
        assertEquals("olt_mgr_onu", OltMgrOnu::class.java.getAnnotation(Table::class.java).name)
        assertEquals("olt_mgr_task", OltMgrTask::class.java.getAnnotation(Table::class.java).name)
        assertEquals("olt_mgr_audit_log", OltMgrAuditLog::class.java.getAnnotation(Table::class.java).name)
        assertEquals("olt_mgr_onu_status_current", OltMgrOnuStatusCurrent::class.java.getAnnotation(Table::class.java).name)
        assertEquals("olt_mgr_olt", OltMgrOlt::class.java.getAnnotation(Table::class.java).name)
        assertEquals("olt_mgr_zone", OltMgrZone::class.java.getAnnotation(Table::class.java).name)
        assertEquals("olt_mgr_onu_type", OltMgrOnuType::class.java.getAnnotation(Table::class.java).name)
        assertEquals("olt_mgr_onu_service_port", OltMgrOnuServicePort::class.java.getAnnotation(Table::class.java).name)
        assertEquals("olt_mgr_onu_extra_vlan", OltMgrOnuExtraVlan::class.java.getAnnotation(Table::class.java).name)
        assertEquals("olt_mgr_custom_template", OltMgrCustomTemplate::class.java.getAnnotation(Table::class.java).name)
        assertEquals("olt_mgr_speed_profile", OltMgrSpeedProfile::class.java.getAnnotation(Table::class.java).name)
        assertEquals("olt_mgr_olt_pon_port", OltMgrOltPonPort::class.java.getAnnotation(Table::class.java).name)
        assertEquals("olt_mgr_olt_vlan", OltMgrOltVlan::class.java.getAnnotation(Table::class.java).name)
        assertEquals("olt_mgr_sync_run", OltMgrSyncRun::class.java.getAnnotation(Table::class.java).name)
    }
}
