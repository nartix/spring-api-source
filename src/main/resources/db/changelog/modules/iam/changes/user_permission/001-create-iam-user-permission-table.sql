--liquibase formatted sql

--changeset feroz:20260430-009-create-iam-user-permission-table
CREATE TABLE IF NOT EXISTS iam_user_permission
(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(255),
    CONSTRAINT uc_iam_user_permission UNIQUE(user_id, permission_id),
    CONSTRAINT fk_iam_user_permission_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_iam_user_permission_permission FOREIGN KEY (permission_id) REFERENCES iam_permission(id) ON DELETE CASCADE
);

--rollback DROP TABLE IF EXISTS iam_user_permission;
