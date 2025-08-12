package com.gtl.file.audit.tool.utils;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PdfCompressor {

    private static final Logger logger = LoggerFactory.getLogger(PdfCompressor.class);

    private static final int MAX_RETRIES = 15;
    private static final float MIN_QUALITY = 0.1f;
    private static final float MIN_SCALE = 0.2f;
    private static final int MIN_IMAGE_DIMENSION = 400;

    public static byte[] compressPdfToTargetSize(byte[] originalPdfBytes, int targetSizeKB) throws IOException {
        try {
            float scale = 1.0f;
            float quality = 0.8f;
            byte[] result = null;

            logger.info("Starting PDF compression. Target size: {} KB", targetSizeKB);

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                logger.info("Attempt {}: scale={}, quality={}", attempt, scale, quality);

                result = compressPdfWithSettings(originalPdfBytes, scale, quality);
                int sizeKB = result.length / 1024;

                logger.info("Compressed size: {} KB", sizeKB);

                if (sizeKB <= targetSizeKB) {
                    logger.info("Compression successful on attempt {}. Final size: {} KB", attempt, sizeKB);
                    return result;
                }

                if (attempt < 5) {
                    quality -= 0.15f;
                    scale *= 0.85f;
                } else if (attempt < 10) {
                    quality -= 0.1f;
                    scale *= 0.9f;
                } else {
                    quality -= 0.05f;
                    scale *= 0.95f;
                }

                quality = Math.max(quality, MIN_QUALITY);
                scale = Math.max(scale, MIN_SCALE);
            }

            // If PDFBox can't compress enough, try Ghostscript
            logger.warn("PDFBox compression could not meet target size. Trying Ghostscript...");
            try {
                byte[] gsResult = GhostscriptPdfCompressor.compressPdf(originalPdfBytes, targetSizeKB);
                int gsSizeKB = gsResult.length / 1024;

                if (gsSizeKB <= targetSizeKB) {
                    logger.info("Ghostscript compression successful. Final size: {} KB", gsSizeKB);
                    return gsResult;
                }

                logger.warn("Ghostscript compression also exceeded target ({} KB). Returning best result.", gsSizeKB);
                return gsResult;
            } catch (Exception e) {
                logger.error("Ghostscript compression failed: {}", e.getMessage());
            }

            logger.warn("All compression methods failed. Falling back to rasterization.");
            byte[] rasterized = rasterizePdfToImages(originalPdfBytes, 70f, 0.45f);
            int finalSize = rasterized.length / 1024;
            logger.warn("Rasterization result: {} KB", finalSize);
            return rasterized;

        } catch (Exception e) {
            logger.error("PDF compression failed completely: {}", e.getMessage(), e);
            throw new IOException("Failed to compress PDF", e);
        }
    }

    private static byte[] compressPdfWithSettings(byte[] originalPdfBytes, float scale, float quality) throws IOException {
        try (PDDocument document = PDDocument.load(originalPdfBytes)) {

            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            List<Future<?>> tasks = new ArrayList<>();

            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();

                for (COSName xObjectName : resources.getXObjectNames()) {
                    PDXObject xObject = resources.getXObject(xObjectName);

                    if (xObject instanceof PDImageXObject imageXObject) {
                        tasks.add(executor.submit(() -> {
                            try {
                                BufferedImage image = null;

                                try {
                                    image = imageXObject.getImage();
                                } catch (IOException e) {
                                    logger.warn("Standard decoding failed for image: {}. Attempting fallback. Error: {}", xObjectName.getName(), e.getMessage());

                                    try (InputStream fallbackIn = imageXObject.getStream().createInputStream()) {
                                        image = ImageIO.read(fallbackIn);
                                    } catch (IOException fallbackEx) {
                                        logger.error("Fallback decoding failed for image: {}. Skipping. Error: {}", xObjectName.getName(), fallbackEx.getMessage());
                                        return;
                                    }
                                }

                                if (image == null) {
                                    logger.error("Image decoding failed completely for: {}", xObjectName.getName());
                                    return;
                                }

                                BufferedImage resized = resizeImage(image, scale);

                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                writeJpegWithQuality(resized, baos, quality);

                                PDImageXObject compressedImage = JPEGFactory.createFromStream(document,
                                        new ByteArrayInputStream(baos.toByteArray()));

                                synchronized (resources) {
                                    resources.put(xObjectName, compressedImage);
                                }

                            } catch (Exception e) {
                                logger.error("Image compression failed for {}: {}", xObjectName.getName(), e.getMessage(), e);
                            }
                        }));
                    }
                }
            }

            for (Future<?> task : tasks) {
                try {
                    task.get();
                } catch (Exception e) {
                    logger.error("Compression task execution failed: {}", e.getMessage(), e);
                }
            }

            executor.shutdown();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static BufferedImage resizeImage(BufferedImage original, double scale) {
        int width = Math.max((int) (original.getWidth() * scale), MIN_IMAGE_DIMENSION);
        int height = Math.max((int) (original.getHeight() * scale), MIN_IMAGE_DIMENSION);

        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(original, 0, 0, width, height, null);
        g2d.dispose();

        return resized;
    }

    private static void writeJpegWithQuality(BufferedImage image, OutputStream os, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG writers found");
        }

        ImageWriter jpgWriter = writers.next();
        ImageWriteParam jpgWriteParam = jpgWriter.getDefaultWriteParam();
        jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        jpgWriteParam.setCompressionQuality(quality);

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(os)) {
            jpgWriter.setOutput(ios);
            IIOImage ioImage = new IIOImage(image, null, null);
            jpgWriter.write(null, ioImage, jpgWriteParam);
        } finally {
            jpgWriter.dispose();
        }
    }

    private static byte[] rasterizePdfToImages(byte[] originalPdfBytes, float dpi, float jpegQuality) throws IOException {
        try (PDDocument originalDoc = PDDocument.load(originalPdfBytes);
             PDDocument outputDoc = new PDDocument()) {

            PDFRenderer renderer = new PDFRenderer(originalDoc);

            for (int i = 0; i < originalDoc.getNumberOfPages(); i++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);

                ByteArrayOutputStream imageBytes = new ByteArrayOutputStream();
                writeJpegWithQuality(pageImage, imageBytes, jpegQuality);

                PDImageXObject pdImage = JPEGFactory.createFromStream(outputDoc,
                        new ByteArrayInputStream(imageBytes.toByteArray()));

                PDPage page = new PDPage();
                outputDoc.addPage(page);

                try (PDPageContentStream contentStream = new PDPageContentStream(outputDoc, page)) {
                    contentStream.drawImage(pdImage, 0, 0,
                            page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
                }
            }

            ByteArrayOutputStream finalOut = new ByteArrayOutputStream();
            outputDoc.save(finalOut);
            return finalOut.toByteArray();
        }
    }
}



