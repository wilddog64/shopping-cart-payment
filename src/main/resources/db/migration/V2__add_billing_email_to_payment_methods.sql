-- Add missing billing_email column to payment_methods to match PaymentMethod entity
ALTER TABLE payment_methods
    ADD COLUMN IF NOT EXISTS billing_email VARCHAR(100);

COMMENT ON COLUMN payment_methods.billing_email IS 'Billing contact email for the saved payment method';
