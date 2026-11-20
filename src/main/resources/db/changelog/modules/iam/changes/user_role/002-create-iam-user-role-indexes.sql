--liquibase formatted sql

--changeset feroz:20260430-008-create-iam-user-role-indexes
CREATE INDEX IF NOT EXISTS idx_iam_user_role_user_id ON iam_user_role(user_id);
CREATE INDEX IF NOT EXISTS idx_iam_user_role_role_id ON iam_user_role(role_id);
CREATE INDEX IF NOT EXISTS idx_iam_user_role_assigned_at ON iam_user_role(assigned_at DESC);

--rollback DROP INDEX IF EXISTS idx_iam_user_role_user_id;
--rollback DROP INDEX IF EXISTS idx_iam_user_role_role_id;
--rollback DROP INDEX IF EXISTS idx_iam_user_role_assigned_at;
