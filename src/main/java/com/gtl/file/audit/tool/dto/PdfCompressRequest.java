package com.gtl.file.audit.tool.dto;

public class PdfCompressRequest {
    private String base64Pdf;
    private Integer targetSizeKB; // Optional

    // Getters and setters
    public String getBase64Pdf() {
        return base64Pdf;
    }

    public void setBase64Pdf(String base64Pdf) {
        this.base64Pdf = base64Pdf;
    }

    public Integer getTargetSizeKB() {
        return targetSizeKB;
    }

    public void setTargetSizeKB(Integer targetSizeKB) {
        this.targetSizeKB = targetSizeKB;
    }
}


