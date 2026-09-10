package com.example.userservice.controller;

import com.example.userservice.dto.CreateUserDTO;
import com.example.userservice.dto.UpdateUserDTO;
import com.example.userservice.dto.UserResponseDTO;
import com.example.userservice.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }


    // ============================
    // CREATE USER
    // POST /api/users
    // ============================

    @PostMapping
    public ResponseEntity<UserResponseDTO> createUser(
            @Valid @RequestBody CreateUserDTO request
    ) {

        UserResponseDTO response =
                userService.createUser(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // ============================
    // GET ALL USERS
    // GET /api/users
    // ============================

    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {

        return ResponseEntity.ok(
                userService.getAllUsers()
        );
    }


    // ============================
    // GET USER BY ID
    // GET /api/users/1
    // ============================

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponseDTO> getUserById(
            @PathVariable Integer userId
    ) {

        return ResponseEntity.ok(
                userService.getUserById(userId)
        );
    }


    // ============================
    // GET USER BY EMAIL
    // GET /api/users/email/test@gmail.com
    // ============================

    @GetMapping("/email/{email}")
    public ResponseEntity<UserResponseDTO> getUserByEmail(
            @PathVariable String email
    ) {

        return ResponseEntity.ok(
                userService.getUserByEmail(email)
        );
    }


    // ============================
    // UPDATE USER
    // PUT /api/users/1
    // ============================

    @PutMapping("/{userId}")
    public ResponseEntity<UserResponseDTO> updateUser(
            @PathVariable Integer userId,
            @Valid @RequestBody UpdateUserDTO request
    ) {

        return ResponseEntity.ok(
                userService.updateUser(userId, request)
        );
    }


    // ============================
    // DELETE USER
    // DELETE /api/users/1
    // ============================

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Integer userId
    ) {

        userService.deleteUser(userId);

        return ResponseEntity.noContent().build();
    }
}
