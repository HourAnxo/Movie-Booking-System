package com.example.movieservice.dto;

import com.example.movieservice.entity.MovieStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Only title is mandatory — every other column in the movies table is
 * nullable, and the catalogue is populated incrementally.
 *
 * status stays optional on purpose: MovieServiceImpl reads null as "leave
 * it alone" on update and "NOW_SHOWING" on create. Making it @NotNull
 * would break both defaults.
 */
public record MovieRequestDTO(

        @NotBlank(message = "is required")
        @Size(max = 255, message = "must be at most 255 characters")
        String title,

        String description,

        @Size(max = 100, message = "must be at most 100 characters")
        String genre,

        // Minutes. Nullable, but a runtime of zero or less is never right.
        @Positive(message = "must be a positive number of minutes")
        Integer duration,

        @Size(max = 50, message = "must be at most 50 characters")
        String language,

        LocalDate releaseDate,

        // DECIMAL(3,1) — a 0.0-10.0 score with one decimal place.
        @DecimalMin(value = "0.0", message = "must not be below 0.0")
        @DecimalMax(value = "10.0", message = "must not be above 10.0")
        @Digits(integer = 2, fraction = 1,
                message = "must have at most one decimal place")
        BigDecimal rating,

        @Size(max = 500, message = "must be at most 500 characters")
        String posterUrl,

        MovieStatus status
) {
}
