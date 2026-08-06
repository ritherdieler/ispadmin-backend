package com.dscorp.wispadmin.wispadmin.data.model

import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "scheduled_task_logs")
data class ScheduledTaskLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val taskType: ScheduledTaskType,

    @Column(nullable = false)
    val executionDate: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val processedCount: Int = 0,

    val createdCount: Int = 0,

    val deletedCount: Int = 0,

    val alreadyExistsCount: Int = 0,

    val errorCount: Int = 0,

    val itemsGeneratedInMikrotik: Int = 0,

    val omittedByTvCable: Int = 0,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val status: TaskExecutionStatus = TaskExecutionStatus.SUCCESS,

    @Lob
    @Column(columnDefinition = "TEXT")
    val message: String? = null,

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    val detailedResult: String? = null,

    @Lob
    @Column(columnDefinition = "TEXT")
    val errorMessage: String? = null
)

enum class ScheduledTaskType {
    CUT_INTERNET_SERVICE,
    CUT_INTERNET_SERVICE_DEBTORS,
    CUT_INTERNET_SERVICE_CANCELLED,
    GENERATE_ADDRESS_LIST_CANCELLED,
    CREATE_SUBSCRIPTIONS_QUEUE,
    MONTHLY_BILLING_CLOSE,
    MONTHLY_CLOSE_SUBSCRIPTION_SNAPSHOT,
    MONTHLY_CLOSE_COLLECTS_SNAPSHOT,
    MONTHLY_CLOSE_MASS_BILLING,
}

enum class TaskExecutionStatus {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED
}

