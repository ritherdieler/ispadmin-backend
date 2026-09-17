package com.dscorp.wispadmin.wispadmin.trafficclient

object TrafficRouterOsGatewayAccessor {
    private var useCase: TrafficRouterOsCommandUseCase? = null

    fun setUseCase(useCase: TrafficRouterOsCommandUseCase) {
        this.useCase = useCase
    }

    fun clear() {
        useCase = null
    }

    fun useCase(): TrafficRouterOsCommandUseCase {
        return useCase ?: throw IllegalStateException("TrafficRouterOsCommandUseCase is not initialized")
    }
}
