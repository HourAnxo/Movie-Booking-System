package com.example.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A plain POJO rather than a record, matching the rest of user-service.
 */
public class CreateUserDTO {

    @NotBlank(message = "is required")
    @Size(max = 100, message = "must be at most 100 characters")
    private String name;

    @NotBlank(message = "is required")
    @Email(message = "must be a valid email address")
    @Size(max = 150, message = "must be at most 150 characters")
    private String email;

    @Size(max = 20, message = "must be at most 20 characters")
    private String phone;

    public CreateUserDTO() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
