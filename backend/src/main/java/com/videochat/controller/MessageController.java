package com.videochat.controller;

import com.videochat.model.Message;
import com.videochat.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {
    
    private final MessageService messageService;
    
    @PostMapping("/send")
    public ResponseEntity<Message> sendMessage(
            Authentication authentication,
            @RequestBody Map<String, Object> body) {
        Long fromUserId = (Long) authentication.getPrincipal();
        Long toUserId = Long.parseLong(body.get("toUserId").toString());
        Integer type = Integer.parseInt(body.get("type").toString());
        String content = body.get("content").toString();
        
        Message message = messageService.sendMessage(fromUserId, toUserId, type, content);
        return ResponseEntity.ok(message);
    }
    
    @GetMapping("/list/{friendId}")
    public ResponseEntity<List<Message>> getMessageList(
            Authentication authentication,
            @PathVariable Long friendId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size) {
        Long userId = (Long) authentication.getPrincipal();
        List<Message> messages = messageService.getMessageList(userId, friendId, page, size);
        return ResponseEntity.ok(messages);
    }
    
    @PutMapping("/read/{friendId}")
    public ResponseEntity<Void> markAsRead(
            Authentication authentication,
            @PathVariable Long friendId) {
        Long userId = (Long) authentication.getPrincipal();
        messageService.markAsRead(userId, friendId);
        return ResponseEntity.ok().build();
    }
}
