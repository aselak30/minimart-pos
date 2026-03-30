package com.minimartpos.hardware;

import com.minimartpos.model.Bill;
import com.minimartpos.service.SettingsService;
import com.minimartpos.util.PrintUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.print.*;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import java.awt.print.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages printing to thermal receipt printers.
 *
 * Two printing paths:
 * 1. RAW ESC/POS bytes — fastest, works with most thermal printers
 * 2. Java Print Service (text) — fallback for non-ESC/POS printers
 *
 * Usage:
 * PrinterManager pm = new PrinterManager();
 * pm.printReceipt(bill);
 *
 * Printer name is configured in settings ("receipt_printer").
 * If blank, uses the system default printer.
 */
public class PrinterManager {

    private static final Logger logger = LogManager.getLogger(PrinterManager.class);

    private final SettingsService settingsService = new SettingsService();

    // ESC/POS command bytes
    private static final byte[] ESC_INIT = { 0x1B, 0x40 }; // Initialize
    private static final byte[] ESC_BOLD_ON = { 0x1B, 0x45, 0x01 }; // Bold on
    private static final byte[] ESC_BOLD_OFF = { 0x1B, 0x45, 0x00 }; // Bold off
    private static final byte[] ESC_CENTER = { 0x1B, 0x61, 0x01 }; // Align center
    private static final byte[] ESC_LEFT = { 0x1B, 0x61, 0x00 }; // Align left
    private static final byte[] ESC_DOUBLE_HEIGHT = { 0x1B, 0x21, 0x10 }; // Double height
    private static final byte[] ESC_NORMAL_SIZE = { 0x1B, 0x21, 0x00 }; // Normal size
    private static final byte[] ESC_FEED_CUT = { 0x1B, 0x64, 0x04, 0x1D, 0x56, 0x42, 0x00 }; // Feed 4 lines + partial
                                                                                             // cut

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Prints a receipt for the given bill.
     * Tries ESC/POS first, falls back to plain text.
     *
     * @return true if printing succeeded
     */
    public boolean printReceipt(Bill bill) {
        String printerName = settingsService.get("receipt_printer", "");
        String company = settingsService.company();
        String address = settingsService.address();
        String phone = settingsService.phone();
        String footer = settingsService.receiptFooter();

        // Build receipt text
        String receiptText = PrintUtil.buildReceipt(bill, company, address, phone, footer);
        logger.info("Printing receipt for bill: {}", bill.getBillNumber());

        // Try raw ESC/POS first
        PrintService printer = findPrinter(printerName);
        if (printer != null && supportsRaw(printer)) {
            return printRaw(printer, receiptText);
        }

        // Fallback: plain text via Java Print Service
        return printText(printer, receiptText);
    }

    /**
     * Opens the cash drawer via ESC/POS pulse command.
     * Most receipt printers have a cash drawer port (RJ11).
     *
     * @return true if command was sent
     */
    public boolean openCashDrawer() {
        String printerName = settingsService.get("receipt_printer", "");
        PrintService printer = findPrinter(printerName);
        if (printer == null) {
            logger.warn("No printer found to open cash drawer.");
            return false;
        }
        byte[] drawerCmd = { 0x1B, 0x70, 0x00, 0x19, (byte) 0xFA }; // ESC p 0 25 250
        return sendRawBytes(printer, drawerCmd);
    }

    /**
     * Returns a list of available printer names on this system.
     */
    public List<String> getAvailablePrinters() {
        List<String> names = new ArrayList<>();
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService s : services) {
            names.add(s.getName());
        }
        return names;
    }

    /**
     * Tests whether the named printer is reachable.
     */
    public boolean testPrinter(String printerName) {
        return findPrinter(printerName) != null;
    }

    // ── ESC/POS Raw Printing ──────────────────────────────────────────────────

    private boolean printRaw(PrintService printer, String text) {
        try {
            List<byte[]> chunks = new ArrayList<>();
            chunks.add(ESC_INIT);
            chunks.add(ESC_CENTER);
            chunks.add(ESC_BOLD_ON);
            chunks.add(ESC_DOUBLE_HEIGHT);
            // text as UTF-8 bytes
            chunks.add(text.getBytes(Charset.forName("UTF-8")));
            chunks.add(ESC_NORMAL_SIZE);
            chunks.add(ESC_BOLD_OFF);
            chunks.add(ESC_LEFT);
            chunks.add(ESC_FEED_CUT);

            // Combine all byte arrays
            int totalLen = chunks.stream().mapToInt(b -> b.length).sum();
            byte[] allBytes = new byte[totalLen];
            int pos = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, allBytes, pos, chunk.length);
                pos += chunk.length;
            }

            return sendRawBytes(printer, allBytes);

        } catch (Exception e) {
            logger.error("printRaw error: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean sendRawBytes(PrintService printer, byte[] data) {
        try {
            DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
            Doc doc = new SimpleDoc(data, flavor, null);
            PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
            DocPrintJob job = printer.createPrintJob();
            job.print(doc, attrs);
            logger.debug("Raw bytes sent to printer: {} bytes", data.length);
            return true;
        } catch (PrintException e) {
            logger.error("sendRawBytes error: {}", e.getMessage(), e);
            return false;
        }
    }

    // ── Plain Text Fallback ───────────────────────────────────────────────────

    private boolean printText(PrintService printer, String text) {
        try {
            if (printer == null) {
                printer = PrintServiceLookup.lookupDefaultPrintService();
            }
            if (printer == null) {
                logger.error("No default printer found.");
                return false;
            }
            DocFlavor flavor = DocFlavor.BYTE_ARRAY.TEXT_PLAIN_UTF_8;
            byte[] bytes = text.getBytes(Charset.forName("UTF-8"));
            Doc doc = new SimpleDoc(bytes, flavor, null);
            DocPrintJob job = printer.createPrintJob();
            job.print(doc, new HashPrintRequestAttributeSet());
            logger.info("Receipt printed via text fallback on: {}", printer.getName());
            return true;
        } catch (Exception e) {
            logger.error("printText error: {}", e.getMessage(), e);
            return false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PrintService findPrinter(String name) {
        if (name == null || name.isBlank()) {
            return PrintServiceLookup.lookupDefaultPrintService();
        }
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService s : services) {
            if (s.getName().equalsIgnoreCase(name.trim()))
                return s;
        }
        logger.warn("Printer '{}' not found. Using default.", name);
        return PrintServiceLookup.lookupDefaultPrintService();
    }

    private boolean supportsRaw(PrintService printer) {
        try {
            printer.getSupportedDocFlavors();
            for (DocFlavor f : printer.getSupportedDocFlavors()) {
                if (DocFlavor.BYTE_ARRAY.AUTOSENSE.equals(f))
                    return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
