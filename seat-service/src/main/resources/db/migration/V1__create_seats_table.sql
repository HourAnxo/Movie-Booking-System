CREATE TABLE seats (
                       seat_id INT AUTO_INCREMENT PRIMARY KEY,

                       screen_id INT NOT NULL,

                       seat_number VARCHAR(10) NOT NULL,

                       seat_type VARCHAR(20) NOT NULL,

                       status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',

                       created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);