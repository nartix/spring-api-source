--liquibase formatted sql

--changeset feroz:20260430-011-create-iam-role-permission-table
CREATE TABLE IF NOT EXISTS iam_role_permission
(
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    CONSTRAINT uc_iam_role_permission UNIQUE(role_id, permission_id),
    CONSTRAINT fk_iam_role_permission_role FOREIGN KEY (role_id) REFERENCES iam_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_iam_role_permission_permission FOREIGN KEY (permission_id) REFERENCES iam_permission(id) ON DELETE CASCADE
);

--rollback DROP TABLE IF EXISTS iam_role_permission;
