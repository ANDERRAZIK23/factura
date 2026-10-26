ALTER TABLE refresh_tokens ADD COLUMN created_by_ip VARCHAR(64);
ALTER TABLE refresh_tokens ADD COLUMN user_agent VARCHAR(512);
CREATE INDEX idx_refresh_tokens_active_expiry
    ON refresh_tokens(user_id, expires_at)
    WHERE revoked_at IS NULL;
