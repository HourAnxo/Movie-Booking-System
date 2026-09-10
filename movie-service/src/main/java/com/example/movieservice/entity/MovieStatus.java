package com.example.movieservice.entity;

public enum MovieStatus {

    /** Showing now — showtimes can be scheduled against it. */
    NOW_SHOWING,

    /** Announced but not yet released. */
    COMING_SOON,

    /** Run finished; kept for historical bookings. */
    ARCHIVED
}
