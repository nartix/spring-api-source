--liquibase formatted sql

--changeset feroz:20260430-002-create-iam-user-indexes
CREATE INDEX IF NOT EXISTS idx_iam_user_username ON iam_user(username);
CREATE INDEX IF NOT EXISTS idx_iam_user_email ON iam_user(email);
CREATE INDEX IF NOT EXISTS idx_iam_user_is_active ON iam_user(is_active);
CREATE INDEX IF NOT EXISTS idx_iam_user_created_at ON iam_user(created_at DESC);

--rollback DROP INDEX IF EXISTS idx_iam_user_username;
--rollback DROP INDEX IF EXISTS idx_iam_user_email;
--rollback DROP INDEX IF EXISTS idx_iam_user_is_active;
--rollback DROP INDEX IF EXISTS idx_iam_user_created_at;
