package com.videochat.controller;

import com.videochat.dto.LoginRequest;
import com.videochat.dto.RegisterRequest;
import com.videochat.dto.UserDTO;
import com.videochat.service.JwtService;
import com.videochat.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    private final JwtService jwtService;
    
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        String token = userService.register(request);
        return ResponseEntity.ok(Map.of("token", token));
    }
    
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        String token = userService.login(request);
        return ResponseEntity.ok(Map.of("token", token));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id) {
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }
    
    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        UserDTO user = userService.getUserById(userId);
        return ResponseEntity.ok(user);
    }
    
    @PutMapping
    public ResponseEntity<UserDTO> updateUser(
            Authentication authentication,
            @RequestBody UserDTO userDTO) {
        Long userId = (Long) authentication.getPrincipal();
        UserDTO updated = userService.updateUser(userId, userDTO);
        return ResponseEntity.ok(updated);
    }
}
