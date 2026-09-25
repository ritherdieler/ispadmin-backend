package com.dscorp.wispadmin.wispadmin.service.provisioningv2

/** Earlier stages reach SUCCEEDED only after their remote readback; VERIFY guards durable evidence completeness. */
class VerifyProvisioningStageHandler : ProvisioningStageHandler {
    override val stage = ProvisioningStage.VERIFY
    override fun reconcile(context: ProvisioningStageContext): StageObservation {
        listOf("olt", "omci", "acs-contact", "internet", "wifi").forEach { key ->
            if (context.resourceSnapshot(key) == null) throw ProvisioningStepException(
                ProvisioningFailure("VERIFY_RESOURCE_MISSING", "Falta evidencia de aprovisionamiento. Consulte el historial.", false))
        }
        return StageObservation.SATISFIED
    }
    override fun apply(context: ProvisioningStageContext) = reconcile(context)
    override fun compensate(context: ProvisioningStageContext) = StageObservation.SATISFIED
}
