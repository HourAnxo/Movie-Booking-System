package com.example.authservice.dto;

public record CreateUserRequestDTO(

        String name,

        String email,

        String phone

) {
}