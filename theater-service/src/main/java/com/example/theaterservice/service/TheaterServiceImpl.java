package com.example.theaterservice.service;

import com.example.theaterservice.exception.ResourceNotFoundException;
import com.example.theaterservice.dto.TheaterRequestDTO;
import com.example.theaterservice.dto.TheaterResponseDTO;
import com.example.theaterservice.entity.Theater;
import com.example.theaterservice.repository.TheaterRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TheaterServiceImpl implements TheaterService {

    private final TheaterRepository theaterRepository;

    public TheaterServiceImpl(
            TheaterRepository theaterRepository
    ) {
        this.theaterRepository = theaterRepository;
    }

    // ================= CREATE =================

    @Override
    public TheaterResponseDTO createTheater(
            TheaterRequestDTO request
    ) {

        Theater theater = Theater.builder()
                .name(request.name())
                .location(request.location())
                .address(request.address())
                .createdAt(LocalDateTime.now())
                .build();

        Theater savedTheater =
                theaterRepository.save(theater);

        return mapToResponse(savedTheater);
    }


    // ================= GET ALL =================

    @Override
    public List<TheaterResponseDTO> getAllTheaters() {

        return theaterRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // ================= GET BY ID =================

    @Override
    public TheaterResponseDTO getTheaterById(
            Integer id
    ) {

        Theater theater = theaterRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Theater", id)
                );

        return mapToResponse(theater);
    }


    // ================= UPDATE =================

    @Override
    public TheaterResponseDTO updateTheater(
            Integer id,
            TheaterRequestDTO request
    ) {

        Theater theater = theaterRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Theater", id)
                );

        theater.setName(request.name());
        theater.setLocation(request.location());
        theater.setAddress(request.address());

        Theater updatedTheater =
                theaterRepository.save(theater);

        return mapToResponse(updatedTheater);
    }


    // ================= DELETE =================

    @Override
    public void deleteTheater(Integer id) {

        Theater theater = theaterRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Theater", id)
                );

        theaterRepository.delete(theater);
    }


    // ================= MAP TO RESPONSE =================

    private TheaterResponseDTO mapToResponse(
            Theater theater
    ) {

        return new TheaterResponseDTO(
                theater.getTheaterId(),
                theater.getName(),
                theater.getLocation(),
                theater.getAddress(),
                theater.getCreatedAt()
        );
    }
}