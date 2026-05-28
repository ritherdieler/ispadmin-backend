package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.NapBoxRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * Excepciones específicas para gestión de bornes
 */
class BorneNotAvailableException(
    message: String,
    val originalBorne: String?,
    val napBoxId: Int?,
    val availableBornes: List<String>? = null
) : Exception(message)

class ReactivationRequiresBorneAssignmentException(
    message: String,
    val subscriptionId: Int,
    val originalBorne: String?,
    val napBoxId: Int?
) : Exception(message)

class NoAvailableBornesException(
    message: String,
    val napBoxId: Int,
    val napBoxCode: String
) : Exception(message)

class BorneConstraintViolationException(
    message: String,
    val napBoxId: Int,
    val napBoxCode: String,
    val borneNumber: String
) : Exception(message)

/**
 * Resultado de la validación de borne para reactivación
 */
sealed class BorneValidationResult {
    data class Valid(val borneNumber: String?) : BorneValidationResult()
    data class BorneReassigned(
        val originalBorne: String,
        val napBoxId: Int,
        val napBoxCode: String,
        val availableBornes: List<String>,
        val message: String
    ) : BorneValidationResult()
    data class NoBornesAvailable(
        val napBoxId: Int,
        val napBoxCode: String,
        val message: String
    ) : BorneValidationResult()
}

