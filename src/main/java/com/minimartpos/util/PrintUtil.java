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
 *  1. Sent directly to a PrinterManager for ESC/POS printing
 *  2. Previewed in a TextArea in the UI
 *  3. Saved to PDF via iText
 */
public final class PrintUtil {

    private PrintUtil() {}

    public static final int WIDTH = 42;  // characters per line for 80mm paper

    private static final DateTimeFormatter RECEIPT_DT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Builds the full receipt text for a finalized bill.
     *
     * @param bill         The finalized bill
     * @param companyName  Company name (from settings)
     * @param companyPhone Company phone
     * @param footer       Receipt footer message
     * @return             Multi-line receipt string
     */
    public static String buildReceipt(Bill bill, String companyName,
                                      String companyPhone, String footer) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append(center(companyName)).append("\n");
        if (companyPhone != null && !companyPhone.isBlank()) {
            sb.append(center("Tel: " + companyPhone)).append("\n");
        }
        sb.append(repeat("=", WIDTH)).append("\n");
        sb.append(center("RECEIPT")).append("\n");
        sb.append(repeat("-", WIDTH)).append("\n");

        // Bill info
        sb.append(padRight("Bill #: " + bill.getBillNumber(), WIDTH)).append("\n");
        sb.append(padRight("Cashier: " + bill.getCashierName(), WIDTH)).append("\n");
        if (bill.getCustomerName() != null && !bill.getCustomerName().isBlank()
                && !bill.getCustomerName().equals("Walk-in")) {
            sb.append(padRight("Customer: " + bill.getCustomerName(), WIDTH)).append("\n");
        }
        sb.append(padRight("Date: " +
            (bill.getFinalizedAt() != null
                ? bill.getFinalizedAt().format(RECEIPT_DT)
                : LocalDateTime.now().format(RECEIPT_DT)), WIDTH)).append("\n");
        sb.append(repeat("-", WIDTH)).append("\n");

        // Column headers
        sb.append(rowLine("ITEM", "QTY", "PRICE", "TOTAL")).append("\n");
        sb.append(repeat("-", WIDTH)).append("\n");

        // Items
        for (BillItem item : bill.getItems()) {
            // Product name (may wrap)
            String name = item.getProductName();
            if (name.length() > WIDTH) name = name.substring(0, WIDTH - 2) + "..";
            sb.append(name).append("\n");
            sb.append(rowLine("",
                String.valueOf(item.getQuantity()),
                CurrencyUtil.formatPlain(item.getUnitPrice()),
                CurrencyUtil.formatPlain(item.getLineTotal())
            )).append("\n");

            // Show discount if applied
            if (item.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                sb.append(padLeft("Disc: -" + item.getDiscountPercent().toPlainString() +
                    "% = -" + CurrencyUtil.formatPlain(item.getDiscountAmount()), WIDTH)).append("\n");
            }
        }

        sb.append(repeat("-", WIDTH)).append("\n");

        // Totals
        sb.append(totalLine("Subtotal:", CurrencyUtil.formatPlain(bill.getSubtotal()))).append("\n");
        if (bill.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(totalLine("Discount:",
                "-" + CurrencyUtil.formatPlain(bill.getDiscountAmount()))).append("\n");
        }
        if (bill.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(totalLine("Tax:", CurrencyUtil.formatPlain(bill.getTaxAmount()))).append("\n");
        }
        sb.append(repeat("=", WIDTH)).append("\n");
        sb.append(totalLine("TOTAL:", CurrencyUtil.format(bill.getTotalAmount()))).append("\n");
        sb.append(repeat("=", WIDTH)).append("\n");

        // Payment info
        sb.append(totalLine("Paid (" + bill.getPaymentType().name() + "):",
            CurrencyUtil.formatPlain(bill.getPaidAmount()))).append("\n");
        if (bill.getChangeAmount().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(totalLine("Change:", CurrencyUtil.formatPlain(bill.getChangeAmount()))).append("\n");
        }
        sb.append(repeat("-", WIDTH)).append("\n");

        // Items count
        sb.append(center(bill.getItemCount() + " item(s)")).append("\n");
        sb.append("\n");

        // Footer
        if (footer != null && !footer.isBlank()) {
            sb.append(center(footer)).append("\n");
        }
        sb.append(center("Thank you!")).append("\n");
        sb.append("\n\n\n");  // feed for cutter

        return sb.toString();
    }

    // ── Formatting Helpers ────────────────────────────────────────────────────

    /** Centers text within WIDTH */
    public static String center(String text) {
        if (text == null) text = "";
        if (text.length() >= WIDTH) return text;
        int padding = (WIDTH - text.length()) / 2;
        return " ".repeat(padding) + text;
    }

    /** Left-pads text */
    public static String padLeft(String text, int width) {
        if (text.length() >= width) return text;
        return " ".repeat(width - text.length()) + text;
    }

    /** Right-pads text */
    public static String padRight(String text, int width) {
        if (text.length() >= width) return text.substring(0, width);
        return text + " ".repeat(width - text.length());
    }

    /** Two-column total line: "Label:          value" */
    public static String totalLine(String label, String value) {
        int space = WIDTH - label.length() - value.length();
        if (space < 1) space = 1;
        return label + " ".repeat(space) + value;
    }

    /** Four-column item line */
    public static String rowLine(String name, String qty, String price, String total) {
        // Widths: name=18, qty=4, price=10, total=10
        return padRight(name, 18) + padLeft(qty, 4) +
               padLeft(price, 10) + padLeft(total, 10);
    }

    /** Repeats a character N times */
    public static String repeat(String ch, int count) {
        return ch.repeat(count);
    }
}
