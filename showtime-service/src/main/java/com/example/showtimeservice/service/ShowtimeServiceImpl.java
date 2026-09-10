package com.example.showtimeservice.service;

import com.example.showtimeservice.exception.ResourceNotFoundException;
import com.example.showtimeservice.dto.ShowtimeRequestDTO;
import com.example.showtimeservice.dto.ShowtimeResponseDTO;
import com.example.showtimeservice.entity.Showtime;
import com.example.showtimeservice.repository.ShowtimeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShowtimeServiceImpl implements ShowtimeService {

    private final ShowtimeRepository showtimeRepository;

    public ShowtimeServiceImpl(
            ShowtimeRepository showtimeRepository
    ) {
        this.showtimeRepository = showtimeRepository;
    }

    // CREATE
    @Override
    public ShowtimeResponseDTO createShowtime(
            ShowtimeRequestDTO request
    ) {

        Showtime showtime = Showtime.builder()
                .movieId(request.movieId())
                .theaterId(request.theaterId())
                .screenId(request.screenId())
                .showDate(request.showDate())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .createdAt(LocalDateTime.now())
                .build();

        Showtime savedShowtime =
                showtimeRepository.save(showtime);

        return mapToResponse(savedShowtime);
    }

    // GET ALL
    @Override
    public List<ShowtimeResponseDTO> getAllShowtimes() {

        return showtimeRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // GET BY ID
    @Override
    public ShowtimeResponseDTO getShowtimeById(
            Integer id
    ) {

        Showtime showtime = showtimeRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Showtime", id)
                );

        return mapToResponse(showtime);
    }

    // UPDATE
    @Override
    public ShowtimeResponseDTO updateShowtime(
            Integer id,
            ShowtimeRequestDTO request
    ) {

        Showtime showtime = showtimeRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Showtime", id)
                );

        showtime.setMovieId(request.movieId());
        showtime.setTheaterId(request.theaterId());
        showtime.setScreenId(request.screenId());
        showtime.setShowDate(request.showDate());
        showtime.setStartTime(request.startTime());
        showtime.setEndTime(request.endTime());

        Showtime updatedShowtime =
                showtimeRepository.save(showtime);

        return mapToResponse(updatedShowtime);
    }

    // DELETE
    @Override
    public void deleteShowtime(Integer id) {

        Showtime showtime = showtimeRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Showtime", id)
                );

        showtimeRepository.delete(showtime);
    }

    // MAP TO RESPONSE
    private ShowtimeResponseDTO mapToResponse(
            Showtime showtime
    ) {

        return new ShowtimeResponseDTO(
                showtime.getShowtimeId(),
                showtime.getMovieId(),
                showtime.getTheaterId(),
                showtime.getScreenId(),
                showtime.getShowDate(),
                showtime.getStartTime(),
                showtime.getEndTime(),
                showtime.getCreatedAt()
        );
    }
}