package com.example.screenservice.repository;

import com.example.screenservice.entity.Screen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScreenRepository
        extends JpaRepository<Screen, Integer> {

}