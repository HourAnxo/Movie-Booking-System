package com.example.bookingservice.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The part of showtime-service's response booking-service cares about:
 * when the show starts. showtime-service sends more fields; Jackson ignores
 * the rest.
 *
 * showDate and startTime carry no zone. They are wall-clock times at the
 * cinema, read in booking.showtime-zone.
 */
public record ShowtimeDTO(

        Integer showtimeId,

        LocalDate showDate,

        LocalTime startTime

) {
}
