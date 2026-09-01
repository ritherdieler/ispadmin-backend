package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.dto.qualityAt
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Strict projection: never download the WLAN/Hosts subtree or station names. */
object WifiTelemetry {
    const val ROOT = "InternetGatewayDevice.LANDevice.1"
    const val MAX_STATIONS = 32
    val stationFields = listOf("AssociatedDeviceMACAddress", "AssociatedDeviceRssi", "AssociatedDeviceRate",
        "X_ZTE-COM_WLAN_SNR", "X_ZTE-COM_WLAN_Noise", "X_ZTE-COM_WLAN_PacketSend", "X_ZTE-COM_WLAN_PacketReceived",
        "X_HW_RSSI", "X_HW_SNR", "X_HW_Noise", "X_HW_RxRate", "X_HW_TxRate")
    fun radios(model: String): Map<Int,String> = when(model.uppercase()) {
        "F6600R" -> mapOf(1 to "2.4", 5 to "5")
        "V2804AX15T" -> mapOf(1 to "2.4")
        else -> emptyMap()
    }
    fun projection(): String = (listOf("_id","_lastInform","_lastBoot","_deviceId",
        "InternetGatewayDevice.DeviceInfo.SoftwareVersion", "$ROOT.Hosts.HostNumberOfEntries") +
        listOf(1,5).flatMap { radio ->
            val p="$ROOT.WLANConfiguration.$radio"
            listOf("$p.TotalAssociations") + (1..MAX_STATIONS).flatMap { index ->
                stationFields.map { "$p.AssociatedDevice.$index.$it" }
            }
        }).joinToString(",")
    fun countProjection(): String = (listOf("_id","_lastInform","_lastBoot","_deviceId",
        "InternetGatewayDevice.DeviceInfo.SoftwareVersion", "$ROOT.Hosts.HostNumberOfEntries") +
        listOf(1, 5).map { "$ROOT.WLANConfiguration.$it.TotalAssociations" }).joinToString(",")
    fun stationProjection(associated2g: Int, associated5g: Int): String {
        val counts = mapOf(1 to associated2g.coerceIn(0, MAX_STATIONS), 5 to associated5g.coerceIn(0, MAX_STATIONS))
        return (listOf("_id", "_lastInform") + counts.flatMap { (radio, n) ->
            val p = "$ROOT.WLANConfiguration.$radio"
            listOf("$p.TotalAssociations") + (1..n).flatMap { index -> stationFields.map { "$p.AssociatedDevice.$index.$it" } }
        }).joinToString(",")
    }

    fun gpvPaths(root: JsonNode, model: String): List<String> {
        return radios(model).keys.map { radio -> "$ROOT.WLANConfiguration.$radio.TotalAssociations" }
    }
    fun gpvStationPaths(model: String, associated2g: Int?, associated5g: Int?): List<String> {
        val counts = mapOf("2.4" to (associated2g ?: 0), "5" to (associated5g ?: 0))
        return radios(model).flatMap { (radio, band) ->
            val n = counts.getValue(band).coerceIn(0, MAX_STATIONS)
            val base = "$ROOT.WLANConfiguration.$radio"
            (1..n).flatMap { index -> stationFields.map { "$base.AssociatedDevice.$index.$it" } }
        }
    }
    fun gpvRefreshPaths(root: JsonNode, model: String, subscriptionId: Int, now: Instant, secret: String): List<String> {
        val totals = gpvPaths(root, model)
        val reading = parse(root, subscriptionId, model, now, secret) ?: return totals
        if (!reading.complete) return totals
        return totals + gpvStationPaths(model, reading.count.associated2g, reading.count.associated5g)
    }

