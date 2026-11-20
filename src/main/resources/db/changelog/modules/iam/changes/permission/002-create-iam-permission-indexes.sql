--liquibase formatted sql

--changeset feroz:20260430-006-create-iam-permission-indexes
CREATE INDEX IF NOT EXISTS idx_iam_permission_code ON iam_permission(code);
CREATE INDEX IF NOT EXISTS idx_iam_permission_name ON iam_permission(name);
CREATE INDEX IF NOT EXISTS idx_iam_permission_resource ON iam_permission(resource);
CREATE INDEX IF NOT EXISTS idx_iam_permission_action ON iam_permission(action);
CREATE INDEX IF NOT EXISTS idx_iam_permission_is_active ON iam_permission(is_active);

--rollback DROP INDEX IF EXISTS idx_iam_permission_code;
--rollback DROP INDEX IF EXISTS idx_iam_permission_name;
--rollback DROP INDEX IF EXISTS idx_iam_permission_resource;
--rollback DROP INDEX IF EXISTS idx_iam_permission_action;
--rollback DROP INDEX IF EXISTS idx_iam_permission_is_active;
