package com.example.bookingservice.controller;

import com.example.bookingservice.dto.BookingRequestDTO;
import com.example.bookingservice.dto.BookingResponseDTO;
import com.example.bookingservice.service.BookingService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(
            BookingService bookingService
    ) {
        this.bookingService = bookingService;
    }


    // CREATE — reserves the seat, then records a PENDING booking
    @PostMapping
    public ResponseEntity<BookingResponseDTO> createBooking(
            @Valid @RequestBody BookingRequestDTO request
    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        bookingService.createBooking(request)
                );
    }


    // GET ALL
    @GetMapping
    public ResponseEntity<List<BookingResponseDTO>> getAllBookings() {

        return ResponseEntity.ok(
                bookingService.getAllBookings()
        );
    }


    // GET BY ID
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponseDTO> getBookingById(
            @PathVariable Integer id
    ) {

        return ResponseEntity.ok(
                bookingService.getBookingById(id)
        );
    }


    // GET BY USER
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BookingResponseDTO>> getBookingsByUserId(
            @PathVariable Integer userId
    ) {

        return ResponseEntity.ok(
                bookingService.getBookingsByUserId(userId)
        );
    }


    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<BookingResponseDTO> updateBooking(
            @PathVariable Integer id,
            @Valid @RequestBody BookingRequestDTO request
    ) {

        return ResponseEntity.ok(
                bookingService.updateBooking(id, request)
        );
    }


    // CONFIRM — called by payment-service once payment is PAID
    @PutMapping("/{id}/confirm")
    public ResponseEntity<BookingResponseDTO> confirmBooking(
            @PathVariable Integer id
    ) {

        return ResponseEntity.ok(
                bookingService.confirmBooking(id)
        );
    }


    // CANCEL — releases the seat
    @PutMapping("/{id}/cancel")
    public ResponseEntity<BookingResponseDTO> cancelBooking(
            @PathVariable Integer id
    ) {

        return ResponseEntity.ok(
                bookingService.cancelBooking(id)
        );
    }


    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBooking(
            @PathVariable Integer id
    ) {

        bookingService.deleteBooking(id);

        return ResponseEntity.noContent().build();
    }
}
