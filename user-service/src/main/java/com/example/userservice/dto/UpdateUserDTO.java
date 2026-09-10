package com.example.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * email is deliberately absent — it is the unique key other services
 * match on, so changing it is not a field edit.
 */
public class UpdateUserDTO {

    @NotBlank(message = "is required")
    @Size(max = 100, message = "must be at most 100 characters")
    private String name;

    @Size(max = 20, message = "must be at most 20 characters")
    private String phone;

    public UpdateUserDTO() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
