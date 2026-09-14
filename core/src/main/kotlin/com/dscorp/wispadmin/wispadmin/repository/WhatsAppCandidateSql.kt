package com.dscorp.wispadmin.wispadmin.repository

object WhatsAppCandidateSql {
    /**
     * One row per subscription_id: every unpaid invoice is aggregated.
     * The oldest unpaid payment id (billing_date, then id) remains the send anchor.
     */
    const val OLDEST_UNPAID_PAYMENT_PER_SUBSCRIPTION_JOIN = """
        INNER JOIN (
            SELECT
                subscription_id,
                SUM(amount_to_pay) AS total_amount,
                COUNT(id) AS invoice_count,
                MIN(billing_date_datetime) AS period_from,
                MAX(billing_date_datetime) AS period_to,
                SUBSTRING_INDEX(
                    GROUP_CONCAT(id ORDER BY billing_date_datetime ASC, id ASC),
                    ',',
                    1
                ) AS oldest_payment_id
            FROM payment
            WHERE paid = false
            GROUP BY subscription_id
        ) unpaid ON CAST(unpaid.oldest_payment_id AS UNSIGNED) = p.id
    """

    const val REMINDER_CANDIDATE_ROW_SELECT = """
        SELECT
            p.id AS payment_id,
            s.id AS subscription_id,
            s.first_name,
            s.last_name,
            s.phone,
            unpaid.total_amount,
            p.amount_paid,
            unpaid.period_from,
            p.payment_date_datetime,
            unpaid.invoice_count,
            unpaid.period_to,
            COALESCE(s.is_bimonthly, FALSE) AS isBimonthly
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
