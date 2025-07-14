package com.gtl.file.audit.tool.dto;

public class PdfCompressResponse {
    private String compressedImage;
    private String ErrorMsg;
    private String format;
    private String errorCode;

    public PdfCompressResponse(String base64Compressed) {
        this.compressedImage = base64Compressed;
        this.ErrorMsg = "Success";
        this.format = "PDF";
        this.errorCode = "0";
    }

    public String getCompressedImage() {
        return compressedImage;
    }

    public void setCompressedImage(String compressedImage) {
        this.compressedImage = compressedImage;
    }

    public String getErrorMsg() {
        return ErrorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        ErrorMsg = errorMsg;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}
