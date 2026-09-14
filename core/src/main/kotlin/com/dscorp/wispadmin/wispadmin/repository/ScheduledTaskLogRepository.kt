package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.ScheduledTaskLog
import com.dscorp.wispadmin.wispadmin.data.model.ScheduledTaskType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface ScheduledTaskLogRepository : JpaRepository<ScheduledTaskLog, Int> {
    
    fun findByTaskTypeOrderByExecutionDateDesc(taskType: ScheduledTaskType): List<ScheduledTaskLog>
    
    @Query(value = "SELECT * FROM scheduled_task_logs ORDER BY execution_date DESC LIMIT ?1", nativeQuery = true)
    fun findRecentLogs(limit: Int): List<ScheduledTaskLog>
    
    fun findByExecutionDateBetween(startDate: LocalDateTime, endDate: LocalDateTime): List<ScheduledTaskLog>
    
    @Query("SELECT s FROM ScheduledTaskLog s WHERE s.taskType = ?1 AND s.executionDate BETWEEN ?2 AND ?3 ORDER BY s.executionDate DESC")
    fun findByTaskTypeAndDateRange(taskType: ScheduledTaskType, startDate: LocalDateTime, endDate: LocalDateTime): List<ScheduledTaskLog>
}


