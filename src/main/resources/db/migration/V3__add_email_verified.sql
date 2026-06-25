-- Прапорець підтвердження email. М'яка верифікація: лист із посиланням
-- надсилається при реєстрації, але вхід не блокується (значення лише для UI).
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
