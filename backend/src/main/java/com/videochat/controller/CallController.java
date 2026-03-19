package com.videochat.controller;

import com.videochat.model.CallRecord;
import com.videochat.service.CallService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/call")
@RequiredArgsConstructor
public class CallController {
    
    private final CallService callService;
    
    @GetMapping("/history")
    public ResponseEntity<List<CallRecord>> getCallHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Long userId = (Long) authentication.getPrincipal();
        List<CallRecord> history = callService.getCallHistory(userId, page, size);
        return ResponseEntity.ok(history);
    }
}
