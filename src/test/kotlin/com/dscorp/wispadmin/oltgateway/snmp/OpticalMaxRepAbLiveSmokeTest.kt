package com.dscorp.wispadmin.oltgateway.snmp

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.snmp4j.CommunityTarget
import org.snmp4j.PDU
import org.snmp4j.Snmp
import org.snmp4j.TransportMapping
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.smi.Address
import org.snmp4j.smi.GenericAddress
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.UdpAddress
import org.snmp4j.smi.VariableBinding
import org.snmp4j.transport.DefaultUdpTransportMapping
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.io.IOException
import java.time.Duration

@Tag("live")
class OpticalMaxRepAbLiveSmokeTest {

    @Test
    fun `maxRep 15 vs 10 on port 1 6 with redis lock`() {
        assumeTrue(System.getenv("OLT_SNMP_MAXREP_AB") == "true") { "set OLT_SNMP_MAXREP_AB=true" }
        val community = System.getenv("OLT_GATEWAY_SNMP_RO_COMMUNITY").orEmpty()
        assumeTrue(community.isNotBlank()) { "set OLT_GATEWAY_SNMP_RO_COMMUNITY" }

        val props = OltGatewayProperties().apply {
            host = System.getenv("OLT_GATEWAY_HOST") ?: "10.11.104.2"
            snmp.enabled = true
            snmp.port = (System.getenv("OLT_GATEWAY_SNMP_PORT") ?: "161").toInt()
            snmp.roCommunity = community
            snmp.timeoutMs = 15_000
            snmp.retries = 1
            snmp.opticalParallelPorts = 1
        }
        val probe = OpticalMaxRepAbProbe { cursor, maxRep ->
            sendPage(props, cursor, maxRep)
        }
        val lock = redisLockOrNoOp()
        val rootOid = OpticalMaxRepAbProbe.rootOidForPort(slot = 1, port = 6)
        val report = OpticalMaxRepAbJob.run(lock, probe, rootOid)
        println(
            "MAXREP_AB slot=1 port=6 timeoutMs=15000 retries=1 parallel=1 " +
                "firstMaxRep=${report.first.maxRepetitions} firstOk=${report.first.pagesOk} " +
                "firstTimeout=${report.first.pagesTimedOut} firstRows=${report.first.rowCount} " +
                "secondMaxRep=${report.second.maxRepetitions} secondOk=${report.second.pagesOk} " +
                "secondTimeout=${report.second.pagesTimedOut} secondRows=${report.second.rowCount}"
        )
        report.first.pages.forEach { page ->
            println("MAXREP_AB_PAGE maxRep=15 index=${page.pageIndex} ok=${page.ok} bindings=${page.bindings} error=${page.error}")
        }
        report.second.pages.forEach { page ->
            println("MAXREP_AB_PAGE maxRep=10 index=${page.pageIndex} ok=${page.ok} bindings=${page.bindings} error=${page.error}")
        }
    }

    private fun redisLockOrNoOp(): OltSnmpPollLocker {
        val host = System.getenv("REDIS_HOST") ?: return NoOpOltSnmpPollLock().also {
            println("MAXREP_AB_LOCK skipped REDIS_HOST unset")
        }
        val port = (System.getenv("REDIS_PORT") ?: "6379").toInt()
        val standalone = RedisStandaloneConfiguration(host, port)
        val password = System.getenv("REDIS_PASSWORD").orEmpty()
        if (password.isNotBlank()) {
            standalone.setPassword(password)
        }
        val factory = LettuceConnectionFactory(standalone)
        factory.afterPropertiesSet()
        val redis = StringRedisTemplate(factory)
        val key = OltSnmpPollLock.resolveKey(
            configured = System.getenv("OLT_GATEWAY_SNMP_POLL_LOCK_KEY") ?: "olt-snmp-poll",
            namespace = "",
            shared = true,
        )
        println("MAXREP_AB_LOCK key=$key host=$host")
        return OltSnmpPollLock(
            store = RedisOltSnmpPollLockStore(redis),
            key = key,
            ttl = Duration.ofMillis(1_200_000),
            waitSlice = Duration.ofSeconds(5),
        )
    }

    private fun sendPage(props: OltGatewayProperties, cursor: String, maxRep: Int): GetBulkPageResponse {
        val snmpProps = props.snmp
        val address = GenericAddress.parse("udp:${props.host}/${snmpProps.port}") as Address
        val transport = DefaultUdpTransportMapping()
        transport.listen()
        val snmp = Snmp(transport as TransportMapping<UdpAddress>)
        try {
            val target = CommunityTarget()
            target.community = OctetString(snmpProps.roCommunity)
            target.address = address
            target.version = SnmpConstants.version2c
            target.timeout = snmpProps.timeoutMs
            target.retries = snmpProps.retries
            val request = PDU().apply {
                type = PDU.GETBULK
                nonRepeaters = 0
                maxRepetitions = maxRep
                add(VariableBinding(OID(cursor)))
            }
            val response = snmp.send(request, target).response
                ?: throw IOException("request timed out")
            if (response.errorStatus != PDU.noError) {
                throw IOException(response.errorStatusText)
            }
            val oids = (0 until response.size()).mapNotNull { index ->
                response.get(index)?.oid?.toString()
            }
            return GetBulkPageResponse(oids = oids)
        } finally {
            try {
                snmp.close()
            } catch (_: Exception) {
            }
        }
    }
}
