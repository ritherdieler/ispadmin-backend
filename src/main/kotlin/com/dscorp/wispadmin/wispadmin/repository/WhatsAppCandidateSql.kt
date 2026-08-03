package com.dscorp.wispadmin.wispadmin.repository

object WhatsAppCandidateSql {
    /**
     * Una fila por subscription_id: factura impaga con billing_date (e id) más antiguos.
     * Requiere MySQL 8+ (ROW_NUMBER).
     */
    const val OLDEST_UNPAID_PAYMENT_PER_SUBSCRIPTION_JOIN = """
        INNER JOIN (
            SELECT id
            FROM (
                SELECT
                    id,
                    ROW_NUMBER() OVER (
                        PARTITION BY subscription_id
                        ORDER BY billing_date_datetime ASC, id ASC
                    ) AS rn
                FROM payment
                WHERE paid = false
            ) ranked
            WHERE ranked.rn = 1
        ) oldest ON oldest.id = p.id
    """

    const val PERUVIAN_PHONE_FILTER = """
          AND s.phone IS NOT NULL
          AND s.phone <> ''
          AND (
              (
                  CHAR_LENGTH(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(s.phone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '')) = 9
                  AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(s.phone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '') LIKE '9%'
              )
              OR
              (
                  CHAR_LENGTH(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(s.phone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '')) = 11
                  AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(s.phone, '+', ''), ' ', ''), '-', ''), '(', ''), ')', '') LIKE '519%'
              )
          )
    """
}
