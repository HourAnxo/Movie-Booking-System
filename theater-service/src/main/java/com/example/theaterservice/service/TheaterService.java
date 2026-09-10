package com.example.theaterservice.service;

import com.example.theaterservice.dto.TheaterRequestDTO;
import com.example.theaterservice.dto.TheaterResponseDTO;

import java.util.List;

public interface TheaterService {

    TheaterResponseDTO createTheater(
            TheaterRequestDTO request
    );

    List<TheaterResponseDTO> getAllTheaters();

    TheaterResponseDTO getTheaterById(
            Integer id
    );

    TheaterResponseDTO updateTheater(
            Integer id,
            TheaterRequestDTO request
    );

    void deleteTheater(
            Integer id
    );
}