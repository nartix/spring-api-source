--liquibase formatted sql

--changeset feroz:20260601-003-create-iam-user-case-insensitive-indexes
CREATE UNIQUE INDEX IF NOT EXISTS ux_iam_user_username_ci ON iam_user (lower(username));
CREATE UNIQUE INDEX IF NOT EXISTS ux_iam_user_email_ci ON iam_user (lower(email));

--rollback DROP INDEX IF EXISTS ux_iam_user_username_ci;
--rollback DROP INDEX IF EXISTS ux_iam_user_email_ci;
