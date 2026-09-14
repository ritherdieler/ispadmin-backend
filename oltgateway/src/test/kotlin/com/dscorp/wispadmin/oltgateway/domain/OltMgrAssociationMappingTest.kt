package com.dscorp.wispadmin.oltgateway.domain

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrAuditLog
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltPonPort
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltVlan
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnu
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuExtraVlan
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuServicePort
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOnuStatusCurrent
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrTask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.persistence.FetchType
import javax.persistence.ManyToOne
import javax.persistence.MapsId
import javax.persistence.OneToOne

class OltMgrAssociationMappingTest {

    @Test
    fun `onu has lazy many-to-one to olt zone onuType and customTemplate`() {
        assertLazyManyToOne(OltMgrOnu::class.java, "olt", optional = false)
        assertLazyManyToOne(OltMgrOnu::class.java, "zone", optional = true)
        assertLazyManyToOne(OltMgrOnu::class.java, "onuType", optional = true)
        assertLazyManyToOne(OltMgrOnu::class.java, "customTemplate", optional = true)
        assertFalse(hasField(OltMgrOnu::class.java, "oltId"))
        assertFalse(hasField(OltMgrOnu::class.java, "zoneId"))
        assertFalse(hasField(OltMgrOnu::class.java, "onuTypeId"))
        assertFalse(hasField(OltMgrOnu::class.java, "customTemplateId"))
    }

    @Test
    fun `status uses one-to-one mapsId to onu`() {
        val onuField = OltMgrOnuStatusCurrent::class.java.getDeclaredField("onu")
        val oneToOne = onuField.getAnnotation(OneToOne::class.java)
        assertNotNull(oneToOne)
        assertEquals(FetchType.LAZY, oneToOne.fetch)
        assertNotNull(onuField.getAnnotation(MapsId::class.java))

        val statusField = OltMgrOnu::class.java.getDeclaredField("status")
        val mappedBy = statusField.getAnnotation(OneToOne::class.java)
        assertNotNull(mappedBy)
        assertEquals("onu", mappedBy.mappedBy)
        assertEquals(FetchType.LAZY, mappedBy.fetch)
    }

    @Test
    fun `task and audit have lazy many-to-one to olt and onu`() {
        assertLazyManyToOne(OltMgrTask::class.java, "olt", optional = true)
        assertLazyManyToOne(OltMgrTask::class.java, "onu", optional = true)
        assertLazyManyToOne(OltMgrAuditLog::class.java, "olt", optional = true)
        assertLazyManyToOne(OltMgrAuditLog::class.java, "onu", optional = true)
        assertFalse(hasField(OltMgrTask::class.java, "oltId"))
        assertFalse(hasField(OltMgrTask::class.java, "onuId"))
        assertFalse(hasField(OltMgrAuditLog::class.java, "oltId"))
        assertFalse(hasField(OltMgrAuditLog::class.java, "onuId"))
    }

    @Test
    fun `stubs have lazy many-to-one associations`() {
        assertLazyManyToOne(OltMgrOnuServicePort::class.java, "onu", optional = false)
        assertLazyManyToOne(OltMgrOnuServicePort::class.java, "downloadSpeed", optional = true)
        assertLazyManyToOne(OltMgrOnuServicePort::class.java, "uploadSpeed", optional = true)
        assertLazyManyToOne(OltMgrOnuExtraVlan::class.java, "onu", optional = false)
        assertLazyManyToOne(OltMgrOltPonPort::class.java, "olt", optional = false)
        assertLazyManyToOne(OltMgrOltVlan::class.java, "olt", optional = false)
    }

    private fun assertLazyManyToOne(type: Class<*>, fieldName: String, optional: Boolean) {
        val field = type.getDeclaredField(fieldName)
        val manyToOne = field.getAnnotation(ManyToOne::class.java)
        assertNotNull(manyToOne, "Missing @ManyToOne on ${type.simpleName}.$fieldName")
        assertEquals(FetchType.LAZY, manyToOne.fetch)
        assertEquals(optional, manyToOne.optional)
    }

    private fun hasField(type: Class<*>, name: String): Boolean {
        return type.declaredFields.any { it.name == name }
    }
}
