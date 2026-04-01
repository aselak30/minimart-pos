package com.minimartpos.util;

import com.minimartpos.model.Bill;
import com.minimartpos.model.BillItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Formats a Bill into a plain-text receipt string,
 * sized for a standard 80mm (42-char) thermal receipt printer.
 *
 * The output can be:
 * 1. Sent directly to a PrinterManager for ESC/POS printing
 * 2. Previewed in a TextArea in the UI
 * 3. Saved to PDF via iText
 */
public final class PrintUtil {

    private PrintUtil() {
    }

    public static final int WIDTH = 42; // characters per line for 80mm paper

    private static final DateTimeFormatter RECEIPT_DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public static final String DEFAULT_TEMPLATE = 
        "{repeat(*)42}\n" +
        "{center({company_name})}\n" +
        "{center({company_address})}\n" +
        "{center(Tel: {company_phone})}\n" +
        "{repeat(*)42}\n" +
        "{center({header})}\n" +
        "{repeat(-)42}\n" +
        "Bill #: {bill_number}\n" +
        "Cashier: {cashier_name}\n" +
        "Customer: {customer_name}\n" +
        "Date: {date_time}\n" +
        "{repeat(-)42}\n" +
        "ITEM               QTY     PRICE     TOTAL\n" +
        "{repeat(-)42}\n" +
        "{items}\n" +
        "{repeat(-)42}\n" +
        "{totals}\n" +
        "{repeat(=)42}\n" +
        "{payment_info}\n" +
        "{repeat(-)42}\n" +
        "{center({item_count} item(s))}\n\n" +
        "{center({receipt_footer})}\n" +
        "{center(Thank you!)}\n" +
        "\n\n\n";

    /**
     * Builds the full receipt text for a finalized bill.
     *
     * @param bill           The finalized bill
     * @param companyName    Company name (from settings)
     * @param companyAddress Company address
     * @param companyPhone   Company phone
     * @param footer         Receipt footer message
     * @param template       Template logic mapping
     * @return Multi-line receipt string
     */
    public static String buildReceipt(Bill bill, String companyName,
            String companyAddress, String companyPhone, String footer, String template) {
        if (template == null || template.isBlank()) {
            template = DEFAULT_TEMPLATE;
        }

        // 1. Build generic data mappings
        String headerTx = "OFFICIAL RECEIPT";
        if (bill.getStatus() == Bill.Status.VOIDED) headerTx = "VOIDED BILL";
        else if (bill.getStatus() == Bill.Status.REFUNDED) headerTx = "REFUNDED BILL";

        String customer = (bill.getCustomerName() != null && !bill.getCustomerName().isBlank() && !bill.getCustomerName().equals("Walk-in"))
            ? bill.getCustomerName() : "";
        
        String dt = (bill.getFinalizedAt() != null) ? bill.getFinalizedAt().format(RECEIPT_DT) : LocalDateTime.now().format(RECEIPT_DT);

        // Build items
        StringBuilder itemsBuilder = new StringBuilder();
        for (BillItem item : bill.getItems()) {
            String name = item.getProductName();
            String qty = String.valueOf(item.getQuantity());
            String unitPrice = CurrencyUtil.formatPlain(item.getUnitPrice());
            String lineTotal = CurrencyUtil.formatPlain(item.getLineTotal());

            if (name.length() <= 17) {
                // Short name: all on one line
                itemsBuilder.append(rowLine(name, qty, unitPrice, lineTotal)).append("\n");
            } else {
                // Long name: name on first row, price info on second row
                String displayName = name;
                if (displayName.length() > WIDTH) {
                    displayName = displayName.substring(0, WIDTH - 3) + "...";
                }
                itemsBuilder.append(displayName).append("\n");
                itemsBuilder.append(rowLine("", qty, unitPrice, lineTotal)).append("\n");
            }

            if (item.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                // Ensure 2+ spaces for Smart Column Alignment
                itemsBuilder.append("  Disc: -" + item.getDiscountPercent().toPlainString() +
                        "%  =  -" + CurrencyUtil.formatPlain(item.getDiscountAmount())).append("\n");
            }
        }
        // Remove trailing newline
        if (itemsBuilder.length() > 0) itemsBuilder.setLength(itemsBuilder.length() - 1);

        // Build Totals
        StringBuilder totalsBuilder = new StringBuilder();
        totalsBuilder.append(totalLine("Subtotal:", CurrencyUtil.formatPlain(bill.getSubtotal()))).append("\n");
        if (bill.getDiscountAmount().compareTo(BigDecimal.ZERO) >= 0) {
            String discLabel = "TOTAL DISCOUNT:";
            totalsBuilder.append(totalLine(discLabel, "-" + CurrencyUtil.formatPlain(bill.getDiscountAmount()))).append("\n");
        }
        if (bill.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            totalsBuilder.append(totalLine("Tax:", CurrencyUtil.formatPlain(bill.getTaxAmount()))).append("\n");
        }
        totalsBuilder.append(repeat("=", WIDTH)).append("\n");
        totalsBuilder.append(totalLine("GRAND TOTAL:", CurrencyUtil.format(bill.getTotalAmount())));

        // Build Payment
        StringBuilder paymentBuilder = new StringBuilder();
        paymentBuilder.append(totalLine("Paid (" + bill.getPaymentType().name() + "):",
                CurrencyUtil.formatPlain(bill.getPaidAmount())));
        if (bill.getChangeAmount().compareTo(BigDecimal.ZERO) > 0) {
            paymentBuilder.append("\n").append(totalLine("Change:", CurrencyUtil.formatPlain(bill.getChangeAmount())));
        }

        // Execute Replacements
        String result = template.replace("{company_name}", companyName == null ? "" : companyName.toUpperCase())
                .replace("{company_address}", companyAddress == null ? "" : companyAddress)
                .replace("{company_phone}", companyPhone == null ? "" : companyPhone)
                .replace("{header}", headerTx)
                .replace("{bill_number}", bill.getBillNumber())
                .replace("{cashier_name}", bill.getCashierName() == null ? "" : bill.getCashierName())
                .replace("{customer_name}", customer)
                .replace("{date_time}", dt)
                .replace("{items}", itemsBuilder.toString())
                .replace("{totals}", totalsBuilder.toString())
                .replace("{discount_total}", CurrencyUtil.formatPlain(bill.getDiscountAmount()))
                .replace("{payment_info}", paymentBuilder.toString())
                .replace("{item_count}", String.valueOf(bill.getItemCount()))
                .replace("{receipt_footer}", footer == null ? "" : footer);

        // Helper blocks like {repeat(*)42} -> ******************************************
        result = result.replace("{repeat(*)42}", repeat("*", WIDTH))
                       .replace("{repeat(-)42}", repeat("-", WIDTH))
                       .replace("{repeat(=)42}", repeat("=", WIDTH));

        // Evaluate {center(xxx)}
        // We will do a quick regex pass to center blocks
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\{center\\(([^}]*)\\)\\}");
        java.util.regex.Matcher m = p.matcher(result);
        StringBuffer finalOut = new StringBuffer();
        while (m.find()) {
            String inner = m.group(1);
            if (inner == null || inner.trim().isEmpty() || inner.contains("null")) {
                m.appendReplacement(finalOut, ""); // strip empty centers like missing address
            } else {
                // escape backwards slashes and dollar signs for replacement buffer
                m.appendReplacement(finalOut, java.util.regex.Matcher.quoteReplacement(center(inner)));
            }
        }
        m.appendTail(finalOut);
        
