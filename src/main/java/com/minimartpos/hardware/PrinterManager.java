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
    private static final byte[] ESC_LEFT = { 0x1B, 0x61, 0x00 }; // Align left
    private static final byte[] ESC_FEED_CUT = { 0x1B, 0x64, 0x04, 0x1D, 0x56, 0x42, 0x00 }; // Feed 4 lines + partial cut

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

        String receiptText = PrintUtil.buildReceipt(bill, company, address, phone, footer);
        logger.info("Printing receipt for bill: {}", bill.getBillNumber());

        // FORCE Graphics Mode if Sinhala or other non-ASCII characters are present.
        // This avoids "Japanese/Garbage" text caused by raw byte encoding mismatches.
        boolean hasNonAscii = !receiptText.chars().allMatch(c -> c < 128);
        
        PrintService printer = findPrinter(printerName);
        if (!hasNonAscii && printer != null && supportsRaw(printer)) {
            return printRaw(printer, receiptText);
        }

        // Use Graphics rendering for complex scripts (Sinhala) or non-raw printers.
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
            chunks.add(ESC_LEFT); // Default to left-align (PrintUtil handles internal spacing)
            
            // Send text as UTF-8 bytes (modern printers only; old ones will need the Graphic path)
            chunks.add(text.getBytes(Charset.forName("UTF-8")));
            
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
            PrinterJob job = PrinterJob.getPrinterJob();
            if (printer != null) {
                job.setPrintService(printer);
            }

            PageFormat pf = job.defaultPage();
            Paper paper = new Paper();
            
            // Standard 80mm (3.125 inch) paper = 226.7 pts
            double totalWidth = 80 * 72 / 25.4; 
            // Standard printable area for 80mm thermal is ~72mm = 204 pts
            double printableWidth = 72 * 72 / 25.4;
            // Center the printable area 
            double leftMargin = (totalWidth - printableWidth) / 2;
            
            paper.setSize(totalWidth, pf.getHeight());
            // Small margins for stability
            paper.setImageableArea(leftMargin, 5, printableWidth, pf.getHeight() - 10);
            pf.setPaper(paper);

            job.setPrintable(new ReceiptPrintable(text), pf);
            job.print();
            logger.info("Receipt printed via Graphics rendering (Centered, 8.5pt, Sinhala support).");
            return true;
        } catch (Exception e) {
            logger.error("printText error: {}", e.getMessage(), e);
            return false;
        }
    }

    private static class ReceiptPrintable implements Printable {
        private final String text;
        
        public ReceiptPrintable(String text) {
            this.text = text;
        }

        @Override
        public int print(java.awt.Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
            if (pageIndex > 0) return NO_SUCH_PAGE;

            java.awt.Graphics2D g2d = (java.awt.Graphics2D) graphics;
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            
            // SansSerif handles Sinhala characters perfectly by mapping to Nirmala UI on Windows.
            // 8.5pt fits comfortably within 72mm for 42 characters.
            java.awt.Font normalFont = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 8);
            java.awt.Font boldFont = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 9);
            g2d.setFont(normalFont);

            int y = 10;
            double pageWidth = pageFormat.getImageableWidth();
            String[] lines = text.split("\n");

            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    y += 6;
                    continue;
                }

                // Header/Footer centering logic
                // If a line starts with leading spaces, it was meant to be centered by PrintUtil.
                // We trim it and center it mathematically for consistent proportional font alignment.
                if (line.startsWith("  ") && !line.trim().startsWith("-") && !line.trim().startsWith("*")) {
                    String trimmed = line.trim();
                    // Headers and specific keywords are bold
                    boolean isMajor = y < 100 || trimmed.contains("TOTAL") || trimmed.contains("THANK YOU");
                    g2d.setFont(isMajor ? boldFont : normalFont);
                    
                    int stringWidth = g2d.getFontMetrics().stringWidth(trimmed);
                    int x = (int) ((pageWidth - stringWidth) / 2);
                    g2d.drawString(trimmed, x, y);
                    g2d.setFont(normalFont);
                } else {
                    // Regular item rows or separator lines
                    g2d.drawString(line, 0, y);
                }
                
                y += g2d.getFontMetrics().getHeight() - 1;
            }

            return PAGE_EXISTS;
        }
    }

    private PrintService findPrinter(String name) {
        if (name == null || name.isBlank()) {
            return PrintServiceLookup.lookupDefaultPrintService();
        }
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService s : services) {
            String printerName = s.getName();
            if (printerName.equalsIgnoreCase(name.trim()))
                return s;
        }
        logger.warn("Printer '{}' not found. Using default.", name);
        return PrintServiceLookup.lookupDefaultPrintService();
    }

    private boolean supportsRaw(PrintService printer) {
        try {
            for (DocFlavor f : printer.getSupportedDocFlavors()) {
                if (DocFlavor.BYTE_ARRAY.AUTOSENSE.equals(f))
                    return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
