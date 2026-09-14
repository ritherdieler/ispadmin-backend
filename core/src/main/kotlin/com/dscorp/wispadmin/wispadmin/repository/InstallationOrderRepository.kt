package com.dscorp.wispadmin.wispadmin.repository

import com.dscorp.wispadmin.wispadmin.data.model.InstallationOrder
import com.dscorp.wispadmin.wispadmin.data.model.InstallationOrderStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface InstallationOrderRepository : JpaRepository<InstallationOrder, Int> {
    fun findByStatus(status: InstallationOrderStatus): List<InstallationOrder>
    /**
     * Obtiene las órdenes de instalación paginadas para un usuario específico
     * @param userId ID del usuario
     * @param pageable Configuración de paginación
     * @return Página de órdenes de instalación
     */
    @Query("SELECT DISTINCT io FROM InstallationOrder io WHERE io.technician.id = :userId OR io.seller.id = :userId OR io.assignedBy.id = :userId ORDER BY io.createdAt DESC, io.id DESC")
    fun findByUserId(userId: Int, pageable: Pageable): Page<InstallationOrder>

    /**
     * Obtiene las órdenes de instalación paginadas para un vendedor específico
     * @param sellerId ID del vendedor
     * @param pageable Configuración de paginación
     * @return Página de órdenes de instalación
     */
    @Query("SELECT DISTINCT io FROM InstallationOrder io WHERE io.seller.id = :sellerId ORDER BY io.createdAt DESC, io.id DESC")
    fun findBySellerIdPaginated(sellerId: Int, pageable: Pageable): Page<InstallationOrder>
    
    /**
     * Obtiene las órdenes de instalación paginadas para un técnico específico
     * @param technicianId ID del técnico
     * @param pageable Configuración de paginación
     * @return Página de órdenes de instalación
     */
    @Query("SELECT DISTINCT io FROM InstallationOrder io WHERE io.technician.id = :technicianId ORDER BY io.createdAt DESC, io.id DESC")
    fun findByTechnicianIdPaginated(technicianId: Int, pageable: Pageable): Page<InstallationOrder>

    /**
     * Obtiene todas las órdenes de instalación paginadas
     * @param pageable Configuración de paginación
     * @return Página de órdenes de instalación
     */
    @Query("SELECT DISTINCT io FROM InstallationOrder io ORDER BY io.createdAt DESC, io.id DESC")
    fun findAllPaginated(pageable: Pageable): Page<InstallationOrder>
}
