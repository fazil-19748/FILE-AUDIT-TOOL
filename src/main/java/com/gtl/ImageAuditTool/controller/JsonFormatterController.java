package com.gtl.FileAuditTool.controller;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/json")
public class JsonFormatterController {

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @PostMapping("/api/format")
    public ResponseEntity<String> formatJson(@RequestBody String jsonInput) {
        try {
            // Prevent partial parsing — ensures all-or-nothing
            JsonNode jsonNode = objectMapper.readTree(jsonInput);
            String prettyJson = objectMapper.writeValueAsString(jsonNode);
            return ResponseEntity.ok(prettyJson);

        } catch (JsonParseException e) {
            int line = e.getLocation().getLineNr();
            int column = e.getLocation().getColumnNr();

            String[] lines = jsonInput.split("\n");
            String fullLine = (line <= lines.length) ? lines[line - 1] : "";

            // Only show up to ~80 characters for preview
            String preview = fullLine;
            if (fullLine.length() > 80) {
                int start = Math.max(0, column - 40);
                int end = Math.min(fullLine.length(), start + 80);
                preview = (start > 0 ? "..." : "") + fullLine.substring(start, end) + (end < fullLine.length() ? "..." : "");
                column = column - start + (start > 0 ? 3 : 0);  // adjust pointer position
            }

            // Clean pointer
            String pointer = " ".repeat(Math.max(0, column - 1)) + "^";

            // Final clean message
            String message = String.format(
                    "Parse error on line %d:\n%s\n%s\nExpecting 'EOF', '}', ':', ',', ']', got '%s'",
                    line,
                    preview,
                    pointer,
                    getUnexpectedToken(e.getOriginalMessage())
            );

            return ResponseEntity.badRequest().body(message);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Unexpected error: " + e.getMessage());
        }
    }

    private String getUnexpectedToken(String originalMessage) {
        // Try to extract something like `'STRING'` or `'['` etc
        if (originalMessage.contains("Unexpected character")) {
            int idx = originalMessage.indexOf('(');
            if (idx != -1) {
                String token = originalMessage.substring(idx + 1).split("[)]")[0];
                if (token.contains("'")) {
                    return token.substring(token.indexOf('\'') + 1, token.lastIndexOf('\''));
                } else if (token.contains("code")) {
                    return token.split(" ")[0];
                }
                return token;
            }
        } else if (originalMessage.toLowerCase().contains("colon")) {
            return ":";
        }
        return "?";
    }
}


