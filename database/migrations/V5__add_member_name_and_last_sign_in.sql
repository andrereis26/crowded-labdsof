-- Platform administration (#140, FR-087): the administrator names each member
-- and sees when they last signed in. Participants have no name: they register
-- with their email only (#59).

ALTER TABLE app_user
  ADD COLUMN name text,
  ADD COLUMN last_signed_in_at timestamptz;

-- Existing members are demo accounts: they are named after their email.
UPDATE app_user SET name = initcap(replace(split_part(email, '@', 1), '.', ' '))
  WHERE role = 'ORGANIZATION_MEMBER' AND name IS NULL;

ALTER TABLE app_user
  ADD CONSTRAINT app_user_member_has_name
    CHECK (role <> 'ORGANIZATION_MEMBER' OR name IS NOT NULL);

-- Two organizations cannot share a name, whatever its case.
CREATE UNIQUE INDEX organization_name_key ON organization (lower(name));
