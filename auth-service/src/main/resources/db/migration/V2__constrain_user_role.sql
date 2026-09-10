-- role is now the Role enum (USER, ADMIN) rather than free text.
--
-- The first admin is made by hand, so this column is the one place in the
-- system where a typo grants nothing and reports nothing: "Admin" becomes
-- the authority ROLE_Admin, every hasRole('ADMIN') returns false, and the
-- account looks promoted while behaving exactly like a normal user. The
-- CHECK turns that into a rejected UPDATE.

-- Normalise anything already in the table before the constraint is added,
-- otherwise the ALTER fails on legacy rows.
UPDATE users SET role = UPPER(TRIM(role));
UPDATE users SET role = 'USER' WHERE role NOT IN ('USER', 'ADMIN');

ALTER TABLE users
    ADD CONSTRAINT chk_users_role
        CHECK (role IN ('USER', 'ADMIN'));
