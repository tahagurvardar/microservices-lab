CREATE TABLE IF NOT EXISTS lab_payment (
  idempotency_key VARCHAR(120) PRIMARY KEY,
  payment_id VARCHAR(80) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ
);
