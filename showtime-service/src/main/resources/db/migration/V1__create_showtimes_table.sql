CREATE TABLE showtimes (
                           showtime_id INT AUTO_INCREMENT PRIMARY KEY,

                           movie_id INT NOT NULL,
                           theater_id INT NOT NULL,
                           screen_id INT NOT NULL,

                           show_date DATE NOT NULL,
                           start_time TIME NOT NULL,
                           end_time TIME NOT NULL,

                           created_at DATETIME NOT NULL
);