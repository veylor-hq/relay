CREATE TABLE applications (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    access_key_hash VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recipients (
    id UUID PRIMARY KEY,
    sanitized_email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notification_logs (
    id UUID PRIMARY KEY,
    batch_id UUID,
    application_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    type VARCHAR(100) NOT NULL,
    level VARCHAR(100) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING' NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_logs_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_logs_recipient FOREIGN KEY (recipient_id) REFERENCES recipients (id) ON DELETE CASCADE
);

CREATE TABLE idempotent_requests (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    nonce UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    status_code INTEGER,
    response_body TEXT,
    CONSTRAINT fk_idempotent_requests_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT uq_idempotency_app_nonce UNIQUE (application_id, nonce)
);

CREATE INDEX idx_idempotent_requests_processed_at ON idempotent_requests(processed_at);
