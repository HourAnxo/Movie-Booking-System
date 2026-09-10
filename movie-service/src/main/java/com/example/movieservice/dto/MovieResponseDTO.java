package com.example.movieservice.dto;

import com.example.movieservice.entity.MovieStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MovieResponseDTO(

        Integer movieId,

        String title,

        String description,

        String genre,

        Integer duration,

        String language,

        LocalDate releaseDate,

        BigDecimal rating,

        String posterUrl,

        MovieStatus status,

        LocalDateTime createdAt

) {
}
