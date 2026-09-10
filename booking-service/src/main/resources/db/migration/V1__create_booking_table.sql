CREATE TABLE bookings (
                          booking_id INT AUTO_INCREMENT PRIMARY KEY,
                          user_id INT NOT NULL,
                          showtime_id INT NOT NULL,
                          seat_id INT NOT NULL,
                          booking_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                          total_amount DECIMAL(10,2) NOT NULL,
                          created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);