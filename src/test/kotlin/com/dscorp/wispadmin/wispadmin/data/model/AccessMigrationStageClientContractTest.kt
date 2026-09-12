package com.dscorp.wispadmin.wispadmin.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccessMigrationStageClientContractTest {

    @Test
    fun quarantineIsCompleteForTheTechnicianClientButNotABackendTerminal() {
        assertTrue(AccessMigrationStage.QUARANTINE.isClientComplete())
        assertFalse(AccessMigrationStage.QUARANTINE.isTerminal())
        assertTrue(AccessMigrationStage.QUARANTINE.isActive())
        assertEquals("En cuarentena", AccessMigrationStage.QUARANTINE.toClientMessage())
    }

    @Test
    fun doneAndFailuresAreCompleteForBothClientAndBackend() {
        listOf(
            AccessMigrationStage.DONE to "Migración completada",
            AccessMigrationStage.FAILED_REVERTED to "Falló y se revirtió",
            AccessMigrationStage.FAILED_STRANDED to "Falló y el CPE no responde",
        ).forEach { (stage, message) ->
            assertTrue(stage.isClientComplete(), stage.name)
            assertTrue(stage.isTerminal(), stage.name)
            assertFalse(stage.isActive(), stage.name)
            assertEquals(message, stage.toClientMessage())
        }
    }

    @Test
    fun inFlightStagesAreNotClientComplete() {
        listOf(
            AccessMigrationStage.ELIGIBLE,
            AccessMigrationStage.OLT_READY,
            AccessMigrationStage.SECRET_READY,
            AccessMigrationStage.CPE_APPLIED,
            AccessMigrationStage.VERIFIED,
            AccessMigrationStage.QUEUE_CLEARED,
        ).forEach { stage ->
            assertFalse(stage.isClientComplete(), stage.name)
            assertTrue(stage.isActive(), stage.name)
        }
    }
}
