package com.example.authservice.entity;

/**
 * The closed set of roles, mapped @Enumerated(EnumType.STRING).
 *
 * This was a free-text String until authorization was added, which meant a
 * hand-edited row reading "Admin" or "ADMINN" produced a user that passed
 * no check and failed none either — the authority became ROLE_ADMINN and
 * every hasRole('ADMIN') simply returned false, silently. A closed set
 * turns that typo into a startup or write failure instead of a
 * privilege-shaped hole.
 *
 * Same reasoning as SeatStatus and BookingStatus elsewhere in the system.
 */
public enum Role {

    /** Everyone who registers. Books seats, pays, reads the catalogue. */
    USER,

    /** Operator: manages the catalogue and the admin surface. */
    ADMIN
}
