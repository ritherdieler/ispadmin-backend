package com.dscorp.wispadmin.wispadmin.repository

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AccessMigrationCandidatesQueryTest {

    @Test
    fun candidatesQueryComparesEnumsWithJpqlLiteralsNotStrings() {
        val source = Files.readString(
            Path.of(System.getProperty("user.dir"))
                .resolve("src/main/kotlin/com/dscorp/wispadmin/wispadmin/repository/SubscriptionRepository.kt"),
        )
        val query = source.substringAfter("fun findAccessMigrationCandidates()")
            .let { source.substring(0, source.indexOf("fun findAccessMigrationCandidates()") + it.length) }
            .let {
                val start = source.indexOf("fun findAccessMigrationCandidates()")
                val queryStart = source.lastIndexOf("@Query", start)
                val nextFun = source.indexOf("fun findForTrafficPolling()")
                source.substring(queryStart, nextFun)
            }
        assertTrue(
            query.contains("s.installationType = com.dscorp.wispadmin.wispadmin.data.model.InstallationType.FIBER"),
            query,
        )
        assertTrue(
            query.contains("s.accessMode = com.dscorp.wispadmin.wispadmin.data.model.AccessMode.STATIC_IP"),
            query,
        )
        assertTrue(
            query.contains("s.serviceStatus = com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus.ACTIVE"),
            query,
        )
        assertFalse(query.contains("= 'FIBER'"), query)
        assertFalse(query.contains("= 'STATIC_IP'"), query)
        assertFalse(query.contains("= 'ACTIVE'"), query)
    }
}
