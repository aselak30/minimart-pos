package com.minimartpos.service;

import com.minimartpos.model.Product;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Handles Excel (.xlsx) import and export using Apache POI.
 *
 * Features:
 *  - Export products to Excel with full formatting
 *  - Import products from template with validation
 *  - Export any report data (List<Map<String,Object>>) to Excel
 *  - Download a blank import template
 */
public class ExcelService {

    private static final Logger logger = LogManager.getLogger(ExcelService.class);

    // Product import/export column headers (must match template)
    private static final String[] PRODUCT_HEADERS = {
        "Barcode", "Product Name", "Brand", "Size/Weight", "Category",
        "Selling Price", "Cost Price", "Tax Rate (%)", "Stock Qty",
        "Reorder Level", "Discount Allowed", "Max Discount (%)",
        "Expiry Date", "Batch Number", "Location", "Active"
    };

    // ── Product Export ────────────────────────────────────────────────────────

    /**
     * Exports a list of products to an Excel file.
     *
     * @param products      Products to export
     * @param outputPath    File path to write to
     * @param includeCost   Whether to include cost price column (admin only)
     */
    public void exportProducts(List<Product> products, String outputPath,
                               boolean includeCost) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            XSSFSheet sheet = wb.createSheet("Products");
            sheet.setDefaultColumnWidth(18);

            // Styles
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle moneyStyle  = createMoneyStyle(wb);
            CellStyle dateStyle   = createDateStyle(wb);
            CellStyle altStyle    = createAltRowStyle(wb);

            // Header row
            Row hdr = sheet.createRow(0);
            int col = 0;
            for (String h : PRODUCT_HEADERS) {
                if (!includeCost && h.equals("Cost Price")) { col++; continue; }
                Cell c = hdr.createCell(col++);
                c.setCellValue(h);
                c.setCellStyle(headerStyle);
            }

            // Data rows
            int rowNum = 1;
            for (Product p : products) {
                Row row = sheet.createRow(rowNum);
                CellStyle rowStyle = (rowNum % 2 == 0) ? altStyle : null;
                col = 0;

                setCell(row, col++, p.getBarcode(), rowStyle);
                setCell(row, col++, p.getName(), rowStyle);
                setCell(row, col++, p.getBrand(), rowStyle);
                setCell(row, col++, p.getSizeWeight(), rowStyle);
                setCell(row, col++, p.getCategoryName(), rowStyle);

                Cell priceCell = row.createCell(col++);
                priceCell.setCellValue(p.getUnitPrice() != null
                    ? p.getUnitPrice().doubleValue() : 0.0);
                priceCell.setCellStyle(moneyStyle);

                if (includeCost) {
                    Cell costCell = row.createCell(col++);
                    costCell.setCellValue(p.getCostPrice() != null
                        ? p.getCostPrice().doubleValue() : 0.0);
                    costCell.setCellStyle(moneyStyle);
                } else { col++; }

                setNumericCell(row, col++, p.getTaxRate() != null
                    ? p.getTaxRate().doubleValue() : 0.0, rowStyle);
                setNumericCell(row, col++, p.getStockQuantity(), rowStyle);
                setNumericCell(row, col++, p.getReorderLevel(), rowStyle);
                setCell(row, col++, p.isDiscountAllowed() ? "YES" : "NO", rowStyle);
                setNumericCell(row, col++, p.getMaxDiscountPercent() != null
                    ? p.getMaxDiscountPercent().doubleValue() : 0.0, rowStyle);

                if (p.getExpiryDate() != null) {
                    Cell dc = row.createCell(col++);
                    dc.setCellValue(java.util.Date.from(
                        p.getExpiryDate().atStartOfDay(ZoneId.systemDefault()).toInstant()));
                    dc.setCellStyle(dateStyle);
                } else { row.createCell(col++).setCellValue(""); }

                setCell(row, col++, p.getBatchNumber(), rowStyle);
                setCell(row, col++, p.getLocation(), rowStyle);
                setCell(row, col++, p.isActive() ? "YES" : "NO", rowStyle);

                rowNum++;
            }

