package com.example.bookingservice.service;

import com.example.bookingservice.client.SeatServiceClient;
import com.example.bookingservice.client.ShowtimeServiceClient;
import com.example.bookingservice.client.UserServiceClient;
import com.example.bookingservice.dto.BookingRequestDTO;
import com.example.bookingservice.dto.BookingResponseDTO;
import com.example.bookingservice.dto.ReservedSeatDTO;
import com.example.bookingservice.dto.ShowtimeDTO;
import com.example.bookingservice.entity.Booking;
import com.example.bookingservice.entity.BookingStatus;
import com.example.bookingservice.exception.ResourceNotFoundException;
import com.example.bookingservice.exception.ShowtimeNotBookableException;
import com.example.bookingservice.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Service
public class BookingServiceImpl implements BookingService {

    private static final Logger log =
            LoggerFactory.getLogger(BookingServiceImpl.class);

    private final BookingRepository bookingRepository;
    private final SeatServiceClient seatServiceClient;
    private final UserServiceClient userServiceClient;
    private final ShowtimeServiceClient showtimeServiceClient;
    private final ZoneId showtimeZone;

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            SeatServiceClient seatServiceClient,
            UserServiceClient userServiceClient,
            ShowtimeServiceClient showtimeServiceClient,
            @Value("${booking.showtime-zone}") String showtimeZone
    ) {
        this.bookingRepository = bookingRepository;
        this.seatServiceClient = seatServiceClient;
        this.userServiceClient = userServiceClient;
        this.showtimeServiceClient = showtimeServiceClient;
        this.showtimeZone = ZoneId.of(showtimeZone);
    }


    /**
     * True once the show's start, read as wall-clock time in the cinema's
     * zone, is at or before now. A show is bookable only strictly before
     * it starts, which is also what the Book page offers.
     */
    static boolean hasStarted(ShowtimeDTO showtime, ZoneId zone, Instant now) {

        Instant start = showtime.showDate()
                .atTime(showtime.startTime())
                .atZone(zone)
                .toInstant();

        return !start.isAfter(now);
    }


    // CREATE BOOKING
    /**
     * Validates the user and the showtime, then reserves the seat, then
     * persists. Both checks come first because they have no side effect —
     * failing one after reserving would mean holding and releasing a seat
     * for a request that was never going to succeed.
     *
     * A showtime that doesn't exist is 400, and one that has started is 400
     * too: the Book page hides started shows, but nothing else stopped a
     * direct API call from booking a show that was already over.
     *
     * If seat-service refuses (409) the exception propagates and no
     * booking row is written, so a lost race leaves no orphan PENDING
     * booking.
     *
     * Deliberately NOT @Transactional: the remote calls cannot join a
     * local transaction. The compensating release below is what undoes
     * the reservation.
     */
    @Override
    public BookingResponseDTO createBooking(
            BookingRequestDTO request
    ) {

        userServiceClient.requireUserExists(request.userId());

        ShowtimeDTO showtime =
                showtimeServiceClient.getShowtime(request.showtimeId());

        if (hasStarted(showtime, showtimeZone, Instant.now())) {
            throw new ShowtimeNotBookableException(
                    "Showtime " + request.showtimeId()
                            + " has already started and can no longer be booked"
            );
        }

        ReservedSeatDTO seat =
                seatServiceClient.reserveSeat(
                        request.seatId(),
                        request.showtimeId()
                );

        try {

            // The price is the seat's, never the client's. payment-service
            // builds a real Bakong QR for exactly this amount, so a total
            // taken from the request body would let a caller name their
            // own price.
            if (seat == null || seat.price() == null) {
                throw new IllegalStateException(
                        "seat-service returned no price for seat "
                                + request.seatId()
                );
            }

            Booking booking = Booking.builder()
                    .userId(request.userId())
                    .showtimeId(request.showtimeId())
                    .seatId(request.seatId())
                    .bookingStatus(BookingStatus.PENDING)
                    .totalAmount(seat.price())
                    .createdAt(LocalDateTime.now())
                    .build();

            Booking savedBooking =
                    bookingRepository.save(booking);

            return mapToResponse(savedBooking);

        } catch (RuntimeException ex) {

            // The seat is held but we could not record the booking.
            // Give the seat back rather than leaking it forever.
            log.error(
                    "Booking insert failed after reserving seat {} — releasing",
                    request.seatId(),
                    ex
            );

            safeReleaseSeat(request.seatId(), request.showtimeId());

            throw ex;
        }
    }


    // GET ALL BOOKINGS
    @Override
    public List<BookingResponseDTO> getAllBookings() {

        return bookingRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // GET BOOKING BY ID
    @Override
    public BookingResponseDTO getBookingById(
            Integer id
    ) {

        return mapToResponse(findBookingById(id));
    }


    // GET BOOKINGS BY USER
    @Override
    public List<BookingResponseDTO> getBookingsByUserId(
            Integer userId
    ) {

        return bookingRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // UPDATE BOOKING
    @Override
    public BookingResponseDTO updateBooking(
            Integer id,
            BookingRequestDTO request
    ) {

        Booking booking = findBookingById(id);

        // The seat is held for this booking's showtime, so moving the
        // booking to another show would leave the old show's seat held and
        // the new show's seat unreserved. Same rule as seatId below.
        if (!Objects.equals(
                booking.getShowtimeId(),
                request.showtimeId()
        )) {
            throw new IllegalArgumentException(
                    "showtimeId cannot be changed; cancel the booking "
                            + "and book the other showtime"
            );
        }

        // Only pay for the remote check when the owner actually changes.
        if (!Objects.equals(
                booking.getUserId(),
                request.userId()
        )) {
            userServiceClient.requireUserExists(request.userId());
        }

        booking.setUserId(request.userId());

        // totalAmount is not updatable either: it is the reserved seat's
        // price, and payment-service charges exactly that.
        //
        // seatId is intentionally not updatable here — moving a booking to
        // a different seat means releasing one seat and reserving another,
        // which is a cancel + re-book, not a field edit.

        Booking updatedBooking =
                bookingRepository.save(booking);

        return mapToResponse(updatedBooking);
    }


    // CONFIRM BOOKING — called by payment-service once payment is PAID
    @Override
    public BookingResponseDTO confirmBooking(Integer id) {

        Booking booking = findBookingById(id);

        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "Cannot confirm a cancelled booking: " + id
            );
        }

        booking.setBookingStatus(BookingStatus.CONFIRMED);

        return mapToResponse(bookingRepository.save(booking));
    }


    // CANCEL BOOKING — user cancels, or payment-service reports failure
    @Override
    public BookingResponseDTO cancelBooking(Integer id) {

        Booking booking = findBookingById(id);

        // Already cancelled: return as-is so a retried call is harmless.
        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
            return mapToResponse(booking);
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);

        Booking cancelledBooking =
                bookingRepository.save(booking);

        // Compensating action: hand the seat back so it can be resold.
        safeReleaseSeat(booking.getSeatId(), booking.getShowtimeId());

        return mapToResponse(cancelledBooking);
    }


    // DELETE BOOKING
    @Override
    public void deleteBooking(Integer id) {

        Booking booking = findBookingById(id);

        bookingRepository.delete(booking);

        if (booking.getBookingStatus() != BookingStatus.CANCELLED) {
            safeReleaseSeat(booking.getSeatId(), booking.getShowtimeId());
        }
    }


    private Booking findBookingById(Integer id) {

        return bookingRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Booking", id)
                );
    }


    /**
     * Releasing a seat must never mask the outcome of the operation that
     * triggered it — a booking that is cancelled stays cancelled even if
     * seat-service is momentarily down. The seat is left stuck BOOKED and
     * the failure is logged for reconciliation.
     */
    private void safeReleaseSeat(Integer seatId, Integer showtimeId) {

        try {
            seatServiceClient.releaseSeat(seatId, showtimeId);
        } catch (RuntimeException ex) {
            log.error(
                    "Failed to release seat {} for showtime {} — it may "
                            + "stay BOOKED for that show until reconciled",
                    seatId,
                    showtimeId,
                    ex
            );
        }
    }


    // MAP ENTITY TO DTO
    private BookingResponseDTO mapToResponse(
            Booking booking
    ) {

        return new BookingResponseDTO(
                booking.getBookingId(),
                booking.getUserId(),
                booking.getShowtimeId(),
                booking.getSeatId(),
                booking.getBookingStatus(),
                booking.getTotalAmount(),
                booking.getCreatedAt()
        );
    }
}