    fun node(root: JsonNode, path: String): JsonNode {
        var current=root
        for (part in path.split('.')) current=current.path(part)
        return current
    }
    fun timestamp(n: JsonNode): Instant? = parseInstant(n.path("_timestamp"))
    fun parseInstant(n: JsonNode): Instant? = try {
        if(n.isNumber) Instant.ofEpochMilli(n.asLong()) else Instant.parse(n.asText())
    } catch (_: Exception) { null }
    fun value(root: JsonNode, path: String): String? = node(root,path).path("_value").takeUnless { it.isMissingNode || it.isNull }?.asText()
    fun stationKey(secret: String, subscriptionId: Int, address: String): String {
        require(secret.toByteArray().size >= 32) { "station-hmac-key debe tener al menos 32 bytes" }
        val normalized=address.replace(Regex("[^a-fA-F0-9]"), "").uppercase()
        require(normalized.length == 12) { "INVALID_STATION_ADDRESS" }
        val mac=Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(),"HmacSHA256"))
        return mac.doFinal("$subscriptionId:$normalized".toByteArray()).joinToString("") { "%02x".format(it) }
    }

    data class Reading(val count: WifiCountSample, val stations: List<WifiStationSample>, val complete: Boolean)
    fun shouldPersist(reading: Reading): Boolean =
        reading.complete && reading.count.observedAt != null && reading.count.qualityStatus != Quality.UNSUPPORTED
    fun applyCurrent(status: WifiCurrent, reading: Reading, deviceId: String, model: String, now: Instant): Boolean {
        if (!shouldPersist(reading)) return false
        status.deviceId = deviceId
        status.model = model
        status.informAt = reading.count.informAt
        status.observedAt = reading.count.observedAt
        status.associatedDeviceCount = reading.count.associatedDeviceCount
        status.qualityStatus = reading.count.qualityStatus
        status.updatedAt = now
        return true
    }
    fun parse(root: JsonNode, subscriptionId: Int, model: String, now: Instant, secret: String): Reading? {
        val inform=parseInstant(root.path("_lastInform")) ?: return null
        val radioMap=radios(model)
        val count=WifiCountSample(subscriptionId=subscriptionId, deviceId=root.path("_id").asText(),informAt=inform,collectedAt=now)
        if(radioMap.isEmpty()) { count.qualityStatus=Quality.UNSUPPORTED; return Reading(count,emptyList(),true) }
        val times=mutableListOf<Instant>()
        val stations=mutableListOf<WifiStationSample>()
        var total=0; var complete=true
        for((radio,band) in radioMap) {
            val p="$ROOT.WLANConfiguration.$radio"
            val n=node(root,"$p.TotalAssociations")
            val observed=timestamp(n)
            val number=n.path("_value").asText().toIntOrNull()
            // Inform timestamps and parameter timestamps describe different events.
            if(observed == null || observed.isBefore(inform.minusSeconds(60)) || observed.isAfter(now.plusSeconds(60)) || number == null || number < 0) {
                complete=false; continue
            }
            times+=observed; total+=number
            if(band=="2.4") count.associated2g=number else count.associated5g=number
            if(number==0) continue
            if(number>MAX_STATIONS) count.errorReason="STATION_LIMIT_EXCEEDED"
            for(index in 1..MAX_STATIONS) {
                val base="$p.AssociatedDevice.$index"
                val addr=node(root,"$base.AssociatedDeviceMACAddress")
                val address=addr.path("_value").asText("")
                val addrAt=timestamp(addr)
                if(address.isBlank() || addrAt==null || addrAt.isBefore(inform.minusSeconds(60))) continue
                val metricTimes=mutableListOf<Instant>()
                fun metric(vararg fields: String): Double? {
                    for(field in fields) {
                        val v=node(root,"$base.$field"); val t=timestamp(v) ?: continue
                        if(t.isBefore(inform.minusSeconds(60)) || t.isAfter(now.plusSeconds(60))) continue
                        val numberValue=v.path("_value").asText().toDoubleOrNull()?.takeIf { it.isFinite() } ?: continue
                        metricTimes+=t; return numberValue
                    }
                    return null
                }
                val rssi=metric("AssociatedDeviceRssi","X_HW_RSSI")?.takeIf { it in -120.0..0.0 }
                val snr=metric("X_ZTE-COM_WLAN_SNR","X_HW_SNR")?.takeIf { it in 0.0..100.0 }
                val noise=metric("X_ZTE-COM_WLAN_Noise","X_HW_Noise")
                val rx=metric("X_HW_RxRate","AssociatedDeviceRate")
                val tx=metric("X_HW_TxRate","AssociatedDeviceRate")
                val ptx=metric("X_ZTE-COM_WLAN_PacketSend")?.toLong()
                val prx=metric("X_ZTE-COM_WLAN_PacketReceived")?.toLong()
                if(rssi==null && snr==null) continue
                val key=try { stationKey(secret,subscriptionId,address) } catch (_: IllegalArgumentException) { continue }
                val t=(metricTimes+addrAt).minOrNull() ?: continue
                stations+=WifiStationSample(subscriptionId=subscriptionId,stationKey=key,band=band,observedAt=t,collectedAt=now,
                    rssi=rssi,snr=snr,noise=noise,rxRate=rx,txRate=tx,packetsTx=ptx,packetsRx=prx)
            }
        }
        count.observedAt=times.minOrNull()
        count.associatedDeviceCount=if(complete) total else null
        count.qualityStatus=if(complete) qualityAt(count.observedAt,now,7200) else Quality.MISSING
        if(!complete) count.errorReason="PARAMETERS_NOT_REFRESHED_FOR_INFORM"
        return Reading(count,stations,complete)
    }
}
