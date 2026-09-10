CREATE TABLE payments (
                          payment_id INT AUTO_INCREMENT PRIMARY KEY,

                          booking_id INT NOT NULL,

                          amount DECIMAL(10,2) NOT NULL,

                          payment_method VARCHAR(50) NOT NULL,

                          payment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

                          transaction_id VARCHAR(100),

                          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

                          updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP
);