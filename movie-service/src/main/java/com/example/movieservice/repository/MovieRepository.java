package com.example.movieservice.repository;

import com.example.movieservice.entity.Movie;
import com.example.movieservice.entity.MovieStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieRepository
        extends JpaRepository<Movie, Integer> {

    List<Movie> findByStatus(MovieStatus status);

    List<Movie> findByGenreIgnoreCase(String genre);
}
