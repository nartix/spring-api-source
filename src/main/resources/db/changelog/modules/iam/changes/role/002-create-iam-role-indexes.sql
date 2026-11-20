--liquibase formatted sql

--changeset feroz:20260430-004-create-iam-role-indexes
CREATE INDEX IF NOT EXISTS idx_iam_role_code ON iam_role(code);
CREATE INDEX IF NOT EXISTS idx_iam_role_name ON iam_role(name);
CREATE INDEX IF NOT EXISTS idx_iam_role_is_active ON iam_role(is_active);

--rollback DROP INDEX IF EXISTS idx_iam_role_code;
--rollback DROP INDEX IF EXISTS idx_iam_role_name;
--rollback DROP INDEX IF EXISTS idx_iam_role_is_active;
