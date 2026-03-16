package com.videochat.controller;

import com.videochat.dto.FriendRequestDTO;
import com.videochat.dto.UserDTO;
import com.videochat.service.FriendService;
import com.videochat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/friend")
@RequiredArgsConstructor
public class FriendController {
    
    private final FriendService friendService;
    private final UserService userService;
    
    @GetMapping("/search")
    public ResponseEntity<UserDTO> searchUser(@RequestParam String username) {
        UserDTO user = userService.getUserByUsername(username);
        return ResponseEntity.ok(user);
    }
    
    @PostMapping("/request")
    public ResponseEntity<Void> sendFriendRequest(
            Authentication authentication,
            @RequestBody Map<String, Long> body) {
        Long fromUserId = (Long) authentication.getPrincipal();
        Long toUserId = body.get("toUserId");
        friendService.sendFriendRequest(fromUserId, toUserId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/request")
    public ResponseEntity<List<FriendRequestDTO>> getFriendRequests(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<FriendRequestDTO> requests = friendService.getFriendRequests(userId);
        return ResponseEntity.ok(requests);
    }
    
    @PutMapping("/request/{id}")
    public ResponseEntity<Void> handleFriendRequest(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        Long userId = (Long) authentication.getPrincipal();
        if (Boolean.TRUE.equals(body.get("accept"))) {
            friendService.acceptFriendRequest(id, userId);
        } else {
            friendService.rejectFriendRequest(id, userId);
        }
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/list")
    public ResponseEntity<List<UserDTO>> getFriendList(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        List<UserDTO> friends = friendService.getFriendList(userId);
        return ResponseEntity.ok(friends);
    }
    
    @DeleteMapping("/{friendId}")
    public ResponseEntity<Void> deleteFriend(
            Authentication authentication,
            @PathVariable Long friendId) {
        Long userId = (Long) authentication.getPrincipal();
        friendService.deleteFriend(userId, friendId);
        return ResponseEntity.ok().build();
    }
}
