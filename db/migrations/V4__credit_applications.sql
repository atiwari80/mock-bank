-- Feature 6: Credit Card Application
-- Stores approved/declined applications. Raw SSN is never persisted; only a
-- SHA-256 hex digest of the value the applicant submitted.

CREATE TABLE credit_applications (
    id             BIGSERIAL PRIMARY KEY,
    customer_id    BIGINT        NOT NULL REFERENCES customers(id),
    ssn_hash       VARCHAR(64)   NOT NULL,
    requested_limit NUMERIC(14,2) NOT NULL,
    approved_limit  NUMERIC(14,2),
    status         VARCHAR(20)   NOT NULL CHECK (status IN ('pending','approved','declined')),
    bureau_score   INT,
    created_at     TIMESTAMP     NOT NULL
);