            // Auto-size key columns
            for (int i = 0; i < PRODUCT_HEADERS.length; i++) sheet.autoSizeColumn(i);
            // Freeze header row
            sheet.createFreezePane(0, 1);
            // Auto-filter
            sheet.setAutoFilter(new CellRangeAddress(0, rowNum - 1, 0, PRODUCT_HEADERS.length - 1));

            writeFile(wb, outputPath);
            logger.info("Exported {} products to {}", products.size(), outputPath);
        }
    }

    // ── Product Import ────────────────────────────────────────────────────────

    /**
     * Imports products from an Excel file.
     * Returns an ImportResult with valid products and any row errors.
     */
    public ImportResult importProducts(String filePath) {
        ImportResult result = new ImportResult();

        try (FileInputStream fis = new FileInputStream(filePath);
             XSSFWorkbook wb = new XSSFWorkbook(fis)) {

            XSSFSheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                result.addError(0, "No sheet found in workbook.");
                return result;
            }

            // Read header row to determine column mapping
            Row header = sheet.getRow(0);
            if (header == null) {
                result.addError(0, "No header row found.");
                return result;
            }
            Map<String, Integer> colMap = buildColumnMap(header);

            // Validate required columns exist
            for (String required : new String[]{"Barcode", "Product Name", "Selling Price"}) {
                if (!colMap.containsKey(required)) {
                    result.addError(0, "Required column missing: " + required);
                }
            }
            if (!result.getErrors().isEmpty()) return result;

            // Read data rows
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                try {
                    Product p = parseProductRow(row, colMap, i);
                    result.addProduct(p);
                } catch (Exception e) {
                    result.addError(i + 1, e.getMessage());
                }
            }

            logger.info("Import parsed: {} valid products, {} errors",
                        result.getProducts().size(), result.getErrors().size());

        } catch (Exception e) {
            logger.error("importProducts error: {}", e.getMessage(), e);
            result.addError(0, "Failed to open file: " + e.getMessage());
        }

        return result;
    }

    private Product parseProductRow(Row row, Map<String, Integer> cols, int rowNum) {
        Product p = new Product();

        String barcode = getString(row, cols, "Barcode");
        String name    = getString(row, cols, "Product Name");
        if (barcode == null || barcode.isBlank())
            throw new IllegalArgumentException("Row " + (rowNum+1) + ": Barcode is required");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Row " + (rowNum+1) + ": Product Name is required");

        p.setBarcode(barcode.trim());
        p.setName(name.trim());
        p.setBrand(getString(row, cols, "Brand"));
        p.setSizeWeight(getString(row, cols, "Size/Weight"));

        double price = getDouble(row, cols, "Selling Price", 0.0);
        if (price <= 0)
            throw new IllegalArgumentException("Row " + (rowNum+1) + ": Selling Price must be > 0");
        p.setUnitPrice(BigDecimal.valueOf(price));

        double cost = getDouble(row, cols, "Cost Price", 0.0);
        p.setCostPrice(BigDecimal.valueOf(cost));
        p.setTaxRate(BigDecimal.valueOf(getDouble(row, cols, "Tax Rate (%)", 0.0)));
        p.setStockQuantity((int) getDouble(row, cols, "Stock Qty", 0.0));
        p.setReorderLevel(Math.max(1, (int) getDouble(row, cols, "Reorder Level", 5.0)));

        String discAllowed = getString(row, cols, "Discount Allowed");
        p.setDiscountAllowed(!"NO".equalsIgnoreCase(discAllowed));

        double maxDisc = getDouble(row, cols, "Max Discount (%)", 0.0);
        if (maxDisc > 0) p.setMaxDiscountPercent(BigDecimal.valueOf(maxDisc));

        p.setBatchNumber(getString(row, cols, "Batch Number"));
        p.setLocation(getString(row, cols, "Location"));

        String active = getString(row, cols, "Active");
        p.setActive(!"NO".equalsIgnoreCase(active));

        return p;
    }

    // ── Blank Template Download ───────────────────────────────────────────────

    /**
     * Writes a blank import template with headers and example row.
     */
    public void writeImportTemplate(String outputPath) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Products");
            sheet.setDefaultColumnWidth(18);

            CellStyle headerStyle = createHeaderStyle(wb);

            // Header row
            Row hdr = sheet.createRow(0);
            for (int i = 0; i < PRODUCT_HEADERS.length; i++) {
                Cell c = hdr.createCell(i);
                c.setCellValue(PRODUCT_HEADERS[i]);
                c.setCellStyle(headerStyle);
            }

            // Example row
            Row ex = sheet.createRow(1);
            String[] example = {
                "4890008100309", "Coca Cola 330ml", "Coca-Cola", "330ml", "Beverages",
                "120.00", "85.00", "0", "100", "10", "YES", "15",
                "", "", "Aisle 3", "YES"
            };
            for (int i = 0; i < example.length; i++) {
                ex.createCell(i).setCellValue(example[i]);
            }

            for (int i = 0; i < PRODUCT_HEADERS.length; i++) sheet.autoSizeColumn(i);
            sheet.createFreezePane(0, 1);

            writeFile(wb, outputPath);
            logger.info("Import template written to {}", outputPath);
        }
    }

    // ── Stock Export ──────────────────────────────────────────────────────────

    private static final String[] STOCK_HEADERS = {
        "Barcode", "Product Name", "Brand", "Category",
        "Current Stock", "Reorder Level", "Status",
        "Cost Price", "Selling Price", "Stock Value (Cost)",
        "Batch Number", "Expiry Date", "Location"
    };

    /**
     * Exports current stock levels to Excel.
     * Highlights low-stock rows in yellow, out-of-stock in red.
     */
    public void exportStock(List<Product> products, String outputPath) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Stock Report");
            sheet.setDefaultColumnWidth(18);

            CellStyle headerStyle   = createHeaderStyle(wb);
            CellStyle moneyStyle    = createMoneyStyle(wb);
            CellStyle dateStyle     = createDateStyle(wb);
            CellStyle lowStyle      = createLowStockStyle(wb);
            CellStyle outStyle      = createOutOfStockStyle(wb);
            CellStyle altStyle      = createAltRowStyle(wb);

            // Title
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Stock Report — Generated " + java.time.LocalDate.now());
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, STOCK_HEADERS.length - 1));
            XSSFCellStyle titleStyle = wb.createCellStyle();
            XSSFFont titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 13);
            titleStyle.setFont(titleFont);
            titleRow.createCell(0).setCellStyle(titleStyle);

            // Header row
            Row hdr = sheet.createRow(1);
            for (int i = 0; i < STOCK_HEADERS.length; i++) {
                Cell c = hdr.createCell(i);
                c.setCellValue(STOCK_HEADERS[i]);
                c.setCellStyle(headerStyle);
            }

            int rowNum = 2;
            int lowCount = 0, outCount = 0;

            for (Product p : products) {
                Row row = sheet.createRow(rowNum);
                boolean outOfStock = p.getStockQuantity() <= 0;
                boolean lowStock   = !outOfStock && p.getStockQuantity() <= p.getReorderLevel();

                CellStyle rowStyle = outOfStock ? outStyle
                                   : lowStock   ? lowStyle
                                   : (rowNum % 2 == 0 ? altStyle : null);

                if (outOfStock) outCount++;
                else if (lowStock) lowCount++;

                setStyledCell(row, 0, p.getBarcode(), rowStyle);
                setStyledCell(row, 1, p.getName(), rowStyle);
                setStyledCell(row, 2, p.getBrand() != null ? p.getBrand() : "", rowStyle);
                setStyledCell(row, 3, p.getCategoryName() != null ? p.getCategoryName() : "", rowStyle);

                // Current Stock — numeric
                Cell stockCell = row.createCell(4);
                stockCell.setCellValue(p.getStockQuantity());
                if (rowStyle != null) stockCell.setCellStyle(rowStyle);

                Cell reorderCell = row.createCell(5);
                reorderCell.setCellValue(p.getReorderLevel());
                if (rowStyle != null) reorderCell.setCellStyle(rowStyle);

                setStyledCell(row, 6,
                    outOfStock ? "OUT OF STOCK" : lowStock ? "LOW STOCK" : "OK",
                    rowStyle);

                // Cost / Selling price — money style
                Cell costCell = row.createCell(7);
                costCell.setCellValue(p.getCostPrice() != null ? p.getCostPrice().doubleValue() : 0.0);
                costCell.setCellStyle(moneyStyle);

                Cell priceCell = row.createCell(8);
                priceCell.setCellValue(p.getUnitPrice() != null ? p.getUnitPrice().doubleValue() : 0.0);
                priceCell.setCellStyle(moneyStyle);

                // Stock value = qty × cost
                Cell valueCell = row.createCell(9);
                double stockValue = p.getStockQuantity() *
                    (p.getCostPrice() != null ? p.getCostPrice().doubleValue() : 0.0);
                valueCell.setCellValue(stockValue);
                valueCell.setCellStyle(moneyStyle);

                setStyledCell(row, 10, p.getBatchNumber() != null ? p.getBatchNumber() : "", rowStyle);

                if (p.getExpiryDate() != null) {
                    Cell dc = row.createCell(11);
                    dc.setCellValue(java.util.Date.from(
                        p.getExpiryDate().atStartOfDay(ZoneId.systemDefault()).toInstant()));
                    dc.setCellStyle(dateStyle);
                } else {
                    setStyledCell(row, 11, "", rowStyle);
                }

                setStyledCell(row, 12, p.getLocation() != null ? p.getLocation() : "", rowStyle);
                rowNum++;
            }

            // Summary totals row
            Row totalRow = sheet.createRow(rowNum + 1);
            XSSFCellStyle summaryStyle = wb.createCellStyle();
            XSSFFont boldFont = wb.createFont();
            boldFont.setBold(true);
            summaryStyle.setFont(boldFont);

            Cell sumLabel = totalRow.createCell(0);
            sumLabel.setCellValue("SUMMARY: " + products.size() + " products | " +
                lowCount + " low stock | " + outCount + " out of stock");
            sumLabel.setCellStyle(summaryStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowNum + 1, rowNum + 1, 0, 8));

            for (int i = 0; i < STOCK_HEADERS.length; i++) sheet.autoSizeColumn(i);
            sheet.createFreezePane(0, 2);
            sheet.setAutoFilter(new CellRangeAddress(1, rowNum - 1, 0, STOCK_HEADERS.length - 1));

            writeFile(wb, outputPath);
            logger.info("Stock exported: {} products ({} low, {} out) to {}",
                        products.size(), lowCount, outCount, outputPath);
        }
    }

    private CellStyle createLowStockStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private CellStyle createOutOfStockStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private void setStyledCell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value : "");
        if (style != null) c.setCellStyle(style);
    }

    // ── Report Export ─────────────────────────────────────────────────────────

    /**
     * Exports any list of Map data to Excel (e.g. from ReportService queries).
     *
     * @param reportTitle  Title shown in the first row
     * @param data         List of row maps (keys become column headers)
     * @param outputPath   Output file path
     */
    public void exportReport(String reportTitle, List<Map<String, Object>> data,
                              String outputPath) throws IOException {
        if (data.isEmpty()) {
            logger.warn("exportReport: no data to export for '{}'", reportTitle);
            return;
        }

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet(reportTitle.substring(0, Math.min(30, reportTitle.length())));
            sheet.setDefaultColumnWidth(16);

            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle moneyStyle  = createMoneyStyle(wb);
            CellStyle altStyle    = createAltRowStyle(wb);

            // Title row
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(reportTitle);

            // Header row
            List<String> keys = new ArrayList<>(data.get(0).keySet());
            Row hdrRow = sheet.createRow(1);
            for (int i = 0; i < keys.size(); i++) {
                Cell c = hdrRow.createCell(i);
                c.setCellValue(formatKey(keys.get(i)));
                c.setCellStyle(headerStyle);
            }

            // Data rows
            int rowNum = 2;
            for (Map<String, Object> rowData : data) {
                Row row = sheet.createRow(rowNum);
                CellStyle rowStyle = (rowNum % 2 == 0) ? altStyle : null;
                for (int i = 0; i < keys.size(); i++) {
                    Object val = rowData.get(keys.get(i));
                    Cell cell = row.createCell(i);
                    if (val instanceof BigDecimal bd) {
                        cell.setCellValue(bd.doubleValue());
                        cell.setCellStyle(moneyStyle);
                    } else if (val instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                        if (rowStyle != null) cell.setCellStyle(rowStyle);
                    } else if (val instanceof LocalDate ld) {
                        cell.setCellValue(java.util.Date.from(
                            ld.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                        cell.setCellStyle(createDateStyle(wb));
                    } else {
                        cell.setCellValue(val != null ? val.toString() : "");
                        if (rowStyle != null) cell.setCellStyle(rowStyle);
                    }
                }
                rowNum++;
            }

            for (int i = 0; i < keys.size(); i++) sheet.autoSizeColumn(i);
            sheet.createFreezePane(0, 2);
            sheet.setAutoFilter(new CellRangeAddress(1, rowNum - 1, 0, keys.size() - 1));

            writeFile(wb, outputPath);
            logger.info("Report '{}' exported: {} rows to {}", reportTitle, data.size(), outputPath);
        }
    }

    // ── Style Factories ───────────────────────────────────────────────────────

    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = wb.createFont();
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setBold(true);
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createMoneyStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        return style;
    }

    private CellStyle createDateStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("dd/mm/yyyy"));
        return style;
    }

    private CellStyle createAltRowStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void writeFile(Workbook wb, String path) throws IOException {
        File file = new File(path);
        file.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            wb.write(fos);
        }
    }

    private Map<String, Integer> buildColumnMap(Row headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell c = headerRow.getCell(i);
            if (c != null) map.put(c.getStringCellValue().trim(), i);
        }
        return map;
    }

    private boolean isRowEmpty(Row row) {
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell c = row.getCell(i);
            if (c != null && c.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    private String getString(Row row, Map<String, Integer> cols, String key) {
        Integer idx = cols.get(key);
        if (idx == null) return null;
        Cell c = row.getCell(idx);
        if (c == null) return null;
        return c.getCellType() == CellType.STRING
            ? c.getStringCellValue().trim()
            : String.valueOf((long) c.getNumericCellValue());
    }

    private double getDouble(Row row, Map<String, Integer> cols, String key, double def) {
        Integer idx = cols.get(key);
        if (idx == null) return def;
        Cell c = row.getCell(idx);
        if (c == null) return def;
        try {
            return c.getCellType() == CellType.NUMERIC
                ? c.getNumericCellValue()
                : Double.parseDouble(c.getStringCellValue().trim());
        } catch (Exception e) { return def; }
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value : "");
        if (style != null) c.setCellStyle(style);
    }

    private void setNumericCell(Row row, int col, double value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
    }

    private String formatKey(String key) {
        return key.replaceAll("([A-Z])", " $1").trim();
    }

    // ── ImportResult ──────────────────────────────────────────────────────────

    public static class ImportResult {
        private final List<Product>          products = new ArrayList<>();
        private final List<String>           errors   = new ArrayList<>();

        public void addProduct(Product p)          { products.add(p); }
        public void addError(int row, String msg)  { errors.add("Row " + row + ": " + msg); }
        public List<Product> getProducts()         { return products; }
        public List<String>  getErrors()           { return errors; }
        public boolean       hasErrors()           { return !errors.isEmpty(); }
        public int           successCount()        { return products.size(); }
    }
}
