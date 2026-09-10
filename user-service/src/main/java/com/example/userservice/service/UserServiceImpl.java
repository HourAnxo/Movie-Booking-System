package com.example.userservice.service;

import com.example.userservice.dto.CreateUserDTO;
import com.example.userservice.exception.DuplicateResourceException;
import com.example.userservice.exception.ResourceNotFoundException;
import com.example.userservice.dto.UpdateUserDTO;
import com.example.userservice.dto.UserResponseDTO;
import com.example.userservice.entity.User;
import com.example.userservice.repository.UserRepository;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    // ============================
    // CREATE USER
    // ============================

    @Override
    public UserResponseDTO createUser(CreateUserDTO request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already exists: " + request.getEmail());
        }

        User user = new User();

        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());

        User savedUser = userRepository.save(user);

        return mapToResponse(savedUser);
    }


    // ============================
    // GET ALL USERS
    // ============================

    @Override
    public List<UserResponseDTO> getAllUsers() {

        return userRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // ============================
    // GET USER BY ID
    // ============================

    @Override
    public UserResponseDTO getUserById(Integer userId) {

        User user = userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User", userId)
                );

        return mapToResponse(user);
    }


    // ============================
    // GET USER BY EMAIL
    // ============================

    @Override
    public UserResponseDTO getUserByEmail(String email) {

        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found with email: " + email)
                );

        return mapToResponse(user);
    }


    // ============================
    // UPDATE USER
    // ============================

    @Override
    public UserResponseDTO updateUser(
            Integer userId,
            UpdateUserDTO request
    ) {

        User user = userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User", userId)
                );

        user.setName(request.getName());
        user.setPhone(request.getPhone());

        User updatedUser = userRepository.save(user);

        return mapToResponse(updatedUser);
    }


    // ============================
    // DELETE USER
    // ============================

    @Override
    public void deleteUser(Integer userId) {

        User user = userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User", userId)
                );

        userRepository.delete(user);
    }


    // ============================
    // ENTITY → DTO
    // ============================

    private UserResponseDTO mapToResponse(User user) {

        UserResponseDTO response = new UserResponseDTO();

        response.setUserId(user.getUserId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());

        return response;
    }
}