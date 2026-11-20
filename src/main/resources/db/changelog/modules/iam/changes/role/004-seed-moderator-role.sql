--liquibase formatted sql

--changeset feroz:20260606-003-seed-moderator-role
INSERT INTO iam_role (code, name, description, is_active, created_by, updated_by)
VALUES
    ('MODERATOR', 'Moderator', 'Limited admin moderation role', true, 'system', 'system')
ON CONFLICT (code) DO NOTHING;

--rollback DELETE FROM iam_role WHERE code = 'MODERATOR';
