CREATE TABLE movies (
                        movie_id INT NOT NULL AUTO_INCREMENT,

                        title VARCHAR(255) NOT NULL,

                        description TEXT,

                        genre VARCHAR(100) DEFAULT NULL,

                        duration INT DEFAULT NULL,

                        language VARCHAR(50) DEFAULT NULL,

                        release_date DATE DEFAULT NULL,

                        rating DECIMAL(3, 1) DEFAULT NULL,

                        poster_url VARCHAR(500) DEFAULT NULL,

                        created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,

                        PRIMARY KEY (movie_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