        // Evaluate {bold(xxx)}
        java.util.regex.Pattern p2 = java.util.regex.Pattern.compile("\\{bold\\(([^}]*)\\)\\}");
        java.util.regex.Matcher m2 = p2.matcher(finalOut.toString());
        StringBuffer boldOut = new StringBuffer();
        while (m2.find()) {
            String inner = m2.group(1);
            if (inner == null || inner.trim().isEmpty()) {
                m2.appendReplacement(boldOut, ""); 
            } else {
                // Add [B] prefix for PrinterManager to recognize line-boldness
                m2.appendReplacement(boldOut, java.util.regex.Matcher.quoteReplacement("[B]" + inner));
            }
        }
        m2.appendTail(boldOut);
        
        // Final cleanup pass for dead empty lines (avoid ugly spacing if customer/address was missing)
        String cleaned = boldOut.toString().replaceAll("(?m)^[ \\t]*Customer: [ \\t]*$\\n?", "")
                                            .replaceAll("(?m)^\\s*$\\n?", "\n"); 

        return cleaned;
    }

    // ── Formatting Helpers ────────────────────────────────────────────────────

    /** Centers text within WIDTH */
    public static String center(String text) {
        if (text == null)
            text = "";
        if (text.length() >= WIDTH)
            return text;
        int padding = (WIDTH - text.length()) / 2;
        return " ".repeat(padding) + text;
    }

    /** Left-pads text */
    public static String padLeft(String text, int width) {
        if (text.length() >= width)
            return text;
        return " ".repeat(width - text.length()) + text;
    }

    /** Right-pads text */
    public static String padRight(String text, int width) {
        if (text.length() >= width)
            return text.substring(0, width);
        return text + " ".repeat(width - text.length());
    }

    /** Two-column total line: "Label: value" */
    public static String totalLine(String label, String value) {
        int space = WIDTH - label.length() - value.length();
        if (space < 1)
            space = 1;
        return label + " ".repeat(space) + value;
    }

    /** Four-column item line */
    public static String rowLine(String name, String qty, String price, String total) {
        // Widths: name=17, qty=5, price=10, total=10 (Total 42)
        // Right-alignment logic is handled by padLeft here for Raw mode,
        // and by PrinterManager for Graphics (proportional) mode.
        return padRight(name, 17) + padLeft(qty, 5) +
                padLeft(price, 10) + padLeft(total, 10);
    }

    /** Repeats a character N times */
    public static String repeat(String ch, int count) {
        return ch.repeat(count);
    }
}
