package com.dscorp.wispadmin.wispadmin.repository

object WhatsAppCandidateSql {
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
