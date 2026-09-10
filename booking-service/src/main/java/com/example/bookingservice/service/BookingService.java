package com.example.bookingservice.service;

import com.example.bookingservice.dto.BookingRequestDTO;
import com.example.bookingservice.dto.BookingResponseDTO;

import java.util.List;

public interface BookingService {

    BookingResponseDTO createBooking(
            BookingRequestDTO request
    );

    List<BookingResponseDTO> getAllBookings();

    BookingResponseDTO getBookingById(
            Integer id
    );

    List<BookingResponseDTO> getBookingsByUserId(
            Integer userId
    );

    BookingResponseDTO updateBooking(
            Integer id,
            BookingRequestDTO request
    );

    /** PENDING -> CONFIRMED. Called by payment-service on a paid payment. */
    BookingResponseDTO confirmBooking(Integer id);

    /** -> CANCELLED, and releases the seat. */
    BookingResponseDTO cancelBooking(Integer id);

    void deleteBooking(
            Integer id
    );
}
