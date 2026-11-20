--liquibase formatted sql

--changeset feroz:20260430-012-create-iam-role-permission-indexes
CREATE INDEX IF NOT EXISTS idx_iam_role_permission_role_id ON iam_role_permission(role_id);
CREATE INDEX IF NOT EXISTS idx_iam_role_permission_permission_id ON iam_role_permission(permission_id);
CREATE INDEX IF NOT EXISTS idx_iam_role_permission_granted_at ON iam_role_permission(granted_at DESC);

--rollback DROP INDEX IF EXISTS idx_iam_role_permission_role_id;
--rollback DROP INDEX IF EXISTS idx_iam_role_permission_permission_id;
--rollback DROP INDEX IF EXISTS idx_iam_role_permission_granted_at;
