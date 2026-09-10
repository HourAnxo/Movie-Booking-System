package com.example.movieservice.service;

import com.example.movieservice.dto.MovieRequestDTO;
import com.example.movieservice.dto.MovieResponseDTO;
import com.example.movieservice.entity.MovieStatus;

import java.util.List;

public interface MovieService {

    MovieResponseDTO createMovie(
            MovieRequestDTO request
    );

    List<MovieResponseDTO> getAllMovies();

    List<MovieResponseDTO> getMoviesByStatus(
            MovieStatus status
    );

    List<MovieResponseDTO> getMoviesByGenre(
            String genre
    );

    MovieResponseDTO getMovieById(
            Integer id
    );

    MovieResponseDTO updateMovie(
            Integer id,
            MovieRequestDTO request
    );

    void deleteMovie(
            Integer id
    );
}
