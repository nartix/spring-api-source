--liquibase formatted sql

--changeset feroz:20260529-002-grant-admin-api-authorization-permissions
INSERT INTO iam_role_permission (role_id, permission_id, created_by)
SELECT role.id, permission.id, 'system'
FROM iam_role role
JOIN iam_permission permission
    ON permission.code IN ('users:read', 'users:write', 'categories:read', 'categories:write')
WHERE role.code = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

--rollback DELETE FROM iam_role_permission role_permission USING iam_role role, iam_permission permission WHERE role_permission.role_id = role.id AND role_permission.permission_id = permission.id AND role.code = 'ADMIN' AND permission.code IN ('users:read', 'users:write', 'categories:read', 'categories:write');
