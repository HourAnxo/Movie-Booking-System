package com.example.screenservice.service;

import com.example.screenservice.dto.ScreenRequestDTO;
import com.example.screenservice.dto.ScreenResponseDTO;

import java.util.List;

public interface ScreenService {

    ScreenResponseDTO createScreen(
            ScreenRequestDTO request
    );

    List<ScreenResponseDTO> getAllScreens();

    ScreenResponseDTO getScreenById(
            Integer id
    );

    ScreenResponseDTO updateScreen(
            Integer id,
            ScreenRequestDTO request
    );

    void deleteScreen(
            Integer id
    );
}