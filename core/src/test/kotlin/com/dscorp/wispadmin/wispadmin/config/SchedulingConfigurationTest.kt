package com.dscorp.wispadmin.wispadmin.config

import com.dscorp.wispadmin.wispadmin.WispAdminApplication
import com.dscorp.wispadmin.servicehealth.service.HealthSnapshotConsumer
import com.dscorp.wispadmin.wispadmin.scheduled.CutServiceMonthlyTaskScheduler
import com.dscorp.wispadmin.wispadmin.scheduled.MonthlyBillingCloseScheduler
import com.dscorp.wispadmin.wispadmin.service.CpeProvisioningEventConsumer
import com.dscorp.wispadmin.wispadmin.service.subscription.AccessMigrationQuarantineJob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.SmartLifecycle
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

class SchedulingConfigurationTest {

    @Test
    fun scheduling_is_opt_in_via_gigafiber_flag_and_not_on_the_application_class() {
        val conditional = SchedulingConfiguration::class.java.getAnnotation(ConditionalOnProperty::class.java)
        assertNotNull(conditional)
        assertEquals("gigafiber.scheduling.enabled", conditional!!.name[0])
        assertEquals("true", conditional.havingValue)
        assertTrue(conditional.matchIfMissing)
        assertNotNull(SchedulingConfiguration::class.java.getAnnotation(EnableScheduling::class.java))
        assertNull(WispAdminApplication::class.java.getAnnotation(EnableScheduling::class.java))
    }

    @Test
    fun operational_jobs_are_opt_in_so_staging_can_run_360_schedulers() {
        listOf(MonthlyBillingCloseScheduler::class.java, CutServiceMonthlyTaskScheduler::class.java).forEach { type ->
            val conditional = type.getAnnotation(ConditionalOnProperty::class.java)
            assertNotNull(conditional, type.simpleName)
            assertEquals("gigafiber.scheduling.operational-jobs", conditional!!.name[0], type.simpleName)
            assertEquals("true", conditional.havingValue, type.simpleName)
            assertTrue(conditional.matchIfMissing, type.simpleName)
        }
        val scheduled = AccessMigrationQuarantineJob::class.java.getDeclaredMethod("scheduledFinishDueQuarantines")
        assertNotNull(scheduled.getAnnotation(Scheduled::class.java))
        assertNull(AccessMigrationQuarantineJob::class.java.getDeclaredMethod("finishDueQuarantines").getAnnotation(Scheduled::class.java))
    }

    @Test
    fun redis_stream_consumers_run_without_global_scheduling() {
        val runtime = RedisStreamConsumerRuntime::class.java
        val redisGate = runtime.getAnnotation(ConditionalOnProperty::class.java)
        assertNotNull(redisGate)
        assertEquals("gigafiber.redis", redisGate!!.prefix)
        assertEquals("enabled", redisGate.name[0])
        assertEquals("true", redisGate.havingValue)
        assertTrue(SmartLifecycle::class.java.isAssignableFrom(runtime))
        assertNull(HealthSnapshotConsumer::class.java.getDeclaredMethod("poll").getAnnotation(Scheduled::class.java))
        assertNull(CpeProvisioningEventConsumer::class.java.getDeclaredMethod("poll").getAnnotation(Scheduled::class.java))
    }
}
