--liquibase formatted sql

--changeset feroz:20260430-007-create-iam-user-role-table
CREATE TABLE IF NOT EXISTS iam_user_role
(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(255),
    CONSTRAINT uc_iam_user_role UNIQUE(user_id, role_id),
    CONSTRAINT fk_iam_user_role_user FOREIGN KEY (user_id) REFERENCES iam_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_iam_user_role_role FOREIGN KEY (role_id) REFERENCES iam_role(id) ON DELETE CASCADE
);

--rollback DROP TABLE IF EXISTS iam_user_role;
