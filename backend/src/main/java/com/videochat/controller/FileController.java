package com.videochat.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/file")
public class FileController {
    
    @Value("${file.upload.path:./uploads}")
    private String uploadPath;
    
@PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            System.out.println("Upload request received, file: " + file.getOriginalFilename());
            
            // Create upload directory if not exists
            File dir = new File(uploadPath);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                System.out.println("Created uploads directory: " + created);
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString() + extension;

            // Save file
            Path filePath = Paths.get(uploadPath, filename);
            Files.write(filePath, file.getBytes());
            System.out.println("File saved to: " + filePath);

            // Return file URL
            String fileUrl = "/uploads/" + filename;
            Map<String, String> response = new HashMap<>();
            response.put("url", fileUrl);
            response.put("filename", filename);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
