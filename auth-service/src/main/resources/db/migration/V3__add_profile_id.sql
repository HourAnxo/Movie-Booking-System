-- Links a credential row in auth_db to its profile row in user_db.
--
-- Until now auth-service called user-service on register and threw the
-- response away, so nothing anywhere connected a username to the userId
-- that bookings are keyed on. A client could authenticate and still had no
-- way to say who it was booking for.
--
-- Nullable on purpose: rows created before this migration have no link
-- yet. AuthServiceImpl.login backfills one lazily via user-service's
-- lookup-by-email rather than a cross-database UPDATE here — auth_db must
-- not read user_db's tables, even though they share a MySQL instance.
ALTER TABLE users
    ADD COLUMN profile_id INT NULL AFTER role;

-- One credential row per profile. Catches a double-register that somehow
-- produced two logins pointing at the same person.
ALTER TABLE users
    ADD CONSTRAINT uq_users_profile_id UNIQUE (profile_id);
