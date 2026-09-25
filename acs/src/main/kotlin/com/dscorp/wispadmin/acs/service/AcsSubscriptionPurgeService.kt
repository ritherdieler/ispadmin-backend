package com.dscorp.wispadmin.acs.service

class AcsPurgeException(message: String, val retryable: Boolean) : RuntimeException(message)

class AcsSubscriptionPurgeService(
    private val deleteTasks: (String) -> Unit,
    private val deleteCpe: (String) -> Unit,
    private val purgeDevice: (String) -> Unit,
) {
    fun purge(sn: String?, deviceId: String?) {
        val serial = sn?.trim().orEmpty()
        if (serial.isNotEmpty()) {
            deleteTasks(serial)
            deleteCpe(serial)
        }
        val device = deviceId?.trim().orEmpty()
        if (device.isNotEmpty()) purgeDevice(device)
    }
}
