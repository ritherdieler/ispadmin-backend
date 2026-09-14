-- Normalize Peruvian mobile phones in WhatsApp outbound logs (9 digits -> 51 + 9).
-- Safe to re-run: only updates rows that still match the 9-digit mobile pattern.

UPDATE ispadmin.whatsapp_message_log
SET phone = CONCAT('51', phone)
WHERE CHAR_LENGTH(phone) = 9
  AND phone LIKE '9%';
