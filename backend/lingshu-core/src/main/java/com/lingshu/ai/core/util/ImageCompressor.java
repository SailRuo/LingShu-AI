package com.lingshu.ai.core.util;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public final class ImageCompressor {

    private static final Logger log = LoggerFactory.getLogger(ImageCompressor.class);

    private static final int MAX_WIDTH = 800;
    private static final int MAX_HEIGHT = 800;
    private static final float QUALITY = 0.7f;
    private static final int MAX_BASE64_LENGTH = 100 * 1024;
    private static final String OUTPUT_FORMAT = "jpg";

    private ImageCompressor() {
    }

    public static CompressionResult compress(String base64Image) {
        if (base64Image == null || base64Image.isBlank()) {
            return new CompressionResult(null, null, false, "Empty image data");
        }

        try {
            String base64Data = base64Image;
            String mimeType = "image/jpeg";

            if (base64Image.contains(",")) {
                String[] parts = base64Image.split(",", 2);
                base64Data = parts[1];
                String prefix = parts[0];
                if (prefix.contains(":") && prefix.contains(";")) {
                    mimeType = prefix.substring(prefix.indexOf(":") + 1, prefix.indexOf(";"));
                }
            }

            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            int originalSize = imageBytes.length;
            log.debug("Original image size: {} bytes", originalSize);

            if (base64Data.length() <= MAX_BASE64_LENGTH) {
                log.debug("Image already within size limit, no compression needed");
                return new CompressionResult(base64Image, mimeType, false, null);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(imageBytes))
                    .size(MAX_WIDTH, MAX_HEIGHT)
                    .outputQuality(QUALITY)
                    .outputFormat(OUTPUT_FORMAT)
                    .toOutputStream(outputStream);

            byte[] compressedBytes = outputStream.toByteArray();
            int compressedSize = compressedBytes.length;
            String compressedBase64 = Base64.getEncoder().encodeToString(compressedBytes);
            String compressedMimeType = "image/jpeg";

            double compressionRatio = (1 - (double) compressedSize / originalSize) * 100;
            log.info("Image compressed: {} bytes -> {} bytes ({}% reduction)",
                    originalSize, compressedSize, String.format("%.1f", compressionRatio));

            return new CompressionResult(
                    "data:" + compressedMimeType + ";base64," + compressedBase64,
                    compressedMimeType,
                    true,
                    null
            );

        } catch (Exception e) {
            log.error("Failed to compress image: {}", e.getMessage(), e);
            return new CompressionResult(null, null, false, "Image compression failed: " + e.getMessage());
        }
    }

    public static String compressToBase64(String base64Image) {
        CompressionResult result = compress(base64Image);
        if (result.success() || result.data() != null) {
            return result.data();
        }
        return base64Image;
    }

    public static CompressionResult checkAndCompress(String base64Image) {
        if (base64Image == null || base64Image.isBlank()) {
            return new CompressionResult(null, null, false, "Empty image data");
        }

        String base64Data = base64Image;
        if (base64Image.contains(",")) {
            base64Data = base64Image.split(",", 2)[1];
        }

        if (base64Data.length() > MAX_BASE64_LENGTH) {
            log.info("Image exceeds size limit ({} > {}), will compress",
                    base64Data.length(), MAX_BASE64_LENGTH);
            return compress(base64Image);
        }

        return new CompressionResult(base64Image, null, false, null);
    }

    public static record CompressionResult(
            String data,
            String mimeType,
            boolean success,
            String error
    ) {
        public boolean hasError() {
            return error != null;
        }
    }
}
