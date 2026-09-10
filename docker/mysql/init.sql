-- Runs once, on an empty data volume, before any service starts.
--
-- It creates the databases and nothing else: every table in them is owned
-- by that service's Flyway migrations, which run at its startup. Adding a
-- CREATE TABLE here would produce a schema Flyway did not record, and
-- ddl-auto=validate would then fail against it.
--
-- The collation matches what the V1 migrations declare, so a container
-- database and a hand-made local one converge on the same schema.

CREATE DATABASE IF NOT EXISTS auth_db     CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS user_db     CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS movie_db    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS theater_db  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS screen_db   CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS showtime_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS seat_db     CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS booking_db  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS payment_db  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
