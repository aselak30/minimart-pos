package com.minimartpos.util;

import java.math.BigDecimal;

/**
 * Input validation helpers used across forms.
 */
public final class ValidationUtil {

    private ValidationUtil() {}

    public static boolean isNullOrBlank(String s) {
        return s == null || s.isBlank();
    }

    public static boolean isPositive(BigDecimal v) {
        return v != null && v.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isNonNegative(BigDecimal v) {
        return v != null && v.compareTo(BigDecimal.ZERO) >= 0;
    }

    public static boolean isValidBarcode(String barcode) {
        return barcode != null && barcode.matches("[A-Za-z0-9\\-]{3,20}");
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && phone.matches("[0-9+\\-\\s]{7,20}");
    }

    /** Returns trimmed text or null if blank */
    public static String trimOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }
}
