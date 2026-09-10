package com.example.movieservice.controller;

import com.example.movieservice.dto.MovieRequestDTO;
import com.example.movieservice.dto.MovieResponseDTO;
import com.example.movieservice.entity.MovieStatus;
import com.example.movieservice.service.MovieService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieService movieService;

    public MovieController(
            MovieService movieService
    ) {
        this.movieService = movieService;
    }


    // CREATE
    @PostMapping
    public ResponseEntity<MovieResponseDTO> createMovie(
            @Valid @RequestBody MovieRequestDTO request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(movieService.createMovie(request));
    }


    // GET ALL
    @GetMapping
    public ResponseEntity<List<MovieResponseDTO>> getAllMovies() {

        return ResponseEntity.ok(
                movieService.getAllMovies()
        );
    }


    // GET BY STATUS
    @GetMapping("/status/{status}")
    public ResponseEntity<List<MovieResponseDTO>> getMoviesByStatus(
            @PathVariable MovieStatus status
    ) {

        return ResponseEntity.ok(
                movieService.getMoviesByStatus(status)
        );
    }


    // GET BY GENRE
    @GetMapping("/genre/{genre}")
    public ResponseEntity<List<MovieResponseDTO>> getMoviesByGenre(
            @PathVariable String genre
    ) {

        return ResponseEntity.ok(
                movieService.getMoviesByGenre(genre)
        );
    }


    // GET BY ID
    @GetMapping("/{id}")
    public ResponseEntity<MovieResponseDTO> getMovieById(
            @PathVariable Integer id
    ) {

        return ResponseEntity.ok(
                movieService.getMovieById(id)
        );
    }


    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<MovieResponseDTO> updateMovie(
            @PathVariable Integer id,
            @Valid @RequestBody MovieRequestDTO request
    ) {

        return ResponseEntity.ok(
                movieService.updateMovie(id, request)
        );
    }


    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMovie(
            @PathVariable Integer id
    ) {

        movieService.deleteMovie(id);

        return ResponseEntity.noContent().build();
    }
}
