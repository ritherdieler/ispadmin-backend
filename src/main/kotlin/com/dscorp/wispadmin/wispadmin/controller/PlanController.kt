package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.dto.PlanDto
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.PlanRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.CompletableFuture

@RestController
@RequestMapping("/plan")
class PlanController {
    private val logger = LoggerFactory.getLogger(PlanController::class.java)

    val objectErrorResponse: ResponseEntity<Plan> = ResponseEntity.status(500).body(null)
    val listObjectErrorResponse: ResponseEntity<List<Plan>> = ResponseEntity.status(500).body(null)
    val listDtoErrorResponse: ResponseEntity<List<PlanDto>> = ResponseEntity.status(500).body(null)

    @Autowired
    lateinit var repository: PlanRepository

    @Autowired
    lateinit var subscriptionRepository: SubscriptionRepository

    @PostMapping
    fun registerPlan(@RequestBody newPlan: Plan): ResponseEntity<Plan> {
        return try {
            val plan = repository.save(newPlan)
            if (plan != null) ResponseEntity.status(200).body(plan)
            else objectErrorResponse
        } catch (e: Exception) {
            e.printStackTrace()
            objectErrorResponse
        }
    }


    @PutMapping
    fun updatePlan(@RequestBody plan: Plan): ResponseEntity<Plan> {
        return try {
            val planToUpdate = repository.findById(plan.id).get().apply {
                this.name = plan.name
                this.price = plan.price
                this.uploadSpeed = plan.uploadSpeed
                this.downloadSpeed = plan.downloadSpeed
            }

            val subscriptions = subscriptionRepository.findByPlanId(planToUpdate.id)

            updatePlansInMikrotikInADifferentThread(subscriptions, planToUpdate)

            val updatedPlan = repository.save(planToUpdate)
            ResponseEntity.status(200).body(updatedPlan)
        } catch (e: Exception) {
            e.printStackTrace()
            objectErrorResponse
            ResponseEntity.status(500).body(null)
        }
    }

    private fun updatePlansInMikrotikInADifferentThread(
        subscriptions: List<Subscription>,
        planToUpdate: Plan
    ) {

        val withoutHostDevice = subscriptions.filter { it.hostDevice == null }
        if (withoutHostDevice.isNotEmpty()) {
            logger.warn("Suscripciones sin hostDevice: {}", withoutHostDevice.map { it.id })
        }

        val withNullHostId = subscriptions.filter { it.hostDevice != null && it.hostDevice?.id == null }
        if (withNullHostId.isNotEmpty()) {
            logger.warn("Suscripciones con hostDevice.id nulo: {}", withNullHostId.map { it.id })
        }

        // Agrupar solo suscripciones con hostDevice y id válido
        val groupedHostDevice = subscriptions
            .filter { it.hostDevice?.id != null }
            .groupBy { it.hostDevice!!.id!! }

        CompletableFuture.runAsync { // Ejecuta la tarea programada en un hilo separado
            groupedHostDevice.forEach { id, subs ->
                subs.first().hostDevice?.executeCommand { apiConnection ->
                    subs.forEach {
                        if (it.ip.isNullOrBlank()) {
                            logger.warn("Suscripción {} con IP nula/vacía. Se omite actualización de cola.", it.id)
                        } else {
                            val query = "/queue/simple/print where target=${it.ip}/32"
                            val result = apiConnection.execute(query)
                            result.singleOrNull()?.let { map ->
                                apiConnection.execute("/queue/simple/set .id=${map[".id"]} max-limit=${planToUpdate.uploadSpeed}M/${planToUpdate.downloadSpeed}M")
                            }
                        }
                    }
                }
            }
        }
    }


    @GetMapping
    fun getPlanList(): ResponseEntity<List<Plan>> {
        return try {
            val planList = repository.findAll().filter { it.isActive }.sortedBy { it.type }
            if (planList != null) ResponseEntity.status(200).body(planList)
            else listObjectErrorResponse
        } catch (e: Exception) {
            e.printStackTrace()
            listObjectErrorResponse
        }
    }

    @GetMapping("/all")
    fun getAllPlans(): ResponseEntity<List<PlanDto>> {
        return try {
            val planList = repository.findAll().sortedBy { it.type }
            val activeSubscriptions = subscriptionRepository.findActiveSubscriptions()
            
            // Contar suscripciones activas por plan
            val subscriptionCountByPlan = activeSubscriptions
                .filter { it.plan != null }
                .groupBy { it.plan!!.id }
                .mapValues { it.value.size }
            
            val planDtoList = planList.map { plan ->
                plan.toDto(subscriptionCountByPlan[plan.id] ?: 0)
            }
            
            ResponseEntity.status(200).body(planDtoList)
        } catch (e: Exception) {
            e.printStackTrace()
            listDtoErrorResponse
        }
    }

    @DeleteMapping("/{id}")
    fun deletePlan(@PathVariable id: Int): ResponseEntity<Plan> {
        return try {
            val planToDelete = repository.findById(id)
            if (planToDelete.isPresent) {
                val plan = planToDelete.get()
                plan.isActive = false
                val deletedPlan = repository.save(plan)
                ResponseEntity.status(200).body(deletedPlan)
            } else {
                ResponseEntity.status(404).body(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            objectErrorResponse
        }
    }

    @PutMapping("/{id}/activate")
    fun activatePlan(@PathVariable id: Int): ResponseEntity<Plan> {
        return try {
            val plan = repository.findById(id).orElseThrow { RuntimeException("Plan no encontrado") }
            plan.isActive = true
            val updatedPlan = repository.save(plan)
            ResponseEntity.status(200).body(updatedPlan)
        } catch (e: Exception) {
            e.printStackTrace()
            objectErrorResponse
        }
    }

    @PutMapping("/{id}/deactivate")
    fun deactivatePlan(@PathVariable id: Int): ResponseEntity<Plan> {
        return try {
            val plan = repository.findById(id).orElseThrow { RuntimeException("Plan no encontrado") }
            plan.isActive = false
            val updatedPlan = repository.save(plan)
            ResponseEntity.status(200).body(updatedPlan)
        } catch (e: Exception) {
            e.printStackTrace()
            objectErrorResponse
        }
    }

}

// Extensión para convertir Plan a PlanDto con conteo de suscripciones
fun Plan.toDto(activeSubscriptionsCount: Int = 0): PlanDto = PlanDto(
    id = this.id,
    name = this.name,
    price = this.price,
    downloadSpeed = this.downloadSpeed,
    uploadSpeed = this.uploadSpeed,
    type = this.type,
    isActive = this.isActive,
    activeSubscriptionsCount = activeSubscriptionsCount
)
