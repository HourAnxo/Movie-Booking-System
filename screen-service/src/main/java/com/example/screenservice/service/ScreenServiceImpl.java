package com.example.screenservice.service;

import com.example.screenservice.exception.ResourceNotFoundException;
import com.example.screenservice.dto.ScreenRequestDTO;
import com.example.screenservice.dto.ScreenResponseDTO;
import com.example.screenservice.entity.Screen;
import com.example.screenservice.repository.ScreenRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScreenServiceImpl implements ScreenService {

    private final ScreenRepository screenRepository;

    public ScreenServiceImpl(ScreenRepository screenRepository) {
        this.screenRepository = screenRepository;
    }

    // ================= CREATE =================

    @Override
    public ScreenResponseDTO createScreen(
            ScreenRequestDTO request
    ) {

        Screen screen = Screen.builder()
                .theaterId(request.theaterId())
                .name(request.name())
                .screenType(request.screenType())
                .capacity(request.capacity())
                .createdAt(LocalDateTime.now())
                .build();

        Screen savedScreen =
                screenRepository.save(screen);

        return mapToResponse(savedScreen);
    }


    // ================= GET ALL =================

    @Override
    public List<ScreenResponseDTO> getAllScreens() {

        return screenRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // ================= GET BY ID =================

    @Override
    public ScreenResponseDTO getScreenById(
            Integer id
    ) {

        Screen screen = screenRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Screen", id)
                );

        return mapToResponse(screen);
    }


    // ================= UPDATE =================

    @Override
    public ScreenResponseDTO updateScreen(
            Integer id,
            ScreenRequestDTO request
    ) {

        Screen screen = screenRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Screen", id)
                );

        screen.setTheaterId(request.theaterId());
        screen.setName(request.name());
        screen.setScreenType(request.screenType());
        screen.setCapacity(request.capacity());

        Screen updatedScreen =
                screenRepository.save(screen);

        return mapToResponse(updatedScreen);
    }


    // ================= DELETE =================

    @Override
    public void deleteScreen(Integer id) {

        Screen screen = screenRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Screen", id)
                );

        screenRepository.delete(screen);
    }


    // ================= MAP TO RESPONSE =================

    private ScreenResponseDTO mapToResponse(
            Screen screen
    ) {

        return new ScreenResponseDTO(
                screen.getScreenId(),
                screen.getTheaterId(),
                screen.getName(),
                screen.getScreenType(),
                screen.getCapacity(),
                screen.getCreatedAt()
        );
    }
}