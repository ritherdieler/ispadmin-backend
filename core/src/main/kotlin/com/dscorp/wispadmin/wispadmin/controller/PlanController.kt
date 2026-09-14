package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.PlanDto
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.PlanRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.CompletableFuture

@RestController
@RequestMapping("/plan")
class PlanController(
    private val repository: PlanRepository,
    private val subscriptionRepository: SubscriptionRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(PlanController::class.java)
    }

    @PostMapping
    fun registerPlan(@RequestBody newPlan: Plan): ResponseEntity<Plan> =
        ResponseEntity.ok(repository.save(newPlan))

    @PutMapping
    fun updatePlan(@RequestBody plan: Plan): ResponseEntity<Plan> {
        val planToUpdate = repository.findById(plan.id).get().apply {
            this.name = plan.name
            this.price = plan.price
            this.uploadSpeed = plan.uploadSpeed
            this.downloadSpeed = plan.downloadSpeed
        }

        val subscriptions = subscriptionRepository.findByPlanId(planToUpdate.id)

        updatePlansInMikrotikInADifferentThread(subscriptions, planToUpdate)

        return ResponseEntity.ok(repository.save(planToUpdate))
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

        val groupedHostDevice = subscriptions
            .filter { it.hostDevice?.id != null }
            .groupBy { it.hostDevice!!.id!! }

        CompletableFuture.runAsync {
            groupedHostDevice.forEach { id, subs ->
                subs.first().hostDevice?.executeCommand { session ->
                    subs.forEach {
                        if (it.ip.isNullOrBlank()) {
                            logger.warn("Suscripción {} con IP nula/vacía. Se omite actualización de cola.", it.id)
                        } else {
                            val result = session.print("/queue/simple", mapOf("target" to "${it.ip}/32"))
                            result.singleOrNull()?.get(".id")?.let { id ->
                                session.set(
                                    "/queue/simple",
                                    id,
                                    mapOf("max-limit" to "${planToUpdate.uploadSpeed}M/${planToUpdate.downloadSpeed}M")
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @GetMapping
    fun getPlanList(): ResponseEntity<List<Plan>> =
        ResponseEntity.ok(repository.findAll().filter { it.isActive }.sortedBy { it.type })

    @GetMapping("/all")
    fun getAllPlans(): ResponseEntity<List<PlanDto>> {
        val planList = repository.findAll().sortedBy { it.type }
        val activeSubscriptions = subscriptionRepository.findActiveSubscriptions()

        val subscriptionCountByPlan = activeSubscriptions
            .filter { it.plan != null }
            .groupBy { it.plan!!.id }
            .mapValues { it.value.size }

        val planDtoList = planList.map { plan ->
            plan.toDto(subscriptionCountByPlan[plan.id] ?: 0)
        }

        return ResponseEntity.ok(planDtoList)
    }

    @DeleteMapping("/{id}")
    fun deletePlan(@PathVariable id: Int): ResponseEntity<Plan> {
        val planToDelete = repository.findById(id)
        return if (planToDelete.isPresent) {
            val plan = planToDelete.get()
            plan.isActive = false
            ResponseEntity.ok(repository.save(plan))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PutMapping("/{id}/activate")
    fun activatePlan(@PathVariable id: Int): ResponseEntity<Plan> {
        val plan = repository.findById(id).orElseThrow { RuntimeException("Plan no encontrado") }
        plan.isActive = true
        return ResponseEntity.ok(repository.save(plan))
    }

    @PutMapping("/{id}/deactivate")
    fun deactivatePlan(@PathVariable id: Int): ResponseEntity<Plan> {
        val plan = repository.findById(id).orElseThrow { RuntimeException("Plan no encontrado") }
        plan.isActive = false
        return ResponseEntity.ok(repository.save(plan))
    }
}

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
