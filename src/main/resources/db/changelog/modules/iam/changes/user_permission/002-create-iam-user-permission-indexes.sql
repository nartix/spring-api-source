--liquibase formatted sql

--changeset feroz:20260430-010-create-iam-user-permission-indexes
CREATE INDEX IF NOT EXISTS idx_iam_user_permission_user_id ON iam_user_permission(user_id);
CREATE INDEX IF NOT EXISTS idx_iam_user_permission_permission_id ON iam_user_permission(permission_id);
CREATE INDEX IF NOT EXISTS idx_iam_user_permission_granted_at ON iam_user_permission(granted_at DESC);

--rollback DROP INDEX IF EXISTS idx_iam_user_permission_user_id;
--rollback DROP INDEX IF EXISTS idx_iam_user_permission_permission_id;
--rollback DROP INDEX IF EXISTS idx_iam_user_permission_granted_at;
