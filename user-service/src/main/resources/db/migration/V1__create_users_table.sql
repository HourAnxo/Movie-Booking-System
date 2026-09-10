CREATE TABLE users (
                       user_id INT AUTO_INCREMENT PRIMARY KEY,

                       name VARCHAR(100) NOT NULL,

                       email VARCHAR(150) NOT NULL UNIQUE,

                       phone VARCHAR(20),



                       created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                       updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                           ON UPDATE CURRENT_TIMESTAMP
);