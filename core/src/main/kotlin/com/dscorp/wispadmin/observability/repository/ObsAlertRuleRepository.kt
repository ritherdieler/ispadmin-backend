package com.dscorp.wispadmin.observability.repository

import com.dscorp.wispadmin.observability.entity.ObsAlertRule
import com.dscorp.wispadmin.observability.entity.ObsAlertType
import org.springframework.data.jpa.repository.JpaRepository

interface ObsAlertRuleRepository : JpaRepository<ObsAlertRule, Long> {

    fun findByEnabledTrue(): List<ObsAlertRule>

    fun findByEnabledTrueAndType(type: ObsAlertType): List<ObsAlertRule>

    fun findAllByOrderByCreatedAtDesc(): List<ObsAlertRule>
}
