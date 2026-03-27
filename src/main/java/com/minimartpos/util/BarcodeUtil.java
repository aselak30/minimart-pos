package com.minimartpos.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.EAN13Writer;
import com.google.zxing.oned.Code128Writer;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Generates barcodes using ZXing.
 *
 * Supports:
 *  - EAN-13  (standard retail product barcodes, 13 digits)
 *  - Code128 (alphanumeric, any length — good for internal barcodes)
 *
 * Usage:
 *   Image img = BarcodeUtil.toFxImage("4890008100309", BarcodeUtil.Format.EAN13, 300, 80);
 *   imageView.setImage(img);
 */
public final class BarcodeUtil {

    private static final Logger logger = LogManager.getLogger(BarcodeUtil.class);

    public enum Format { EAN13, CODE128 }

    private BarcodeUtil() {}

    // ── Generate JavaFX Image ─────────────────────────────────────────────────

    /**
     * Generates a barcode as a JavaFX Image.
     *
     * @param content  The barcode content (e.g. "4890008100309")
     * @param format   EAN13 or CODE128
     * @param width    Image width in pixels
     * @param height   Image height in pixels
     * @return         JavaFX Image, or null on error
     */
    public static Image toFxImage(String content, Format format, int width, int height) {
        try {
            BufferedImage bi = toBufferedImage(content, format, width, height);
            return bi != null ? SwingFXUtils.toFXImage(bi, null) : null;
        } catch (Exception e) {
            logger.error("toFxImage error for '{}': {}", content, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Generates a barcode as a BufferedImage (for printing / PDF embedding).
     */
    public static BufferedImage toBufferedImage(String content, Format format,
                                                 int width, int height) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 5);

            BitMatrix matrix;
            if (format == Format.EAN13) {
                EAN13Writer writer = new EAN13Writer();
                matrix = writer.encode(content, BarcodeFormat.EAN_13, width, height, hints);
            } else {
                Code128Writer writer = new Code128Writer();
                matrix = writer.encode(content, BarcodeFormat.CODE_128, width, height, hints);
            }

            return matrixToImage(matrix, width, height);

        } catch (Exception e) {
            logger.error("toBufferedImage error for '{}': {}", content, e.getMessage(), e);
            return null;
        }
    }

    // ── EAN-13 Check Digit ────────────────────────────────────────────────────

    /**
     * Computes the EAN-13 check digit for a 12-digit prefix.
     * Returns the full 13-digit barcode.
     */
    public static String computeEAN13(String twelveDigits) {
        if (twelveDigits == null || twelveDigits.length() != 12 ||
                !twelveDigits.matches("\\d{12}")) {
            throw new IllegalArgumentException("Input must be exactly 12 digits");
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = twelveDigits.charAt(i) - '0';
            sum += (i % 2 == 0) ? d : d * 3;
        }
        int check = (10 - (sum % 10)) % 10;
        return twelveDigits + check;
    }

    /**
     * Validates an EAN-13 barcode's check digit.
     */
    public static boolean validateEAN13(String barcode) {
        if (barcode == null || barcode.length() != 13 || !barcode.matches("\\d{13}")) {
            return false;
        }
        try {
            String prefix = barcode.substring(0, 12);
            return computeEAN13(prefix).equals(barcode);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generates a new EAN-13 barcode using the current timestamp as seed.
     * Prefix: 200 (internal use range per GS1 standard).
     */
    public static String generateEAN13() {
        long ts    = System.currentTimeMillis() % 100_000_000L;
        String base = String.format("200%09d", ts);  // 12 digits
        return computeEAN13(base);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static BufferedImage matrixToImage(BitMatrix matrix, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        for (int x = 0; x < matrix.getWidth(); x++) {
            for (int y = 0; y < matrix.getHeight(); y++) {
                if (matrix.get(x, y)) {
                    g.fillRect(x, y, 1, 1);
                }
            }
        }
        g.dispose();
        return image;
    }
}
