package com.minimartpos.hardware;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;

/**
 * Handles input from USB HID barcode scanners.
 *
 * USB scanners act as keyboard emulators — they type the barcode
 * followed by an Enter key press. The POS Terminal's barcode TextField
 * handles this natively via its onAction handler.
 *
 * This class provides:
 *  1. A KeyEvent filter to detect scanner input vs manual typing
 *     (scanners typically complete input in < 100ms)
 *  2. A global scene-level key capture mode for scanners connected
 *     to systems without a dedicated barcode field
 *
 * Usage (global capture mode):
 *   BarcodeScanner scanner = new BarcodeScanner(barcode -> addToCart(barcode));
 *   scene.addEventFilter(KeyEvent.KEY_PRESSED, scanner::onKeyPressed);
 *   scene.addEventFilter(KeyEvent.KEY_TYPED, scanner::onKeyTyped);
 */
public class BarcodeScanner {

    private static final Logger logger = LogManager.getLogger(BarcodeScanner.class);

    /** Maximum ms between keystrokes to count as scanner (not manual typing) */
    private static final long SCANNER_THRESHOLD_MS = 50;

    private final Consumer<String> onBarcodeScanned;
    private final StringBuilder    buffer    = new StringBuilder();
    private long                   lastKeyMs = 0;
    private boolean                capturing = false;

    public BarcodeScanner(Consumer<String> onBarcodeScanned) {
        this.onBarcodeScanned = onBarcodeScanned;
    }

    /**
     * Add to scene: scene.addEventFilter(KeyEvent.KEY_TYPED, scanner::onKeyTyped)
     */
    public void onKeyTyped(KeyEvent event) {
        long now = System.currentTimeMillis();
        long gap = now - lastKeyMs;
        lastKeyMs = now;

        if (gap > 500) {
            // Long pause — start fresh capture
            buffer.setLength(0);
            capturing = false;
        }

        String ch = event.getCharacter();
        if (ch == null || ch.isEmpty() || ch.equals("\r") || ch.equals("\n")) return;

        if (gap < SCANNER_THRESHOLD_MS || capturing) {
            capturing = true;
            buffer.append(ch);
        }
    }

    /**
     * Add to scene: scene.addEventFilter(KeyEvent.KEY_PRESSED, scanner::onKeyPressed)
     */
    public void onKeyPressed(KeyEvent event) {
        if ((event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.F12)
                && capturing && buffer.length() >= 3) {
            String barcode = buffer.toString().trim();
            buffer.setLength(0);
            capturing = false;

            logger.debug("Barcode scanned: {}", barcode);
            if (onBarcodeScanned != null) {
                onBarcodeScanned.accept(barcode);
            }
            event.consume(); // prevent Enter from triggering other handlers
        }
    }

    /** Resets internal state (e.g. when navigating between screens) */
    public void reset() {
        buffer.setLength(0);
        capturing = false;
        lastKeyMs = 0;
    }
}
