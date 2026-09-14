package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.InstallationType

object WelcomeServiceCopy {

    const val PAYMENT_INFO = "Recuerda pagar antes del vencimiento para mantener tu servicio activo."

    fun serviceTitle(installationType: InstallationType?): String = when (installationType) {
        InstallationType.ONLY_TV_FIBER -> "TV Cable"
        InstallationType.FIBER -> "Internet 100% Fibra Optica"
        InstallationType.WIRELESS -> "Enlace Dedicado Inalambrico"
        null -> "Servicio GigaFiber"
    }

    fun serviceDetails(
        installationType: InstallationType?,
        downloadSpeed: Int?,
        uploadSpeed: Int?
    ): String = when (installationType) {
        InstallationType.ONLY_TV_FIBER -> "Full HD + SD y mas de 90 canales"
        InstallationType.FIBER -> formatFiberSpeed(downloadSpeed, uploadSpeed)
        InstallationType.WIRELESS -> "Alta disponibilidad para tu ubicacion"
        null -> "Gracias por confiar en nosotros"
    }

    fun formatFiberSpeed(downloadSpeed: Int?, uploadSpeed: Int?): String {
        val download = formatSpeedMbps(downloadSpeed)
        val upload = formatSpeedMbps(uploadSpeed ?: downloadSpeed)
        return "$download Mbps de bajada y $upload Mbps de subida"
    }

    private fun formatSpeedMbps(rawSpeed: Int?): String {
        if (rawSpeed == null || rawSpeed <= 0) {
            return "—"
        }
        return if (rawSpeed >= 1000) {
            (rawSpeed / 1000).toString()
        } else {
            rawSpeed.toString()
        }
    }
}
