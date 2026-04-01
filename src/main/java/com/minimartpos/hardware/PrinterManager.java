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
import java.awt.Image;
import java.io.File;
import java.net.URL;
import javax.imageio.ImageIO;
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
    
    // Printer lookup is slow on Windows — cache the results for 5 minutes
    private static PrintService[] cachedServices = null;
    private static long lastLookupTime = 0;
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000; 


    // ESC/POS command bytes
    private static final byte[] ESC_INIT = { 0x1B, 0x40 }; // Initialize
    private static final byte[] ESC_CENTER = { 0x1B, 0x61, 0x01 }; // Align center
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
        String template = settingsService.get("receipt_template", "");

        String receiptText = PrintUtil.buildReceipt(bill, company, address, phone, footer, template);
        logger.info("Printing receipt for bill: {}", bill.getBillNumber());

        String logoPath = settingsService.get("receipt_logo", "");
        boolean hasLogo = !logoPath.isBlank() && new File(logoPath).exists();

        // FORCE Graphics Mode if Sinhala or other non-ASCII characters are present, OR if there's a logo.
        // This avoids "Japanese/Garbage" text caused by raw byte encoding mismatches and supports graphic images.
        boolean hasNonAscii = !receiptText.chars().allMatch(c -> c < 128);
        
        PrintService printer = findPrinter(printerName);
        if (!hasLogo && !hasNonAscii && printer != null && supportsRaw(printer)) {
            return printRaw(printer, receiptText);
        }

        // Use Graphics rendering for complex scripts (Sinhala), logos, or non-raw printers.
        String fontFamily = settingsService.get("receipt_font", "Monospaced");
        boolean forceBold = "1".equals(settingsService.get("receipt_force_bold", "0"));
        return printText(printer, receiptText, hasLogo ? logoPath : null, fontFamily, forceBold);
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
            chunks.add(ESC_CENTER); // Center the entire text block on the paper roll
            
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

    private boolean printText(PrintService printer, String text, String logoPath, String fontFamily, boolean forceBold) {
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

            String[] lines = text.split("\n");
            int lineCount = lines.length;
            
            // Thermal printers need a defined height even if the roll is infinite.
            // Estimate height: Logo (~150) + Lines (~13pt/line) + Margins (~50)
            double estimatedHeight = 50 + (lineCount * 13) + (logoPath != null ? 160 : 0);
            double finalHeight = Math.max(500, estimatedHeight); // Minimum 500pt to avoid driver issues

            paper.setSize(totalWidth, finalHeight);
            // Small margins for stability
            paper.setImageableArea(leftMargin, 5, printableWidth, finalHeight - 10);
            pf.setPaper(paper);

            job.setPrintable(new ReceiptPrintable(text, logoPath, fontFamily, forceBold), pf);
            job.print();
            logger.info("Receipt printed via Graphics rendering (Font: {}, Bold: {}).", fontFamily, forceBold);
            return true;
        } catch (Exception e) {
            logger.error("printText error: {}", e.getMessage(), e);
            return false;
        }
    }

    private static class ReceiptPrintable implements Printable {
        private final String text;
        private final String logoPath;
        private final String fontFamily;
        private final boolean forceBold;
        
        public ReceiptPrintable(String text, String logoPath, String fontFamily, boolean forceBold) {
            this.text = text;
            this.logoPath = logoPath;
            this.fontFamily = (fontFamily == null || fontFamily.isEmpty()) ? java.awt.Font.MONOSPACED : fontFamily;
            this.forceBold = forceBold;
        }

        @Override
        public int print(java.awt.Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
            if (pageIndex > 0) return NO_SUCH_PAGE;

            java.awt.Graphics2D g2d = (java.awt.Graphics2D) graphics;
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            
            int y = 5;
            double pageWidth = pageFormat.getImageableWidth();

            if (logoPath != null && !logoPath.trim().isEmpty()) {
                try {
                    // 1. Try disk
                    java.io.File file = new java.io.File(logoPath);
                    Image logo = null;
                    if (file.exists()) {
                        logo = ImageIO.read(file);
                    }
                    
                    // 2. Try resource
                    if (logo == null) {
                        String resPath = logoPath.startsWith("/") ? logoPath : "/" + logoPath;
                        URL res = getClass().getResource(resPath);
                        if (res != null) {
                            logo = ImageIO.read(res);
                        }
                    }

                    if (logo != null) {
                        int imgW = logo.getWidth(null);
                        int imgH = logo.getHeight(null);
                        // Scale to max width 150pt
                        int drawW = Math.min(150, imgW);
                        int drawH = (int)((double)drawW / imgW * imgH);
                        int drawX = (int) ((pageWidth - drawW) / 2);
                        g2d.drawImage(logo, drawX, y, drawW, drawH, null);
                        y += drawH + 10;
                    }
                } catch (Exception e) {
                    logger.error("Could not load receipt logo: {}", e.getMessage());
                }
            }

            // Monospaced fonts preserve the column grid formatting (42 chars wide).
            java.awt.Font normalFont = new java.awt.Font(fontFamily, forceBold ? java.awt.Font.BOLD : java.awt.Font.PLAIN, 8);
            java.awt.Font boldFont = new java.awt.Font(fontFamily, java.awt.Font.BOLD, 9);
            g2d.setFont(normalFont);

            String[] lines = text.split("\n");

            // Base X offset to center the monospaced block slightly if there's remaining space
            int charWidth = g2d.getFontMetrics(normalFont).charWidth('W');
            // Cap blockWidth at actual usable paper width to keep columns on-page.
            int blockWidth = Math.min((int) pageWidth, charWidth * 42); 
            int baseX = (int) Math.max(0, (pageWidth - blockWidth) / 2);

            for (String line : lines) {
                try {
                    boolean lineIsBold = forceBold || line.startsWith("[B]");
                    String cleanLine = line.startsWith("[B]") ? line.substring(3) : line;

                    if (cleanLine.trim().isEmpty()) {
                        y += 6;
                        continue;
                    }

                    // Header/Footer centering logic (lines starting with leading spaces)
                    if (cleanLine.startsWith("  ") && !cleanLine.trim().startsWith("-") && !cleanLine.trim().startsWith("*") && !cleanLine.trim().startsWith("=")) {
                        String trimmed = cleanLine.trim();
                        // Headers and specific keywords are bold
                        boolean isMajor = y < 100 || trimmed.contains("TOTAL") || trimmed.contains("THANK YOU") || trimmed.contains("RECEIPT") || trimmed.contains("BILL");
                        g2d.setFont((isMajor || lineIsBold) ? boldFont : normalFont);
                        
                        int stringWidth = g2d.getFontMetrics().stringWidth(trimmed);
                        int segmentX = (int) ((pageWidth - stringWidth) / 2);
                        g2d.drawString(trimmed, segmentX, y);
                        g2d.setFont(normalFont);
                    } else {
                        // Regular item rows or separator lines
                        g2d.setFont(lineIsBold ? boldFont : normalFont);

                        // --- Smart Column Alignment for Proportional Fonts ---
                        boolean isProportional = !(fontFamily.equalsIgnoreCase("Monospaced") || fontFamily.equalsIgnoreCase("Consolas") || fontFamily.equalsIgnoreCase("Courier New"));

                        if (isProportional && cleanLine.contains("  ")) {
                            // Split by 2 or more spaces
                            String[] segments = cleanLine.split("\\s{2,}");
                            
                            if (segments.length > 1) {
                                for (int i = 0; i < segments.length; i++) {
                                    String segment = segments[i].trim();
                                    if (segment.isEmpty()) continue;
                                    
                                    int segmentX;
                                    if (i == 0) {
                                        segmentX = baseX;
                                    } else if (segments.length == 4) {
                                        // Standard 4-column row (ITEM, QTY, PRICE, TOTAL)
                                        // Use right-alignment within columns for a professional look.
                                        double endRatio = (i == 1) ? 22.0/42.0 : (i == 2) ? 32.0/42.0 : 1.0;
                                        segmentX = (int)(baseX + blockWidth * endRatio) - g2d.getFontMetrics().stringWidth(segment);
                                    } else if (i == segments.length - 1) {
                                        // For multi-segment lines like Totals, right-align the last segment (the value).
                                        segmentX = (int)(baseX + blockWidth) - g2d.getFontMetrics().stringWidth(segment);
                                    } else {
                                        // Fallback for intermediate segments
                                        segmentX = (int)(baseX + blockWidth * (double)i / (segments.length - 1));
                                    }
                                    g2d.drawString(segment, segmentX, y);
                                }
                            } else {
                                g2d.drawString(cleanLine, baseX, y);
                            }
                        } else {
                            g2d.drawString(cleanLine, baseX, y);
                        }
                        g2d.setFont(normalFont);
                    }
                    
                    y += g2d.getFontMetrics().getHeight() - 1;
                } catch (Exception lineEx) {
                    logger.error("Error drawing line '{}': {}", line, lineEx.getMessage());
                    y += 10; // move down anyway to prevent overlap/hang
                }
            }

            return PAGE_EXISTS;
        }
    }

    private PrintService findPrinter(String name) {
        if (name == null || name.isBlank()) {
            return PrintServiceLookup.lookupDefaultPrintService();
        }

        long now = System.currentTimeMillis();
        if (cachedServices == null || (now - lastLookupTime) > CACHE_DURATION_MS) {
            logger.debug("Performing fresh printer lookup (slow)...");
            cachedServices = PrintServiceLookup.lookupPrintServices(null, null);
            lastLookupTime = now;
        } else {
            logger.debug("Using cached printer list (instant).");
        }

        if (cachedServices == null) {
            logger.warn("No print services available on this system.");
            return PrintServiceLookup.lookupDefaultPrintService();
        }

        for (PrintService s : cachedServices) {
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
