package com.minimartpos.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.minimartpos.model.Bill;
import com.minimartpos.model.BillItem;
import com.minimartpos.util.CurrencyUtil;
import com.minimartpos.util.DateUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Generates PDF documents using iText 7.
 *
 * Supported outputs:
 *  1. Sales report PDF (date range, summary table, breakdown)
 *  2. Receipt PDF (A5/thermal size — useful for email receipts)
 *  3. Stock report PDF (products with low stock / expiry alerts)
 */
public class PdfService {

    private static final Logger logger = LogManager.getLogger(PdfService.class);

    // Brand colours (matching main.css)
    private static final DeviceRgb PRIMARY      = new DeviceRgb(0x19, 0x76, 0xD2);
    private static final DeviceRgb HEADER_BG    = new DeviceRgb(0x19, 0x76, 0xD2);
    private static final DeviceRgb ALT_ROW      = new DeviceRgb(0xF5, 0xF7, 0xFA);
    private static final DeviceRgb SUCCESS      = new DeviceRgb(0x38, 0x8E, 0x3C);
    private static final DeviceRgb DANGER       = new DeviceRgb(0xD3, 0x2F, 0x2F);
    private static final DeviceRgb WARNING_CLR  = new DeviceRgb(0xF5, 0x7C, 0x00);

    private final SettingsService settingsService = new SettingsService();

    // ── Sales Report ──────────────────────────────────────────────────────────

