CREATE TABLE IF NOT EXISTS notification_jobs (
    id UUID PRIMARY KEY,
    batch_id UUID,
    application_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    type VARCHAR(100) NOT NULL,
    level VARCHAR(100) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_jobs_application FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_jobs_recipient FOREIGN KEY (recipient_id) REFERENCES recipients (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_notification_jobs_status_retry_created ON notification_jobs (status, retry_after, created_at ASC);
