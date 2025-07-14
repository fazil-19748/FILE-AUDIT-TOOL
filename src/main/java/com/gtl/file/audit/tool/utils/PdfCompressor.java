package com.gtl.file.audit.tool.utils;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.cos.COSName;
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

    public static byte[] compressPdfToTargetSize(byte[] originalPdfBytes, int targetSizeKB) throws IOException {
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

            // Reduce image scale and quality for next attempt
            scale *= 0.9f;
            quality -= 0.1f;
            if (quality < MIN_QUALITY) {
                quality = MIN_QUALITY;
            }
        }

        logger.warn("Compression could not meet target size after {} attempts. Returning best effort ({} KB)",
                MAX_RETRIES, (result != null ? result.length / 1024 : -1));
        return result;
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
                        // Submit image compression task
                        tasks.add(executor.submit(() -> {
                            try {
                                BufferedImage image = imageXObject.getImage();
                                BufferedImage resized = resizeImage(image, scale);

                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                writeJpegWithQuality(resized, baos, quality);

                                PDImageXObject compressedImage = JPEGFactory.createFromStream(document,
                                        new ByteArrayInputStream(baos.toByteArray()));

                                synchronized (resources) {
                                    resources.put(xObjectName, compressedImage);
                                }

                            } catch (Exception e) {
                                logger.error("Image compression failed: {}", e.getMessage(), e);
                            }
                        }));
                    }
                }
            }

            // Wait for all tasks to complete
            for (Future<?> task : tasks) {
                try {
                    task.get(); // waits for task to complete
                } catch (Exception e) {
                    logger.error("Compression task failed: {}", e.getMessage(), e);
                }
            }

            executor.shutdown();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static BufferedImage resizeImage(BufferedImage original, double scale) {
        int width = (int) (original.getWidth() * scale);
        int height = (int) (original.getHeight() * scale);

        if (width < 1) {
            width = 1;
        }
        if (height < 1) {
            height = 1;
        }

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
}



