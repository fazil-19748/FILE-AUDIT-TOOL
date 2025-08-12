package com.gtl.file.audit.tool.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GhostscriptPdfCompressor {
    private static final Logger logger = LoggerFactory.getLogger(GhostscriptPdfCompressor.class);

    private static final String DEFAULT_GS_SETTINGS =
            "-sDEVICE=pdfwrite " +
                    "-dCompatibilityLevel=1.4 " +
                    "-dPDFSETTINGS=/screen " +
                    "-dNOPAUSE -dQUIET -dBATCH " +
                    "-dDetectDuplicateImages=true " +
                    "-dCompressFonts=true " +
                    "-dEmbedAllFonts=false " +
                    "-dSubsetFonts=true " +
                    "-dAutoRotatePages=/None " +
                    "-dColorImageDownsampleType=/Bicubic " +
                    "-dColorImageResolution=150 " +
                    "-dGrayImageDownsampleType=/Bicubic " +
                    "-dGrayImageResolution=150 " +
                    "-dMonoImageDownsampleType=/Subsample " +
                    "-dMonoImageResolution=150 " +
                    "-sOutputFile=%s %s";

    private static final String AGGRESSIVE_GS_SETTINGS =
            "-sDEVICE=pdfwrite " +
                    "-dCompatibilityLevel=1.4 " +
                    "-dPDFSETTINGS=/ebook " +
                    "-dNOPAUSE -dQUIET -dBATCH " +
                    "-dDetectDuplicateImages=true " +
                    "-dCompressFonts=true " +
                    "-dEmbedAllFonts=false " +
                    "-dSubsetFonts=true " +
                    "-dAutoRotatePages=/None " +
                    "-dColorImageDownsampleType=/Bicubic " +
                    "-dColorImageResolution=96 " +
                    "-dGrayImageDownsampleType=/Bicubic " +
                    "-dGrayImageResolution=96 " +
                    "-dMonoImageDownsampleType=/Subsample " +
                    "-dMonoImageResolution=96 " +
                    "-dEncodeColorImages=true " +
                    "-dEncodeGrayImages=true " +
                    "-dEncodeMonoImages=true " +
                    "-sOutputFile=%s %s";

    public static byte[] compressPdf(byte[] inputPdf, int targetSizeKB) throws IOException, InterruptedException {
        Path inputFile = Files.createTempFile("input_", ".pdf");
        Path outputFile = Files.createTempFile("output_", ".pdf");

        try {
            Files.write(inputFile, inputPdf);

            byte[] result = compressWithSettings(inputFile, outputFile, DEFAULT_GS_SETTINGS);
            int sizeKB = result.length / 1024;

            if (sizeKB <= targetSizeKB) {
                return result;
            }

            logger.info("Default compression not sufficient ({} KB), trying aggressive settings", sizeKB);
            result = compressWithSettings(inputFile, outputFile, AGGRESSIVE_GS_SETTINGS);
            sizeKB = result.length / 1024;

            if (sizeKB <= targetSizeKB) {
                return result;
            }

            logger.info("Aggressive compression not sufficient ({} KB), trying iterative downsampling", sizeKB);
            return compressIteratively(inputFile, outputFile, targetSizeKB);

        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }

    private static byte[] compressWithSettings(Path inputFile, Path outputFile, String settings)
            throws IOException, InterruptedException {
        String command = String.format("gs " + settings,
                outputFile.toString(), inputFile.toString());

        executeCommand(command);

        if (!Files.exists(outputFile) || Files.size(outputFile) == 0) {
            throw new IOException("Ghostscript failed to produce output file");
        }

        return Files.readAllBytes(outputFile);
    }

    private static byte[] compressIteratively(Path inputFile, Path outputFile, int targetSizeKB)
            throws IOException, InterruptedException {
        List<Integer> resolutionsToTry = List.of(150, 120, 96, 72, 64);
        byte[] bestResult = null;
        int bestSize = Integer.MAX_VALUE;

        for (int resolution : resolutionsToTry) {
            String settings = String.format(
                    "-sDEVICE=pdfwrite " +
                            "-dCompatibilityLevel=1.4 " +
                            "-dPDFSETTINGS=/ebook " +
                            "-dNOPAUSE -dQUIET -dBATCH " +
                            "-dDetectDuplicateImages=true " +
                            "-dCompressFonts=true " +
                            "-dEmbedAllFonts=false " +
                            "-dSubsetFonts=true " +
                            "-dAutoRotatePages=/None " +
                            "-dColorImageDownsampleType=/Bicubic " +
                            "-dColorImageResolution=%d " +
                            "-dGrayImageDownsampleType=/Bicubic " +
                            "-dGrayImageResolution=%d " +
                            "-dMonoImageDownsampleType=/Subsample " +
                            "-dMonoImageResolution=%d " +
                            "-dEncodeColorImages=true " +
                            "-dEncodeGrayImages=true " +
                            "-dEncodeMonoImages=true " +
                            "-sOutputFile=%s %s",
                    resolution, resolution, resolution, "%s", "%s");

            try {
                byte[] result = compressWithSettings(inputFile, outputFile, settings);
                int sizeKB = result.length / 1024;

                if (sizeKB <= targetSizeKB) {
                    return result;
                }

                if (sizeKB < bestSize) {
                    bestSize = sizeKB;
                    bestResult = result;
                }

                logger.info("Tried resolution {}dpi, size: {} KB", resolution, sizeKB);

            } catch (Exception e) {
                logger.warn("Compression with resolution {}dpi failed: {}", resolution, e.getMessage());
            }
        }

        if (bestResult != null) {
            logger.warn("Could not reach target size of {} KB. Best achieved: {} KB",
                    targetSizeKB, bestSize);
            return bestResult;
        }

        throw new IOException("All compression attempts failed");
    }

    private static void executeCommand(String command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("Ghostscript: {}", line);
            }
        }

        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Ghostscript timed out");
        }

        if (process.exitValue() != 0) {
            throw new IOException("Ghostscript failed with exit code " + process.exitValue());
        }
    }
}
