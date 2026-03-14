-- Add missing columns to align payment_methods schema with PaymentMethod entity
ALTER TABLE payment_methods
    ADD COLUMN IF NOT EXISTS billing_email VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last4 VARCHAR(4),
    ADD COLUMN IF NOT EXISTS brand VARCHAR(20),
    ADD COLUMN IF NOT EXISTS expiry_encrypted VARCHAR(255);

COMMENT ON COLUMN payment_methods.billing_email IS 'Billing contact email for the saved payment method';
COMMENT ON COLUMN payment_methods.last4 IS 'Last 4 digits of card (encrypted source)';
COMMENT ON COLUMN payment_methods.brand IS 'Card brand for display';
COMMENT ON COLUMN payment_methods.expiry_encrypted IS 'AES-256-GCM encrypted expiry data';
