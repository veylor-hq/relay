-- V4: Email Senders, Application Links, Recipient extensions, and Outbox Sender associations

-- 1. Applications table additions: enabled flag
ALTER TABLE applications ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT TRUE;
UPDATE applications SET enabled = TRUE WHERE enabled IS NULL;
ALTER TABLE applications ALTER COLUMN enabled SET NOT NULL;
ALTER TABLE applications ALTER COLUMN enabled SET DEFAULT TRUE;

-- 2. Email Senders table
CREATE TABLE IF NOT EXISTS email_senders (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    from_address VARCHAR(255) NOT NULL,
    smtp_host VARCHAR(255) NOT NULL,
    smtp_port INTEGER NOT NULL DEFAULT 587,
    username VARCHAR(255),
    encrypted_password TEXT,
    auth_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    starttls_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    starttls_required BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Application <-> EmailSender many-to-many relationship
CREATE TABLE IF NOT EXISTS application_senders (
    application_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (application_id, sender_id),
    CONSTRAINT fk_app_senders_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_app_senders_sender FOREIGN KEY (sender_id) REFERENCES email_senders (id) ON DELETE CASCADE
);

-- 4. Recipients extensions: name, metadata, and application attribution
ALTER TABLE recipients ADD COLUMN IF NOT EXISTS name VARCHAR(255);
ALTER TABLE recipients ADD COLUMN IF NOT EXISTS metadata TEXT;
ALTER TABLE recipients ADD COLUMN IF NOT EXISTS created_by_app_id UUID;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_recipients_application') THEN
        ALTER TABLE recipients ADD CONSTRAINT fk_recipients_application FOREIGN KEY (created_by_app_id) REFERENCES applications (id) ON DELETE SET NULL;
    END IF;
END $$;

-- 5. Outbox and Log associations with EmailSender
ALTER TABLE notification_jobs ADD COLUMN IF NOT EXISTS sender_id UUID;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_notification_jobs_sender') THEN
        ALTER TABLE notification_jobs ADD CONSTRAINT fk_notification_jobs_sender FOREIGN KEY (sender_id) REFERENCES email_senders (id) ON DELETE SET NULL;
    END IF;
END $$;
ALTER TABLE notification_jobs ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMPTZ;

ALTER TABLE notification_logs ADD COLUMN IF NOT EXISTS sender_id UUID;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_notification_logs_sender') THEN
        ALTER TABLE notification_logs ADD CONSTRAINT fk_notification_logs_sender FOREIGN KEY (sender_id) REFERENCES email_senders (id) ON DELETE SET NULL;
    END IF;
END $$;
ALTER TABLE notification_logs ADD COLUMN IF NOT EXISTS error_details TEXT;

-- Index on processing_started_at for outbox runtime lease recovery
CREATE INDEX IF NOT EXISTS idx_notification_jobs_processing_started_at ON notification_jobs (status, processing_started_at);
