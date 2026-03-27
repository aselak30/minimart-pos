package com.minimartpos.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Date and time formatting utilities used across the application.
 */
public final class DateUtil {

    private DateUtil() {}

    public static final DateTimeFormatter DISPLAY_DATE     = DateTimeFormatter.ofPattern("dd MMM yyyy");
    public static final DateTimeFormatter DISPLAY_DATETIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    public static final DateTimeFormatter DISPLAY_TIME     = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final DateTimeFormatter CLOCK_TIME       = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter TOPBAR_DATE      = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy");
    public static final DateTimeFormatter ISO_DATE         = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String formatDate(LocalDate date) {
        return date != null ? date.format(DISPLAY_DATE) : "—";
    }

    public static String formatDateTime(LocalDateTime dt) {
        return dt != null ? dt.format(DISPLAY_DATETIME) : "—";
    }

    public static String formatTime(LocalDateTime dt) {
        return dt != null ? dt.format(DISPLAY_TIME) : "--:--:--";
    }

    /** Returns "today", "yesterday", or formatted date */
    public static String relativeDate(LocalDate date) {
        if (date == null) return "—";
        LocalDate today = LocalDate.now();
        if (date.equals(today))             return "Today";
        if (date.equals(today.minusDays(1)))return "Yesterday";
        return date.format(DISPLAY_DATE);
    }

    /** Returns "+X" or "-X" delta string with colour hint prefix */
    public static String deltaString(long value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }
}
