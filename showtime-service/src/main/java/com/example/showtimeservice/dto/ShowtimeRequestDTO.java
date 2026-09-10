package com.example.showtimeservice.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalTime;

public record ShowtimeRequestDTO(

        @NotNull(message = "is required")
        @Positive(message = "must be a positive id")
        Integer movieId,

        @NotNull(message = "is required")
        @Positive(message = "must be a positive id")
        Integer theaterId,

        @NotNull(message = "is required")
        @Positive(message = "must be a positive id")
        Integer screenId,

        @NotNull(message = "is required")
        LocalDate showDate,

        @NotNull(message = "is required")
        LocalTime startTime,

        @NotNull(message = "is required")
        LocalTime endTime
) {

    /**
     * The one rule that spans two fields, so it cannot live on either.
     * Bean Validation treats an isXxx() method as a property and reports
     * this as a violation on "endAfterStartTime".
     *
     * Returns true when either time is missing — @NotNull already reports
     * that, and a second message about the same omission is noise.
     *
     * A show that ends past midnight is genuinely not expressible: the
     * table stores two TIME columns and one date, so 23:00-01:00 has no
     * representation. Supporting it means a schema change, not a looser
     * check here.
     */
    @AssertTrue(message = "must be after startTime")
    public boolean isEndAfterStartTime() {

        if (startTime == null || endTime == null) {
            return true;
        }

        return endTime.isAfter(startTime);
    }
}
