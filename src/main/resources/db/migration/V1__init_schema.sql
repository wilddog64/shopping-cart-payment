-- Payment Service Initial Schema
-- Version: 1
-- Description: Create core payment tables

-- Payments table
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    gateway VARCHAR(20) NOT NULL,
    gateway_transaction_id VARCHAR(255),
    gateway_payment_intent_id VARCHAR(255),
    payment_method_id UUID,
    card_last4 VARCHAR(4),
    card_brand VARCHAR(20),
    metadata TEXT,
    failure_reason VARCHAR(500),
    failure_code VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP,
    correlation_id VARCHAR(100),
    idempotency_key VARCHAR(50)
);

-- Indexes for payments
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_customer_id ON payments(customer_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_created_at ON payments(created_at);
CREATE UNIQUE INDEX idx_payments_idempotency_key ON payments(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Refunds table
CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    gateway_refund_id VARCHAR(255),
    failure_reason VARCHAR(500),
    failure_code VARCHAR(50),
    initiated_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP,
    correlation_id VARCHAR(100)
);

-- Indexes for refunds
CREATE INDEX idx_refunds_payment_id ON refunds(payment_id);
CREATE INDEX idx_refunds_status ON refunds(status);
CREATE INDEX idx_refunds_created_at ON refunds(created_at);

-- Payment methods table (tokenized payment methods)
CREATE TABLE payment_methods (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    gateway VARCHAR(20) NOT NULL,
    gateway_token VARCHAR(255) NOT NULL,
    card_last4 VARCHAR(4),
    card_brand VARCHAR(20),
    card_exp_month VARCHAR(2),
    card_exp_year VARCHAR(4),
    cardholder_name_encrypted TEXT,
    billing_address_encrypted TEXT,
    is_default BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    last_used_at TIMESTAMP
);

-- Indexes for payment methods
CREATE INDEX idx_payment_methods_customer_id ON payment_methods(customer_id);
CREATE INDEX idx_payment_methods_gateway_token ON payment_methods(gateway_token);
CREATE INDEX idx_payment_methods_active ON payment_methods(is_active) WHERE is_active = TRUE;

-- Transactions table (audit log)
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    refund_id UUID REFERENCES refunds(id),
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    success BOOLEAN NOT NULL,
    gateway_transaction_id VARCHAR(255),
    gateway_response TEXT,
    gateway_error_code VARCHAR(50),
    gateway_error_message VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    correlation_id VARCHAR(100)
);

-- Indexes for transactions
CREATE INDEX idx_transactions_payment_id ON transactions(payment_id);
CREATE INDEX idx_transactions_refund_id ON transactions(refund_id) WHERE refund_id IS NOT NULL;
CREATE INDEX idx_transactions_type ON transactions(type);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);

-- Add comments for documentation
COMMENT ON TABLE payments IS 'Payment transactions - PCI DSS compliant (no full card data)';
COMMENT ON TABLE refunds IS 'Refund transactions linked to payments';
COMMENT ON TABLE payment_methods IS 'Tokenized payment methods for customers';
COMMENT ON TABLE transactions IS 'Audit log for all gateway transactions';

COMMENT ON COLUMN payments.card_last4 IS 'Last 4 digits of card - safe to store per PCI DSS';
COMMENT ON COLUMN payment_methods.cardholder_name_encrypted IS 'AES-256-GCM encrypted cardholder name';
COMMENT ON COLUMN payment_methods.billing_address_encrypted IS 'AES-256-GCM encrypted billing address';
