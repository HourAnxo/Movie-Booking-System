package com.example.showtimeservice.repository;

import com.example.showtimeservice.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShowtimeRepository
        extends JpaRepository<Showtime, Integer> {

}