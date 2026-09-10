package com.example.showtimeservice.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ShowtimeResponseDTO(

        Integer showtimeId,

        Integer movieId,

        Integer theaterId,

        Integer screenId,

        LocalDate showDate,

        LocalTime startTime,

        LocalTime endTime,

        LocalDateTime createdAt

) {
}