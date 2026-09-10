package com.example.movieservice.service;

import com.example.movieservice.dto.MovieRequestDTO;
import com.example.movieservice.dto.MovieResponseDTO;
import com.example.movieservice.entity.Movie;
import com.example.movieservice.entity.MovieStatus;
import com.example.movieservice.exception.ResourceNotFoundException;
import com.example.movieservice.repository.MovieRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MovieServiceImpl implements MovieService {

    private final MovieRepository movieRepository;

    public MovieServiceImpl(
            MovieRepository movieRepository
    ) {
        this.movieRepository = movieRepository;
    }


    // CREATE
    @Override
    public MovieResponseDTO createMovie(
            MovieRequestDTO request
    ) {

        Movie movie = Movie.builder()
                .title(request.title())
                .description(request.description())
                .genre(request.genre())
                .duration(request.duration())
                .language(request.language())
                .releaseDate(request.releaseDate())
                .rating(request.rating())
                .posterUrl(request.posterUrl())
                .status(
                        request.status() != null
                                ? request.status()
                                : MovieStatus.NOW_SHOWING
                )
                .createdAt(LocalDateTime.now())
                .build();

        Movie savedMovie = movieRepository.save(movie);

        return mapToResponse(savedMovie);
    }


    // GET ALL
    @Override
    public List<MovieResponseDTO> getAllMovies() {

        return movieRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // GET BY STATUS
    @Override
    public List<MovieResponseDTO> getMoviesByStatus(
            MovieStatus status
    ) {

        return movieRepository.findByStatus(status)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // GET BY GENRE
    @Override
    public List<MovieResponseDTO> getMoviesByGenre(
            String genre
    ) {

        return movieRepository.findByGenreIgnoreCase(genre)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // GET BY ID
    @Override
    public MovieResponseDTO getMovieById(
            Integer id
    ) {

        return mapToResponse(findMovieById(id));
    }


    // UPDATE
    @Override
    public MovieResponseDTO updateMovie(
            Integer id,
            MovieRequestDTO request
    ) {

        Movie movie = findMovieById(id);

        movie.setTitle(request.title());
        movie.setDescription(request.description());
        movie.setGenre(request.genre());
        movie.setDuration(request.duration());
        movie.setLanguage(request.language());
        movie.setReleaseDate(request.releaseDate());
        movie.setRating(request.rating());
        movie.setPosterUrl(request.posterUrl());

        if (request.status() != null) {
            movie.setStatus(request.status());
        }

        Movie updatedMovie = movieRepository.save(movie);

        return mapToResponse(updatedMovie);
    }


    // DELETE
    @Override
    public void deleteMovie(
            Integer id
    ) {

        movieRepository.delete(findMovieById(id));
    }


    private Movie findMovieById(
            Integer id
    ) {

        return movieRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Movie", id)
                );
    }


    // MAP ENTITY TO RESPONSE DTO
    private MovieResponseDTO mapToResponse(
            Movie movie
    ) {

        return new MovieResponseDTO(
                movie.getMovieId(),
                movie.getTitle(),
                movie.getDescription(),
                movie.getGenre(),
                movie.getDuration(),
                movie.getLanguage(),
                movie.getReleaseDate(),
                movie.getRating(),
                movie.getPosterUrl(),
                movie.getStatus(),
                movie.getCreatedAt()
        );
    }
}
