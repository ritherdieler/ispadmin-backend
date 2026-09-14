package com.dscorp.wispadmin.wispadmin.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class PrimaryDataSourceConfigTest {

    @Test
    fun businessDataSourceIsPrimarySoJpaAutoConfigKeepsASingleCandidate() {
        val method = PrimaryDataSourceConfig::class.java.getDeclaredMethod(
            "dataSource",
            DataSourceProperties::class.java,
        )
        assertNotNull(method.getAnnotation(Primary::class.java))
        val bean = method.getAnnotation(Bean::class.java)
        assertNotNull(bean)
        assertEquals("dataSource", bean.name.single())
    }
}
