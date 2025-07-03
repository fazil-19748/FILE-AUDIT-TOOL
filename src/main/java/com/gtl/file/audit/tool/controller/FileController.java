package com.gtl.file.audit.tool.controller;
import org.apache.tika.Tika;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final Tika tika = new Tika();

    @PostMapping("/decode")
    public ResponseEntity<byte[]> decodeBase64File(@RequestBody String base64Input) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Input);

            String mimeType = tika.detect(decodedBytes);

            String fileExtension = mimeType.substring(mimeType.lastIndexOf("/") + 1);
            String fileName = "decoded_file" + System.currentTimeMillis() + "." + fileExtension;

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType(mimeType))
                    .body(decodedBytes);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(("Invalid Base64 input").getBytes());
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(("Error processing file").getBytes());
        }
    }

    @PostMapping("/to-base64")
    public ResponseEntity<String> convertToBase64(@RequestParam("file") MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            String base64Encoded = Base64.getEncoder().encodeToString(fileBytes);
            return ResponseEntity.ok(base64Encoded);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error reading file: " + e.getMessage());
        }
    }
}

