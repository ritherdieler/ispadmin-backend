package com.dscorp.wispadmin.oltgateway.domain

import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOlt
import com.dscorp.wispadmin.oltgateway.domain.entity.OltMgrOltModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import javax.persistence.FetchType
import javax.persistence.ManyToOne
import javax.persistence.Table

class OltMgrOltModelMappingTest {

    @Test
    fun `model entity maps to olt_mgr_olt_model`() {
        assertEquals(
            "olt_mgr_olt_model",
            OltMgrOltModel::class.java.getAnnotation(Table::class.java).name
        )
    }

    @Test
    fun `olt has lazy many-to-one to model`() {
        val field = OltMgrOlt::class.java.getDeclaredField("model")
        val manyToOne = field.getAnnotation(ManyToOne::class.java)
        assertNotNull(manyToOne)
        assertEquals(FetchType.LAZY, manyToOne.fetch)
        assertEquals(true, manyToOne.optional)
    }
}
