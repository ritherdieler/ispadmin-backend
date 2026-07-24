package com.dscorp.wispadmin.observability.entity

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

@Entity
@Table(
    name = "obs_system_metric",
    indexes = [
        Index(name = "idx_obs_sysmetric_bucket", columnList = "bucket_start"),
        Index(name = "idx_obs_sysmetric_sampled", columnList = "sampled_at")
    ]
)
data class ObsSystemMetric(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "bucket_start")
    var bucketStart: LocalDateTime? = null,

    @Column(name = "sampled_at")
    var sampledAt: LocalDateTime? = null,

    @Column(name = "heap_used_bytes")
    var heapUsedBytes: Long = 0,

    @Column(name = "heap_max_bytes")
    var heapMaxBytes: Long = 0,

    @Column(name = "non_heap_used_bytes")
    var nonHeapUsedBytes: Long = 0,

    @Column(name = "cpu_process")
    var cpuProcess: Double = 0.0,

    @Column(name = "cpu_system")
    var cpuSystem: Double = 0.0,

    @Column(name = "os_mem_free_bytes")
    var osMemFreeBytes: Long = 0,

    @Column(name = "os_mem_total_bytes")
    var osMemTotalBytes: Long = 0,

    @Column(name = "threads_live")
    var threadsLive: Int = 0,

    @Column(name = "threads_daemon")
    var threadsDaemon: Int = 0,

    @Column(name = "threads_peak")
    var threadsPeak: Int = 0,

    @Column(name = "gc_count")
    var gcCount: Long = 0,

    @Column(name = "gc_time_ms")
    var gcTimeMs: Long = 0,

    @Column(name = "pool_active")
    var poolActive: Int = 0,

    @Column(name = "pool_idle")
    var poolIdle: Int = 0,

    @Column(name = "pool_waiting")
    var poolWaiting: Int = 0,

    @Column(name = "pool_total")
    var poolTotal: Int = 0,

    @Column(name = "pool_max")
    var poolMax: Int = 0
)
