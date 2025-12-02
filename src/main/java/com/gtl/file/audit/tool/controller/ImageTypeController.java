package com.gtl.file.audit.tool.controller;

import org.apache.tika.Tika;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/images")
public class ImageTypeController {

    private final Tika tika = new Tika();

    @PostMapping("/detect-type")
    public ResponseEntity<String> detectImageType(@RequestParam("file") MultipartFile file) {
        log.info("Received detectImageType request: fileName={}, size={} bytes", file.getOriginalFilename(), file.getSize());
        try {
            // Detect MIME type from file content
            String mimeType = tika.detect(file.getBytes());
            log.info("Detected MIME type: {} for file: {}", mimeType, file.getOriginalFilename());

            return ResponseEntity.ok("Original File Type:  " + mimeType.split("/")[1]);
        } catch (Exception e) {
            log.error("Failed to detect file type for file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.badRequest().body("Failed to detect file type: " + e.getMessage());
        }
    }
}