    /**
     * Generates a complete sales report PDF.
     *
     * @param summary    From ReportService.getSalesSummary()
     * @param daily      From ReportService.getDailyTrend()
     * @param byCashier  From ReportService.getSalesByCashier()
     * @param from       Date range start
     * @param to         Date range end
     * @param outputPath Target file path
     */
    public void generateSalesReport(Map<String, Object> summary,
                                     List<Map<String, Object>> daily,
                                     List<Map<String, Object>> byCashier,
                                     LocalDate from, LocalDate to,
                                     String outputPath) throws IOException {
        ensureDir(outputPath);
        try (PdfWriter writer  = new PdfWriter(outputPath);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);
            PdfFont bold    = font(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = font(StandardFonts.HELVETICA);

            // ── Company header ──
            addCompanyHeader(doc, bold, regular);

            // ── Report title ──
            doc.add(new Paragraph("Sales Report")
                .setFont(bold).setFontSize(18).setFontColor(PRIMARY)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(8));

            doc.add(new Paragraph("Period: " +
                from.format(DateUtil.DISPLAY_DATE) + " to " + to.format(DateUtil.DISPLAY_DATE))
                .setFont(regular).setFontSize(11)
                .setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.GRAY));

            doc.add(new LineSeparator(
                new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(0.5f)).setMarginTop(8));

            // ── KPI summary ──
            doc.add(new Paragraph("Summary").setFont(bold).setFontSize(13)
                .setFontColor(PRIMARY).setMarginTop(12));

            Table kpiTable = new Table(new float[]{3, 3, 3, 3}).useAllAvailableWidth();
            addKpiCell(kpiTable, "Total Sales",
                CurrencyUtil.format(getBD(summary, "totalSales")), bold, regular);
            addKpiCell(kpiTable, "Total Bills",
                String.valueOf(summary.getOrDefault("billCount", 0)), bold, regular);
            addKpiCell(kpiTable, "Avg Bill",
                CurrencyUtil.format(getBD(summary, "avgBill")), bold, regular);
            addKpiCell(kpiTable, "Total Profit",
                CurrencyUtil.format(getBD(summary, "totalProfit")), bold, regular);
            doc.add(kpiTable);

            // ── Daily trend table ──
            if (!daily.isEmpty()) {
                doc.add(new Paragraph("Daily Breakdown").setFont(bold).setFontSize(13)
                    .setFontColor(PRIMARY).setMarginTop(16));
                String[] dailyHeaders = {"Date", "Bills", "Sales", "Profit"};
                Table dailyTable = buildTable(dailyHeaders, daily, bold, regular,
                    new String[]{"day", "billCount", "sales", "profit"});
                doc.add(dailyTable);
            }

            // ── By cashier table ──
            if (!byCashier.isEmpty()) {
                doc.add(new Paragraph("Sales by Cashier").setFont(bold).setFontSize(13)
                    .setFontColor(PRIMARY).setMarginTop(16));
                String[] cashierHeaders = {"Cashier", "Bills", "Sales", "Profit"};
                Table cashierTable = buildTable(cashierHeaders, byCashier, bold, regular,
                    new String[]{"cashier", "bills", "sales", "profit"});
                doc.add(cashierTable);
            }

            // ── Footer ──
            addReportFooter(doc, regular);

            logger.info("Sales report PDF generated: {}", outputPath);
        }
    }

    // ── Receipt PDF ───────────────────────────────────────────────────────────

    /**
     * Generates a receipt PDF (A6 size) suitable for emailing to customers.
     */
    public void generateReceiptPdf(Bill bill, String outputPath) throws IOException {
        ensureDir(outputPath);
        try (PdfWriter writer  = new PdfWriter(outputPath);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A6)) {

            doc.setMargins(20, 20, 20, 20);
            PdfFont bold    = font(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = font(StandardFonts.HELVETICA);

            // Header
            doc.add(new Paragraph(settingsService.company())
                .setFont(bold).setFontSize(14).setTextAlignment(TextAlignment.CENTER)
                .setFontColor(PRIMARY));
            doc.add(new Paragraph("RECEIPT").setFont(bold).setFontSize(11)
                .setTextAlignment(TextAlignment.CENTER));

            doc.add(new LineSeparator(
                new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(0.5f)));

            // Bill info
            addLine(doc, regular, "Bill #: ", bill.getBillNumber(), 9);
            addLine(doc, regular, "Date: ",
                bill.getFinalizedAt() != null
                    ? DateUtil.formatDateTime(bill.getFinalizedAt()) : "—", 9);
            addLine(doc, regular, "Cashier: ", bill.getCashierName(), 9);
            if (bill.getCustomerName() != null && !bill.getCustomerName().equals("Walk-in")) {
                addLine(doc, regular, "Customer: ", bill.getCustomerName(), 9);
            }

            doc.add(new LineSeparator(
                new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(0.3f)));

            // Items table
            Table items = new Table(new float[]{4, 1, 2, 2}).useAllAvailableWidth();
            addTableHeader(items, new String[]{"Item", "Qty", "Price", "Total"}, bold);
            for (BillItem item : bill.getItems()) {
                items.addCell(cell(item.getProductName(), regular, 8));
                items.addCell(cell(String.valueOf(item.getQuantity()), regular, 8)
                    .setTextAlignment(TextAlignment.CENTER));
                items.addCell(cell(CurrencyUtil.formatPlain(item.getUnitPrice()), regular, 8)
                    .setTextAlignment(TextAlignment.RIGHT));
                items.addCell(cell(CurrencyUtil.formatPlain(item.getLineTotal()), regular, 8)
                    .setTextAlignment(TextAlignment.RIGHT));
            }
            doc.add(items);

            doc.add(new LineSeparator(
                new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(0.3f)));

            // Totals
            addTotalLine(doc, bold, regular, "Subtotal:",
                CurrencyUtil.format(bill.getSubtotal()));
            if (bill.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                addTotalLine(doc, bold, regular, "Discount:",
                    "- " + CurrencyUtil.format(bill.getDiscountAmount()));
            }
            if (bill.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
                addTotalLine(doc, bold, regular, "Tax:",
                    CurrencyUtil.format(bill.getTaxAmount()));
            }
            addTotalLine(doc, bold, bold, "TOTAL:",
                CurrencyUtil.format(bill.getTotalAmount()));
            addTotalLine(doc, regular, regular,
                "Paid (" + bill.getPaymentType() + "):",
                CurrencyUtil.format(bill.getPaidAmount()));
            if (bill.getChangeAmount().compareTo(BigDecimal.ZERO) > 0) {
                addTotalLine(doc, regular, regular, "Change:",
                    CurrencyUtil.format(bill.getChangeAmount()));
            }

            // Footer
            doc.add(new Paragraph(settingsService.receiptFooter())
                .setFont(regular).setFontSize(8).setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(12).setFontColor(ColorConstants.GRAY));

            logger.info("Receipt PDF generated: {}", outputPath);
        }
    }

    // ── Stock Report ──────────────────────────────────────────────────────────

    /**
     * Generates a stock alert report PDF (low stock + expiring products).
     */
    public void generateStockReport(List<Map<String, Object>> lowStock,
                                     List<Map<String, Object>> expiring,
                                     String outputPath) throws IOException {
        ensureDir(outputPath);
        try (PdfWriter writer  = new PdfWriter(outputPath);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);
            PdfFont bold    = font(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = font(StandardFonts.HELVETICA);

            addCompanyHeader(doc, bold, regular);

            doc.add(new Paragraph("Stock Alert Report")
                .setFont(bold).setFontSize(18).setFontColor(PRIMARY)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(8));
            doc.add(new Paragraph("Generated: " + DateUtil.formatDateTime(LocalDateTime.now()))
                .setFont(regular).setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.GRAY));
            doc.add(new LineSeparator(
                new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(0.5f)));

            // Low stock section
            doc.add(new Paragraph("⚠  Low Stock Products (" + lowStock.size() + ")")
                .setFont(bold).setFontSize(13).setFontColor(new DeviceRgb(0xF5, 0x7C, 0x00))
                .setMarginTop(12));
            if (lowStock.isEmpty()) {
                doc.add(new Paragraph("All products are well-stocked.")
                    .setFont(regular).setFontSize(10).setFontColor(SUCCESS));
            } else {
                Table t = buildTable(new String[]{"Product", "Category", "Stock", "Reorder"},
                    lowStock, bold, regular, new String[]{"name", "category", "stock", "reorderLevel"});
                doc.add(t);
            }

            // Expiry section
            doc.add(new Paragraph("📅  Expiring Products (" + expiring.size() + ")")
                .setFont(bold).setFontSize(13).setFontColor(DANGER).setMarginTop(16));
            if (expiring.isEmpty()) {
                doc.add(new Paragraph("No products expiring soon.")
                    .setFont(regular).setFontSize(10).setFontColor(SUCCESS));
            } else {
                Table t = buildTable(
                    new String[]{"Product", "Batch", "Stock", "Expiry", "Days Left"},
                    expiring, bold, regular,
                    new String[]{"name", "batch", "stock", "expiryDate", "daysLeft"});
                doc.add(t);
            }

            addReportFooter(doc, regular);
            logger.info("Stock report PDF generated: {}", outputPath);
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private void addCompanyHeader(Document doc, PdfFont bold, PdfFont regular) throws IOException {
        String company = settingsService.company();
        String phone   = settingsService.get("company_phone", "");
        doc.add(new Paragraph(company).setFont(bold).setFontSize(16)
            .setFontColor(PRIMARY).setTextAlignment(TextAlignment.CENTER));
        if (!phone.isBlank()) {
            doc.add(new Paragraph("Tel: " + phone).setFont(regular).setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.GRAY));
        }
    }

    private void addReportFooter(Document doc, PdfFont regular) {
        doc.add(new LineSeparator(
            new com.itextpdf.kernel.pdf.canvas.draw.SolidLine(0.3f)).setMarginTop(12));
        doc.add(new Paragraph("Generated by MiniMart POS • " +
            DateUtil.formatDateTime(LocalDateTime.now()))
            .setFont(regular).setFontSize(8)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(ColorConstants.GRAY));
    }

    private Table buildTable(String[] headers, List<Map<String, Object>> data,
                              PdfFont bold, PdfFont regular, String[] keys) {
        float[] widths = new float[headers.length];
        Arrays.fill(widths, 1f);
        Table table = new Table(widths).useAllAvailableWidth();
        addTableHeader(table, headers, bold);
        boolean alt = false;
        for (Map<String, Object> row : data) {
            for (String key : keys) {
                Object val = row.get(key);
                String text = val instanceof BigDecimal bd
                    ? CurrencyUtil.formatPlain(bd)
                    : val instanceof LocalDate ld ? DateUtil.formatDate(ld)
                    : val != null ? val.toString() : "—";
                Cell c = cell(text, regular, 9);
                if (alt) c.setBackgroundColor(ALT_ROW);
                table.addCell(c);
            }
            alt = !alt;
        }
        return table;
    }

    private void addTableHeader(Table table, String[] headers, PdfFont bold) {
        for (String h : headers) {
            table.addHeaderCell(new Cell()
                .add(new Paragraph(h).setFont(bold).setFontSize(9)
                    .setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(HEADER_BG)
                .setPadding(4));
        }
    }

    private Cell cell(String text, PdfFont font, float size) {
        return new Cell().add(new Paragraph(text != null ? text : "—")
            .setFont(font).setFontSize(size)).setPadding(3);
    }

    private void addKpiCell(Table t, String label, String value, PdfFont bold, PdfFont regular) {
        Cell c = new Cell().setPadding(8)
            .setBackgroundColor(ALT_ROW)
            .add(new Paragraph(label).setFont(regular).setFontSize(9)
                .setFontColor(ColorConstants.GRAY))
            .add(new Paragraph(value).setFont(bold).setFontSize(12)
                .setFontColor(PRIMARY));
        t.addCell(c);
    }

    private void addLine(Document doc, PdfFont font, String label, String value, float size) {
        try {
            doc.add(new Paragraph()
                .add(new Text(label).setFont(
                    PdfFontFactory.createRegisteredFont(StandardFonts.HELVETICA_BOLD))
                    .setFontSize(size))
                .add(new Text(value).setFont(font).setFontSize(size)));
        } catch (java.io.IOException e) {
            doc.add(new Paragraph(label + value).setFont(font).setFontSize(size));
        }
    }

    private void addTotalLine(Document doc, PdfFont labelFont, PdfFont valueFont,
                               String label, String value) {
        Table t = new Table(new float[]{3, 2}).useAllAvailableWidth();
        t.addCell(new Cell().add(new Paragraph(label).setFont(labelFont).setFontSize(9))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        t.addCell(new Cell().add(new Paragraph(value).setFont(valueFont).setFontSize(9)
            .setTextAlignment(TextAlignment.RIGHT))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        doc.add(t);
    }

    private PdfFont font(String name) {
        try { return PdfFontFactory.createFont(name); }
        catch (IOException e) { throw new RuntimeException("Font error", e); }
    }

    private BigDecimal getBD(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
    }

    private void ensureDir(String path) {
        File f = new File(path).getParentFile();
        if (f != null) f.mkdirs();
    }
}
