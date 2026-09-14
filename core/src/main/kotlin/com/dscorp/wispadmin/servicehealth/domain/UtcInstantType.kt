package com.dscorp.wispadmin.servicehealth.domain

import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.usertype.UserType
import java.io.Serializable
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.Calendar
import java.util.TimeZone

/** Opt-in UTC storage for this domain; legacy Hibernate JDBC timezone remains America/Lima. */
class UtcInstantType : UserType {
    override fun sqlTypes() = intArrayOf(Types.TIMESTAMP)
    override fun returnedClass(): Class<*> = Instant::class.java
    override fun equals(x: Any?, y: Any?) = x == y
    override fun hashCode(x: Any?) = x?.hashCode() ?: 0
    override fun deepCopy(value: Any?) = value
    override fun isMutable() = false
    override fun disassemble(value: Any?): Serializable? = value as? Instant
    override fun assemble(cached: Serializable?, owner: Any?) = cached
    override fun replace(original: Any?, target: Any?, owner: Any?) = original
    override fun nullSafeGet(rs: ResultSet, names: Array<out String>, session: SharedSessionContractImplementor, owner: Any?): Any? =
        rs.getTimestamp(names[0], utcCalendar())?.toInstant()
    override fun nullSafeSet(st: PreparedStatement, value: Any?, index: Int, session: SharedSessionContractImplementor) {
        if (value == null) st.setNull(index, Types.TIMESTAMP)
        else st.setTimestamp(index, Timestamp.from(value as Instant), utcCalendar())
    }
    private fun utcCalendar() = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
}
