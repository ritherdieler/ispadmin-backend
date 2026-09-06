package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.oltgateway.dto.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.dao.DuplicateKeyException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class ActivationOperation(
    val request: OnuActivateRequestDto,
    val operationId: String = UUID.randomUUID().toString(),
    var status: OnuActivationStatusDto = OnuActivationStatusDto(sn=request.sn,oltStatus=OltActivationStatus.PENDING,cpeStatus=CpeProvisionStatus.PENDING),
    var stage: String = "OLT",
    var leaseUntil: Long = 0,
    var attempts: Int = 0,
)
interface ActivationJournal {
    fun acquire(request: OnuActivateRequestDto): Pair<ActivationOperation,Boolean>
    fun save(operation: ActivationOperation)
    fun bySn(sn: String): ActivationOperation?
    fun byExternalId(id: String): ActivationOperation?
    fun pending(): List<ActivationOperation>
    fun unpublished(): List<ActivationOperation>
    fun published(operationId: String)
}

/** Used only by unit tests; runtime wiring always provides the SQL journal. */
class MemoryActivationJournal : ActivationJournal {
    private val rows=mutableMapOf<String,ActivationOperation>()
    private val delivered=mutableSetOf<String>()
    @Synchronized override fun acquire(request: OnuActivateRequestDto): Pair<ActivationOperation,Boolean> {
        val old=rows[request.sn]
        if(old!=null && old.stage=="DONE") {
            if(old.request==request) return old to false
            rows.remove(request.sn)
        } else if(old!=null) {
            require(old.request==request) { "Activation request conflicts with the existing operation" }
            val acquired=old.leaseUntil < System.currentTimeMillis()
            if(acquired) old.leaseUntil=System.currentTimeMillis()+600_000
            return old to acquired
        }
        val row=ActivationOperation(request).also { rows[request.sn]=it }
        row.leaseUntil=System.currentTimeMillis()+600_000
        return row to true
    }
    @Synchronized override fun save(operation: ActivationOperation) { rows[operation.request.sn]=operation }
    @Synchronized override fun bySn(sn: String)=rows[sn]
    @Synchronized override fun byExternalId(id: String)=rows.values.firstOrNull { it.status.uniqueExternalId==id }
    @Synchronized override fun pending()=rows.values.filter { it.stage!="DONE" && it.leaseUntil < System.currentTimeMillis() }.toList()
    @Synchronized override fun unpublished()=rows.values.filter { it.stage=="DONE" && it.operationId !in delivered }.toList()
    @Synchronized override fun published(operationId: String) { delivered.add(operationId) }
}

class JdbcActivationJournal(private val jdbc: JdbcTemplate,private val json: ObjectMapper,secret: String) : ActivationJournal {
    private val key=SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()),"AES")
    init { require(secret.isNotBlank()) { "OLT operation encryption secret is required" } }

    override fun acquire(request: OnuActivateRequestDto): Pair<ActivationOperation,Boolean> {
        val candidate=ActivationOperation(request)
        val fingerprint=hash(json.writeValueAsBytes(request))
        val existing=bySn(request.sn)
        if(existing!=null && existing.stage=="DONE") {
            val previousFingerprint=hash(json.writeValueAsBytes(existing.request))
            if(previousFingerprint==fingerprint) return existing to false
            jdbc.update("DELETE FROM olt_activation_operation WHERE sn=?",request.sn)
        }
        try {
            jdbc.update("INSERT INTO olt_activation_operation (sn,operation_id,request_hash,request_cipher,status_json,stage,lease_until,event_published,attempt_count) VALUES (?,?,?,?,?,'OLT',0,false,0)",
                request.sn,candidate.operationId,fingerprint,encrypt(json.writeValueAsBytes(request)),json.writeValueAsString(candidate.status))
        } catch(_: DuplicateKeyException) { }
        val old=bySn(request.sn) ?: error("Activation journal write failed")
        require(hash(json.writeValueAsBytes(old.request))==fingerprint) { "Activation request conflicts with the existing operation" }
        val now=System.currentTimeMillis()
        val acquired=jdbc.update("UPDATE olt_activation_operation SET lease_until=? WHERE sn=? AND lease_until<? AND stage<>'DONE'",now+600_000,request.sn,now)==1
        if(acquired) old.leaseUntil=now+600_000
        return old to acquired
    }
    override fun save(operation: ActivationOperation) {
        jdbc.update("UPDATE olt_activation_operation SET status_json=?,external_id=?,stage=?,lease_until=?,attempt_count=? WHERE operation_id=?",
            json.writeValueAsString(operation.status),operation.status.uniqueExternalId,operation.stage,operation.leaseUntil,operation.attempts,operation.operationId)
    }
    override fun bySn(sn: String)=query("WHERE sn=?",sn).firstOrNull()
    override fun byExternalId(id: String)=query("WHERE external_id=?",id).singleOrNull()
    override fun pending()=query("WHERE stage<>'DONE' AND lease_until<? ORDER BY sn LIMIT 50",System.currentTimeMillis())
    override fun unpublished()=query("WHERE stage='DONE' AND event_published=false ORDER BY sn LIMIT 50")
    override fun published(operationId: String) { jdbc.update("UPDATE olt_activation_operation SET event_published=true WHERE operation_id=?",operationId) }
    private fun query(where: String,vararg args: Any): List<ActivationOperation> = jdbc.query("SELECT * FROM olt_activation_operation $where", { rs,_ ->
        ActivationOperation(json.readValue(decrypt(rs.getString("request_cipher")),OnuActivateRequestDto::class.java),rs.getString("operation_id"),
            json.readValue(rs.getString("status_json"),OnuActivationStatusDto::class.java),rs.getString("stage"),rs.getLong("lease_until"),rs.getInt("attempt_count"))
    },*args)
    private fun hash(bytes: ByteArray)=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun encrypt(bytes: ByteArray): String {
        val iv=ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key,GCMParameterSpec(128,iv)) }
        return Base64.getEncoder().encodeToString(iv+cipher.doFinal(bytes))
    }
    private fun decrypt(value: String): ByteArray {
        val bytes=Base64.getDecoder().decode(value)
        return Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(0,12)));doFinal(bytes.copyOfRange(12,bytes.size)) }
    }
}
