package com.minimartpos.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * Currency and number formatting utilities.
 */
public final class CurrencyUtil {

    private CurrencyUtil() {}

    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,##0.00");

    /** Formats a BigDecimal as "Rs. 1,234.50" */
    public static String format(BigDecimal amount) {
        if (amount == null) return "Rs. 0.00";
        return "Rs. " + MONEY_FMT.format(amount);
    }

    /** Formats without currency symbol: "1,234.50" */
    public static String formatPlain(BigDecimal amount) {
        if (amount == null) return "0.00";
        return MONEY_FMT.format(amount);
    }

    /** Parses a string to BigDecimal; returns ZERO on failure. */
    public static BigDecimal parse(String text) {
        if (text == null || text.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(text.replaceAll("[^0-9.]", ""))
                       .setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /** Rounds to 2 decimal places. */
    public static BigDecimal round(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
