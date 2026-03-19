package com.videochat.service;

import com.videochat.dto.LoginRequest;
import com.videochat.dto.RegisterRequest;
import com.videochat.dto.UserDTO;

public interface UserService {
    String register(RegisterRequest request);
    String login(LoginRequest request);
    UserDTO getUserById(Long id);
    UserDTO updateUser(Long userId, UserDTO userDTO);
    UserDTO getUserByUsername(String username);
}
