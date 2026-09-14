package com.dscorp.wispadmin.wispadmin.data.model

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.persistence.Column
import javax.persistence.Table

class WhatsAppMessageLogUniqueMetaMessageIdTest {

    @Test
    fun `metaMessageId declara unique a nivel columna e indice`() {
        val field = WhatsAppMessageLog::class.java.getDeclaredField("metaMessageId")
        val column = field.getAnnotation(Column::class.java)
        assertNotNull(column)
        assertTrue(column!!.unique)

        val table = WhatsAppMessageLog::class.java.getAnnotation(Table::class.java)
        assertNotNull(table)
        val uniqueIndex = table!!.indexes.any {
            it.unique && it.columnList.contains("metaMessageId")
        }
        assertTrue(uniqueIndex)
    }
}
