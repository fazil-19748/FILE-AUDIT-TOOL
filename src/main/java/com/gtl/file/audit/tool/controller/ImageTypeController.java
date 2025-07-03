package com.gtl.file.audit.tool.controller;

import org.apache.tika.Tika;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
public class ImageTypeController {

    private final Tika tika = new Tika();

    @PostMapping("/detect-type")
    public ResponseEntity<String> detectImageType(@RequestParam("file") MultipartFile file) {
        try {
            // Detect MIME type from file content
            String mimeType = tika.detect(file.getBytes());

            return ResponseEntity.ok("Original File Type:  " + mimeType.split("/")[1]);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to detect file type: " + e.getMessage());
        }
    }
}
