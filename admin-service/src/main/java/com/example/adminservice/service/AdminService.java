package com.example.adminservice.service;

import com.example.adminservice.dto.UserDTO;

import java.util.List;

public interface AdminService {

    List<UserDTO> getAllUsers();

}