package com.gtl.file.audit.tool.controller;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final Tika tika = new Tika();

    @PostMapping("/decode")
    public ResponseEntity<byte[]> decodeBase64File(@RequestBody Map<String, String> payload) {
        log.info("Received decodeBase64File request with payload");
        String base64Input = payload.get("base64Input");
        if (base64Input == null || base64Input.isEmpty()) {
            log.warn("decodeBase64File: Missing or empty base64Input");
            return ResponseEntity.badRequest().body(null);
        }
        try {
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Input);
            String mimeType = tika.detect(decodedBytes);
            String fileExtension = mimeType.substring(mimeType.lastIndexOf("/") + 1);
            String fileName = "decoded_file" + System.currentTimeMillis() + "." + fileExtension;
            log.info("Decoded file: mimeType={}, fileName={}", mimeType, fileName);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType(mimeType))
                    .body(decodedBytes);
        } catch (IllegalArgumentException e) {
            log.error("decodeBase64File: Invalid Base64 input", e);
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            log.error("decodeBase64File: Error processing file", e);
            return ResponseEntity.internalServerError()
                    .body(("Error processing file").getBytes());
        }
    }

    @PostMapping("/to-base64")
    public ResponseEntity<String> convertToBase64(@RequestParam("file") MultipartFile file) {
        log.info("Received convertToBase64 request: fileName={}, size={} bytes", file.getOriginalFilename(), file.getSize());
        try {
            byte[] fileBytes = file.getBytes();
            String base64Encoded = Base64.getEncoder().encodeToString(fileBytes);
            log.info("File converted to Base64: fileName={}, base64Length={}", file.getOriginalFilename(), base64Encoded.length());
            return ResponseEntity.ok(base64Encoded);
        } catch (IOException e) {
            log.error("convertToBase64: Error reading file {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(500).body("Error reading file: " + e.getMessage());
        }
    }
}
