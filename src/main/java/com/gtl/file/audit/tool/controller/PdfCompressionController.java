package com.gtl.file.audit.tool.controller;

import com.gtl.file.audit.tool.dto.PdfCompressRequest;
import com.gtl.file.audit.tool.dto.PdfCompressResponse;
import com.gtl.file.audit.tool.utils.PdfCompressor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/pdf")
@Slf4j
public class PdfCompressionController {

    private static final Logger logger = LoggerFactory.getLogger(PdfCompressionController.class);

    @PostMapping("/compress")
    public ResponseEntity<?> compressPdf(@RequestBody PdfCompressRequest request) {
        try {
            byte[] decoded = Base64.getDecoder().decode(request.getBase64Pdf());
            byte[] compressed = PdfCompressor.compressPdfToTargetSize(decoded, request.getTargetSizeKB());

            String base64Compressed = Base64.getEncoder().encodeToString(compressed);
            return ResponseEntity.ok(new PdfCompressResponse(base64Compressed));

        } catch (Exception e) {
            logger.error("Compression failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "compressedImage", null,
                            "errorMsg", "Compression failed: " + e.getMessage(),
                            "format", "",
                            "errorCode", "1"
                    ));
        }
    }


}

