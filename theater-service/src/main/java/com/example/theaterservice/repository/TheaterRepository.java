package com.example.theaterservice.repository;

import com.example.theaterservice.entity.Theater;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TheaterRepository
        extends JpaRepository<Theater, Integer> {

}