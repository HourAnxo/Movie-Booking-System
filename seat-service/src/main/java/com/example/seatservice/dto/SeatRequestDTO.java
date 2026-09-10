package com.example.seatservice.dto;

import com.example.seatservice.entity.SeatStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * status stays optional: SeatServiceImpl reads null as "AVAILABLE" on
 * create and "leave it alone" on update. Note that this DTO is not how a
 * seat gets booked — that goes through the reserve endpoint and its
 * conditional UPDATE, never through a status field on an edit.
 */
public record SeatRequestDTO(

        @NotNull(message = "is required")
        @Positive(message = "must be a positive id")
        Integer screenId,

        @NotBlank(message = "is required")
        @Size(max = 10, message = "must be at most 10 characters")
        String seatNumber,

        @NotBlank(message = "is required")
        @Size(max = 20, message = "must be at most 20 characters")
        String seatType,

        SeatStatus status
) {
}
