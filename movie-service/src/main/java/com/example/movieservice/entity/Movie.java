package com.example.movieservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movie_id")
    private Integer movieId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String genre;

    /** Runtime in minutes. Column is `duration`, not `duration_minutes`. */
    private Integer duration;

    private String language;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    /** Critic score out of 10, one decimal place. */
    @Column(precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(name = "poster_url")
    private String posterUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovieStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
