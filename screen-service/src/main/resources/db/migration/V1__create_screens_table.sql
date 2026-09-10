CREATE TABLE screens (
                         screen_id INT AUTO_INCREMENT PRIMARY KEY,
                         theater_id INT NOT NULL,
                         name VARCHAR(100) NOT NULL,
                         screen_type VARCHAR(50),
                         capacity INT NOT NULL,
                         created_at DATETIME NOT NULL
);