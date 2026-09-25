package com.dscorp.wispadmin.shared.persistence

import org.hibernate.QueryException
import org.hibernate.dialect.function.SQLFunction
import org.hibernate.engine.spi.Mapping
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.type.Type

class UnicodeCiCastFunction : SQLFunction {
    override fun hasArguments(): Boolean = true

    override fun hasParenthesesIfNoArguments(): Boolean = true

    override fun getReturnType(columnType: Type?, mapping: Mapping?): Type? = columnType

    override fun render(columnType: Type?, args: List<Any?>?, factory: SessionFactoryImplementor): String {
        val arguments = args ?: throw QueryException("cast() requires two arguments; found :0")
        if (arguments.size != 2) {
            throw QueryException("cast() requires two arguments; found :${arguments.size}")
        }
        val typeName = arguments[1] as String
        val type = factory.typeResolver.heuristicType(typeName)
            ?: throw QueryException("invalid Hibernate type for cast()")
        val codes = type.sqlTypes(factory)
        if (codes.size != 1) {
            throw QueryException("invalid Hibernate type for cast()")
        }
        val sqlType = factory.dialect.getCastTypeName(codes[0]) ?: typeName
        val cast = "cast(${arguments[0]} as $sqlType)"
        return if (sqlType.equals("char", ignoreCase = true)) "$cast collate utf8mb4_unicode_ci" else cast
    }
}
