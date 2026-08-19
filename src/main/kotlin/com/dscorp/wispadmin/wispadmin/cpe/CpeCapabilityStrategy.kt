package com.dscorp.wispadmin.wispadmin.cpe

/**
 * Resolves what a CPE family accepts over CWMP. Add a new implementation instead of
 * branching on vendors inside the orchestration layer.
 */
interface CpeCapabilityStrategy {

    fun supports(profile: CpeDeviceProfile): Boolean

    fun capabilities(profile: CpeDeviceProfile): CpeCapabilities
}
