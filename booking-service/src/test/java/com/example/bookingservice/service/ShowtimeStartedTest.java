package com.example.bookingservice.service;

import com.example.bookingservice.dto.ShowtimeDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When a showtime counts as started. Showtimes are wall-clock times at the
 * cinema with no zone, so the same "14:00" is a different instant depending
 * on booking.showtime-zone. Plain unit test: no Spring, no database.
 */
@DisplayName("Showtime has started")
class ShowtimeStartedTest {

    private static final ZoneId PHNOM_PENH = ZoneId.of("Asia/Phnom_Penh"); // UTC+7

    // 14:00 in Phnom Penh is 07:00 UTC.
    private static final ShowtimeDTO SHOW =
            new ShowtimeDTO(1, LocalDate.of(2026, 11, 5), LocalTime.of(14, 0));

    @Test
    @DisplayName("a minute before the start, it can still be booked")
    void beforeStart() {
        assertThat(BookingServiceImpl.hasStarted(SHOW, PHNOM_PENH,
                Instant.parse("2026-11-05T06:59:00Z"))).isFalse();
    }

    @Test
    @DisplayName("at the start time exactly, it can no longer be booked")
    void atStart() {
        assertThat(BookingServiceImpl.hasStarted(SHOW, PHNOM_PENH,
                Instant.parse("2026-11-05T07:00:00Z"))).isTrue();
    }

    @Test
    @DisplayName("a show from months ago has started")
    void longPast() {
        ShowtimeDTO september = new ShowtimeDTO(37, LocalDate.of(2026, 9, 5), LocalTime.of(10, 0));

        assertThat(BookingServiceImpl.hasStarted(september, PHNOM_PENH,
                Instant.parse("2026-11-01T06:05:42Z"))).isTrue();
    }

    @Test
    @DisplayName("the zone matters: 14:00 in Phnom Penh has started when 14:00 UTC hasn't")
    void zoneIsApplied() {
        Instant tenAmUtc = Instant.parse("2026-11-05T10:00:00Z"); // 17:00 in Phnom Penh

        assertThat(BookingServiceImpl.hasStarted(SHOW, PHNOM_PENH, tenAmUtc)).isTrue();
        assertThat(BookingServiceImpl.hasStarted(SHOW, ZoneId.of("UTC"), tenAmUtc)).isFalse();
    }
}
