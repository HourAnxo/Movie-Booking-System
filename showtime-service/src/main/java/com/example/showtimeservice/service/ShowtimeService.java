package com.example.showtimeservice.service;

import com.example.showtimeservice.dto.ShowtimeRequestDTO;
import com.example.showtimeservice.dto.ShowtimeResponseDTO;

import java.util.List;

public interface ShowtimeService {

    ShowtimeResponseDTO createShowtime(
            ShowtimeRequestDTO request
    );

    List<ShowtimeResponseDTO> getAllShowtimes();

    ShowtimeResponseDTO getShowtimeById(
            Integer id
    );

    ShowtimeResponseDTO updateShowtime(
            Integer id,
            ShowtimeRequestDTO request
    );

    void deleteShowtime(
            Integer id
    );
}