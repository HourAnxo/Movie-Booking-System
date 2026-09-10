CREATE TABLE users (
                       user_id BIGINT NOT NULL AUTO_INCREMENT,

                       username VARCHAR(100) NOT NULL,

                       email VARCHAR(150) NOT NULL,

                       password VARCHAR(255) NOT NULL,

                       role VARCHAR(50) NOT NULL DEFAULT 'USER',

                       created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,

                       PRIMARY KEY (user_id),
                       UNIQUE KEY uq_users_username (username),
                       UNIQUE KEY uq_users_email (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
