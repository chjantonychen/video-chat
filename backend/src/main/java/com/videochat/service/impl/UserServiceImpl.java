package com.videochat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.videochat.dto.LoginRequest;
import com.videochat.dto.RegisterRequest;
import com.videochat.dto.UserDTO;
import com.videochat.mapper.UserMapper;
import com.videochat.model.User;
import com.videochat.service.JwtService;
import com.videochat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    
    @Override
    public String register(RegisterRequest request) {
        // Check if username exists
        User existing = getUserByUsernameEntity(request.getUsername());
        if (existing != null) {
            throw new RuntimeException("Username already exists");
        }
        
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setAvatar("/default-avatar.png");
        user.setSignature("");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        
        userMapper.insertWithId(user);
        return jwtService.generateToken(user.getId());
    }
    
    @Override
    public String login(LoginRequest request) {
        User user = getUserByUsernameEntity(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }
        return jwtService.generateToken(user.getId());
    }
    
    @Override
    public UserDTO getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) return null;
        return toDTO(user);
    }
    
    @Override
    public UserDTO updateUser(Long userId, UserDTO userDTO) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new RuntimeException("User not found");
        
        if (userDTO.getNickname() != null) user.setNickname(userDTO.getNickname());
        if (userDTO.getAvatar() != null) user.setAvatar(userDTO.getAvatar());
        if (userDTO.getSignature() != null) user.setSignature(userDTO.getSignature());
        user.setUpdatedAt(LocalDateTime.now());
        
        userMapper.updateById(user);
        return toDTO(user);
    }
    
    @Override
    public UserDTO getUserByUsername(String username) {
        User user = getUserByUsernameEntity(username);
        return user != null ? toDTO(user) : null;
    }
    
    private User getUserByUsernameEntity(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return userMapper.selectOne(wrapper);
    }
    
    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setAvatar(user.getAvatar());
        dto.setSignature(user.getSignature());
        return dto;
    }
}
