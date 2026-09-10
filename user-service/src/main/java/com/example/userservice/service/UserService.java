package com.example.userservice.service;

import com.example.userservice.dto.CreateUserDTO;
import com.example.userservice.dto.UpdateUserDTO;
import com.example.userservice.dto.UserResponseDTO;

import java.util.List;

public interface UserService {

    // Create User
    UserResponseDTO createUser(CreateUserDTO request);

    // Get All Users
    List<UserResponseDTO> getAllUsers();

    // Get User By ID
    UserResponseDTO getUserById(Integer userId);

    // Get User By Email
    UserResponseDTO getUserByEmail(String email);

    // Update User
    UserResponseDTO updateUser(
            Integer userId,
            UpdateUserDTO request
    );

    // Delete User
    void deleteUser(Integer userId);
}