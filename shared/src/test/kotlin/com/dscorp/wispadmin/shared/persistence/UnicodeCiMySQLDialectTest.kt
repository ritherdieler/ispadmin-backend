package com.dscorp.wispadmin.shared.persistence

import org.hibernate.dialect.function.SQLFunction
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.type.IntegerType
import org.hibernate.type.StringType
import org.hibernate.type.TypeResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class UnicodeCiMySQLDialectTest {

    @Test
    fun stringCastUsesUnicodeCiCollation() {
        val sql = render("?", "string")
        assertEquals("cast(? as char) collate utf8mb4_unicode_ci", sql)
    }

    @Test
    fun nonStringCastKeepsMysqlType() {
        val sql = render("?", "integer")
        assertEquals("cast(? as signed)", sql)
        assertFalse(sql.contains("collate"))
    }

    private fun render(value: String, typeName: String): String {
        val dialect = UnicodeCiMySQLDialect()
        val function = dialect.functions["cast"] as SQLFunction
        val factory = Mockito.mock(SessionFactoryImplementor::class.java)
        val resolver = Mockito.mock(TypeResolver::class.java)
        Mockito.`when`(factory.dialect).thenReturn(dialect)
        Mockito.`when`(factory.typeResolver).thenReturn(resolver)
        Mockito.`when`(resolver.heuristicType("string")).thenReturn(StringType.INSTANCE)
        Mockito.`when`(resolver.heuristicType("integer")).thenReturn(IntegerType.INSTANCE)
        return function.render(null, listOf(value, typeName), factory)
    }
}
