package com.gtl.file.audit.tool.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/home")
    public String index() {
        return "pages/index";
    }

    @GetMapping("/image")
    public String imageUploadForm() {
        return "pages/image";
    }

    @GetMapping("/base64decode")
    public String base64decode() {
        return "pages/base64decode";
    }

    @GetMapping("/base64upload")
    public String base64upload() {
        return "pages/base64upload";
    }

    @GetMapping("/json-formatter")
    public String jsonFormatter() {
        return "pages/json-formatter";
    }

    @GetMapping("/compress-pdf")
    public String pdfCompressor() {
        return "pages/pdf-compressor";
    }

}

