package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.CrmEventLog
import org.springframework.data.jpa.repository.JpaRepository

interface CrmEventLogRepository : JpaRepository<CrmEventLog, Long> {
    fun findByIdGreaterThanOrderByIdAsc(id: Long): List<CrmEventLog>
}
