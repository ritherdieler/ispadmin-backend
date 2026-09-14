package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.ErrorLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Date

@Repository
interface ErrorLogRepository : JpaRepository<ErrorLog, Long> {

    @Query("SELECT DISTINCT e.module FROM ErrorLog e WHERE e.module IS NOT NULL")
    fun findDistinctModules(): List<String>

    fun findByModuleOrderByDateDesc(module: String): List<ErrorLog>

    @Query(value = "SELECT * FROM error_log ORDER BY date DESC LIMIT ?1", nativeQuery = true)
    fun findRecentErrors(limit: Int): List<ErrorLog>

    @Query("""
        SELECT e FROM ErrorLog e
        WHERE (:module IS NULL OR e.module = :module)
          AND (:search IS NULL OR LOWER(e.error) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(e.data) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:from IS NULL OR e.date >= :from)
          AND (:to IS NULL OR e.date <= :to)
        ORDER BY e.date DESC
    """)
    fun findWithFilters(
        @Param("module") module: String?,
        @Param("search") search: String?,
        @Param("from") from: Date?,
        @Param("to") to: Date?,
        pageable: Pageable
    ): Page<ErrorLog>

    @Query("SELECT e.module, COUNT(e) FROM ErrorLog e WHERE e.module IS NOT NULL GROUP BY e.module ORDER BY COUNT(e) DESC")
    fun countByModule(): List<Array<Any>>

    @Query("SELECT COUNT(e) FROM ErrorLog e WHERE e.date >= :since")
    fun countSince(@Param("since") since: Date): Long

    @Query("SELECT COUNT(e) FROM ErrorLog e")
    fun countTotal(): Long
}
