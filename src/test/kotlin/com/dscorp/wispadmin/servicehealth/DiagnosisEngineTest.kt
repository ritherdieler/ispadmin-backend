package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.service.*
import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.dto.*
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class DiagnosisEngineTest {
    private val now=Instant.parse("2026-08-30T15:00:00Z")
    private val engine=DiagnosisEngine(ServiceHealthProperties().apply { enabled=true; correlationEnabled=true; pilotSubscriptionIds=setOf(1) })
    private fun source(metric: String,value: Any?,quality: Quality=Quality.FRESH,domain: String="OLT")=Evidence(domain,metric,now,value,quality)
    private fun input(sources: List<Evidence>)=HealthInputs(1,now,mapOf("ONU" to "sn","PON" to "1:0:1","ACS" to "device"),sources,
        emptyList(),emptyList(),emptyList(),emptyList(),emptySet(),emptyList(),"ACTIVE",emptyMap())
    @Test fun `zero traffic is not an access outage`() {
        val result=engine.evaluate(input(listOf(source("mbps",mapOf("down" to 0.0,"up" to 0.0),domain="TRAFFIC"))))
        assertFalse(result.diagnoses.any { it.diagnosisCode=="GPON_DOWN" })
        assertEquals("UNKNOWN",result.states["internet"])
    }
    @Test fun `offline stale snapshot cannot confirm GPON down`() {
        val result=engine.evaluate(input(listOf(source("run_state","offline",Quality.STALE))))
        assertTrue(result.diagnoses.isEmpty()); assertEquals("UNKNOWN",result.states["gpon"])
    }
    @Test fun `fresh GPON down has evidence and a safe next check`() {
        val result=engine.evaluate(input(listOf(source("run_state","offline"))))
        assertEquals("GPON_DOWN",result.diagnoses.single().diagnosisCode)
        assertFalse(result.diagnoses.single().evidence.isEmpty())
        assertEquals(Confidence.LOW,result.diagnoses.single().confidence)
    }
    @Test fun `ACS stale with traffic present is management failure not Internet failure`() {
        val result=engine.evaluate(input(listOf(source("run_state","online"),source("mbps",mapOf("down" to 2.0),domain="TRAFFIC"),
            source("last_inform",now.minusSeconds(10000),Quality.STALE,"ACS"),source("collector","OK",domain="ACS"))))
        assertEquals("ACS_STALE",result.diagnoses.single().diagnosisCode); assertEquals("ACTIVE",result.states["internet"])
        assertTrue(result.missingEvidence.any { it.metric=="last_inform" })
    }
    @Test fun `collector loss does not manufacture subscriber outage`() {
        val result=engine.evaluate(input(listOf(source("collector","FAILED",Quality.ERROR,"TRAFFIC"),source("mbps",null,Quality.MISSING,"TRAFFIC"))))
        assertEquals(listOf("TELEMETRY_GAP"),result.diagnoses.map { it.diagnosisCode })
    }
    @Test fun `single weak station reading cannot diagnose persistent WiFi problems`() {
        val base=input(listOf(source("run_state","online"),source("mbps",mapOf("down" to 2.0),domain="TRAFFIC"),source("associated_device_count",2,domain="ACS")))
        val weak=WifiStationSample(countSampleId=1,subscriptionId=1,observedAt=now,rssi=-85.0)
        assertTrue(engine.evaluate(base.copy(wifi=listOf(weak))).diagnoses.isEmpty())
        val second=WifiStationSample(countSampleId=2,subscriptionId=1,observedAt=now.minusSeconds(3600),rssi=-80.0)
        assertEquals("WIFI_QUALITY",engine.evaluate(base.copy(wifi=listOf(weak,second))).diagnoses.single().diagnosisCode)
    }
    @Test fun `optical trend requires both history and flaps`() {
        val base=input(listOf(source("onu_rx_dbm",-29.0)))
        val history=(0..11).map { OpticalSample(onuRxDbm=if(it<6) -24.0 else -29.0,observedAt=now.minusSeconds((12-it)*300L)) }
        assertTrue(engine.evaluate(base.copy(optical=history)).diagnoses.isEmpty())
        val flaps=(1..2).map { OnuStateEvent(previousState="online",state="offline",observedAt=now.minusSeconds(it*1000L)) }
        assertEquals("OPTICAL_DEGRADATION",engine.evaluate(base.copy(optical=history,flaps=flaps)).diagnoses.single().diagnosisCode)
    }
    @Test fun `old weak reading and current good reading do not diagnose WiFi`() {
        val base=input(listOf(source("run_state","online"),source("mbps",mapOf("down" to 2.0),domain="TRAFFIC"),source("associated_device_count",2,domain="ACS")))
        val recent=WifiStationSample(countSampleId=1,observedAt=now,rssi=-80.0)
        val old=WifiStationSample(countSampleId=2,observedAt=now.minusSeconds(7201),rssi=-85.0)
        assertFalse(engine.evaluate(base.copy(wifi=listOf(recent,old))).diagnoses.any { it.diagnosisCode=="WIFI_QUALITY" })
    }
    @Test fun `plan saturation needs coverage healthy optics and router`() {
        val base=input(listOf(source("run_state","online"),source("onu_rx_dbm",-20.0),source("cpu_load",30,domain="NETDIAG")))
        val event=TrafficEvidence(eventId=1,subscriptionId=1,anomalyType="PLAN_SATURATION",coveragePct=95.0,observedAt=now)
        assertTrue(engine.evaluate(base.copy(trafficEvents=listOf(event))).diagnoses.any { it.diagnosisCode=="PLAN_SATURATION" })
        event.coveragePct=20.0
        assertFalse(engine.evaluate(base.copy(trafficEvents=listOf(event))).diagnoses.any { it.diagnosisCode=="PLAN_SATURATION" })
    }
    @Test fun `router capacity requires current CPU and existing domain incident`() {
        val base=input(listOf(source("cpu_load",95,domain="NETDIAG")))
        assertFalse(engine.evaluate(base).diagnoses.any { it.diagnosisCode=="ROUTER_CAPACITY" })
        assertTrue(engine.evaluate(base.copy(routerReasons=setOf("CPU_HIGH"))).diagnoses.any { it.diagnosisCode=="ROUTER_CAPACITY" })
        assertFalse(engine.evaluate(base.copy(sources=listOf(source("cpu_load",95,Quality.STALE,"NETDIAG")),routerReasons=setOf("CPU_HIGH"))).diagnoses.any { it.diagnosisCode=="ROUTER_CAPACITY" })
    }
    @Test fun `unresolved ancestor is explicitly incomplete topology`() {
        val target=com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget(id=10,parentTargetId=999,name="PON")
        val result=engine.evaluate(input(listOf(source("run_state","online"))).copy(targets=listOf(target)))
        assertTrue(result.missingEvidence.any { it.source=="IDENTITY" && it.metric=="PON" })
    }
}
