package com.gtl.FileAuditTool.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/home")
    public String index() {
        return "index";
    }

    @GetMapping("/image")
    public String imageUploadForm() {
        return "image";
    }

    @GetMapping("/base64decode")
    public String base64decode() {
        return "base64decode";
    }

    @GetMapping("/base64upload")
    public String base64upload() {
        return "base64upload";
    }

    @GetMapping("/json-formatter")
    public String jsonFormatter() {
        return "json-formatter";
    }

}