@Service
class BorneManagementService(
    private val subscriptionRepository: SubscriptionRepository,
    private val napBoxRepository: NapBoxRepository
) {
    private val logger = LoggerFactory.getLogger(BorneManagementService::class.java)
    
    companion object {
        const val MAX_BORNES_PER_NAP = 16
        val VALID_BORNE_NUMBERS = (1..MAX_BORNES_PER_NAP).map { it.toString() }
    }
    
    /**
     * Obtiene los bornes disponibles para una NAP Box específica
     */
    fun getAvailableBornes(napBoxId: Int): List<String> {
        val occupiedBornes = subscriptionRepository
            .findBorneNumbersByNapBoxIdAndServiceStatus(napBoxId, ServiceStatus.ACTIVE)
        
        return VALID_BORNE_NUMBERS.filter { !occupiedBornes.contains(it) }
    }
    
    /**
     * Verifica si un borne específico está disponible
     */
    fun isBorneAvailable(napBoxId: Int, borneNumber: String): Boolean {
        return !subscriptionRepository.existsByNapBoxIdAndBorneNumberAndServiceStatus(
            napBoxId, borneNumber, ServiceStatus.ACTIVE
        )
    }
    
    /**
     * Valida y asigna un borne a una suscripción
     */
    fun validateAndAssignBorne(subscription: Subscription, borneNumber: String?): String? {
        if (subscription.installationType !in listOf(InstallationType.FIBER, InstallationType.ONLY_TV_FIBER)) {
            return null // No se requiere borne para otros tipos
        }
        
        val napBox = subscription.napBox
        requireNotNull(napBox) { "NAP Box es requerido para instalaciones de fibra" }
        
        // Si no se especifica borne, asignar automáticamente
        val borneToAssign = borneNumber ?: getAvailableBornes(napBox.id!!).firstOrNull()
        
        if (borneToAssign == null) {
            throw NoAvailableBornesException(
                "No hay bornes disponibles en la NAP Box ${napBox.code}. " +
                "Todos los $MAX_BORNES_PER_NAP bornes están ocupados por suscripciones activas.",
                napBox.id!!,
                napBox.code
            )
        }
        
        require(borneToAssign in VALID_BORNE_NUMBERS) { 
            "Número de borne inválido. Debe estar entre 1 y $MAX_BORNES_PER_NAP" 
        }
        
        // Validar que no haya conflictos de constraint único
        validateBorneAssignment(napBox.id!!, borneToAssign, subscription.id)
        
        return borneToAssign
    }
    
    /**
     * Valida si una suscripción puede ser reactivada basándose en la disponibilidad del borne
     * Incluye validación de política de negocio (fecha límite agosto 2025)
     */
    fun validateReactivationBorne(subscription: Subscription): BorneValidationResult {
        // Solo validar para instalaciones de fibra
        if (subscription.installationType !in listOf(InstallationType.FIBER, InstallationType.ONLY_TV_FIBER)) {
            return BorneValidationResult.Valid(null)
        }
        
        // Verificar si requiere validación según política de fecha (agosto 2025)
        if (!requiresBorneValidationByDate(subscription)) {
            logger.info("No se requiere validación de borne para reactivación - Cliente: ${subscription.getFullName()}")
            return BorneValidationResult.Valid(null)
        }
        
        val napBox = subscription.napBox
        val originalBorne = subscription.borneNumber
        
        requireNotNull(napBox) { "NAP Box es requerido para instalaciones de fibra" }
        val napBoxId = napBox.id
        requireNotNull(napBoxId) { "NAP Box ID es requerido" }
        
        // Si no tenía borne asignado, requiere intervención presencial
        if (originalBorne.isNullOrBlank()) {
            val customerName = subscription.getFullName()
            val napBoxCode = napBox.code
            
            throw Exception(
                "La reactivación del servicio para el cliente '$customerName' debe realizarse de manera presencial. " +
                "Es esencial que un técnico realice la conexión del cable del usuario en un nuevo borne de la caja NAP '$napBoxCode'. " +
                "El cliente no tiene un borne asignado y requiere intervención técnica para la reconexión física."
            )
        }
        
        logger.info("Validación de borne exitosa para reactivación - Cliente: ${subscription.getFullName()}, Borne: $originalBorne")
        
        // Verificar si el borne original sigue disponible
        val isBorneAvailable = isBorneAvailable(napBoxId, originalBorne)
        
        return if (isBorneAvailable) {
            BorneValidationResult.Valid(originalBorne)
        } else {
            val availableBornes = getAvailableBornes(napBoxId)
            
            if (availableBornes.isEmpty()) {
                BorneValidationResult.NoBornesAvailable(
                    napBoxId = napBoxId,
                    napBoxCode = napBox.code,
                    message = buildNoBornesMessage(napBox.code)
                )
            } else {
                BorneValidationResult.BorneReassigned(
                    originalBorne = originalBorne,
                    napBoxId = napBoxId,
                    napBoxCode = napBox.code,
                    availableBornes = availableBornes,
                    message = buildReassignmentMessage(originalBorne, napBox.code, availableBornes)
                )
            }
        }
    }
    
    /**
     * Verifica si una suscripción requiere validación de borne basándose en la fecha de suscripción
     * Política: Solo suscripciones a partir de agosto 2025 requieren validación
     */
    private fun requiresBorneValidationByDate(subscription: Subscription): Boolean {
        // Fecha límite: 1 de agosto de 2025
        val august2025Date = Calendar.getInstance().apply {
            set(2025, Calendar.AUGUST, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        return subscription.subscriptionDate != null && 
               subscription.subscriptionDate!! >= august2025Date
    }
    
    /**
     * Asigna un nuevo borne a una suscripción durante la reactivación
     */
    fun assignNewBorneForReactivation(
        subscription: Subscription, 
        newBorneNumber: String? = null
    ): String {
        val napBox = subscription.napBox
        requireNotNull(napBox) { "NAP Box es requerido para instalaciones de fibra" }
        
        val borneToAssign = newBorneNumber ?: getAvailableBornes(napBox.id!!).firstOrNull()
        
        requireNotNull(borneToAssign) { 
            "No hay bornes disponibles en la NAP Box ${napBox.code}" 
        }
        
        // Validar que no haya conflictos de constraint único
        validateBorneAssignment(napBox.id!!, borneToAssign, subscription.id)
        
        // Asignar el nuevo borne
        subscription.borneNumber = borneToAssign
        
        logger.info("Nuevo borne $borneToAssign asignado para reactivación de suscripción ${subscription.id}")
        
        return borneToAssign
    }
    
    /**
     * Verifica si existe un conflicto de constraint único para NAP Box y borne
     */
    fun checkBorneConstraintViolation(napBoxId: Int, borneNumber: String, excludeSubscriptionId: Int? = null): Boolean {
        return subscriptionRepository.existsByNapBoxIdAndBorneNumberAndServiceStatus(
            napBoxId, borneNumber, ServiceStatus.ACTIVE
        ).also { exists ->
            if (exists) {
                logger.warn("Constraint violation detected: NAP Box $napBoxId, Borne $borneNumber already exists")
            }
        }
    }
    
    /**
     * Valida que no haya conflictos de constraint único antes de asignar un borne
     */
    fun validateBorneAssignment(napBoxId: Int, borneNumber: String, subscriptionId: Int? = null) {
        val napBox = napBoxRepository.findById(napBoxId).orElse(null)
        val napBoxCode = napBox?.code ?: "N/A"
        
        if (checkBorneConstraintViolation(napBoxId, borneNumber, subscriptionId)) {
            throw BorneConstraintViolationException(
                "El borne $borneNumber ya está asignado en la NAP Box $napBoxCode. " +
                "No se puede duplicar la combinación de NAP Box y número de borne.",
                napBoxId,
                napBoxCode,
                borneNumber
            )
        }
    }
    
    /**
     * Libera un borne cuando se cancela una suscripción
     */
    fun releaseBorne(subscription: Subscription) {
        if (subscription.borneNumber != null && 
            subscription.installationType in listOf(InstallationType.FIBER, InstallationType.ONLY_TV_FIBER)) {
            // El borne se libera automáticamente al cambiar el estado a CANCELLED
            logger.info("Borne ${subscription.borneNumber} liberado para suscripción ${subscription.id}")
        }
    }
    
    private fun buildReassignmentMessage(
        originalBorne: String, 
        napBoxCode: String, 
        availableBornes: List<String>
    ): String {
        return when {
            availableBornes.size == 1 -> 
                "El borne $originalBorne que tenía asignado ha sido reasignado a otro usuario en la NAP Box $napBoxCode. " +
                "Solo queda disponible el borne ${availableBornes.first()}. ¿Desea asignar este borne?"
            
            else -> 
                "El borne $originalBorne que tenía asignado ha sido reasignado a otro usuario en la NAP Box $napBoxCode. " +
                "Bornes disponibles: ${availableBornes.joinToString(", ")}. Por favor, seleccione un nuevo borne."
        }
    }
    
    private fun buildNoBornesMessage(napBoxCode: String): String {
        return "No hay bornes disponibles en la NAP Box $napBoxCode. " +
               "Todos los $MAX_BORNES_PER_NAP bornes están ocupados por suscripciones activas. " +
               "Se requiere asignar una nueva NAP Box para reactivar el servicio."
    }
} 