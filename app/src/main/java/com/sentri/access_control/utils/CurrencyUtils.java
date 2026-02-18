package com.sentri.access_control.utils;

import android.util.Log;

import java.text.DecimalFormat;
import java.util.Map;

/**
 * Currency formatting utility for Indian Rupees.
 */
public final class CurrencyUtils {
    private static final String TAG = "CurrencyUtils";
    private static final DecimalFormat RUPEE_FORMAT = new DecimalFormat("#,##0");

    private CurrencyUtils() {} // Prevent instantiation

    /**
     * Formats a double value as Indian Rupees: "₹ X,XXX"
     */
    public static String formatRupees(double value) {
        long rounded = Math.round(value);
        if (rounded == 0) return "₹ 0";
        return "₹ " + RUPEE_FORMAT.format(rounded);
    }

    /**
     * Formats an integer value as Indian Rupees: "₹ X,XXX"
     */
    public static String formatRupees(long value) {
        if (value == 0) return "₹ 0";
        return "₹ " + RUPEE_FORMAT.format(value);
    }

    /**
     * Parses an amount from various types (String, Number, Map).
     * Handles currency symbols, commas, and whitespace.
     */
    public static double parseAmount(Object raw) {
        try {
            if (raw == null) return 0.0;
            if (raw instanceof Number) return ((Number) raw).doubleValue();
            if (raw instanceof String) {
                String s = ((String) raw).trim();
                // Remove currency symbols and non-digit except . and -
                s = s.replaceAll("[^0-9.\\-]", "");
                if (s.isEmpty()) return 0.0;
                return Double.parseDouble(s);
            }
            // If map with nested fields, try to find numeric value
            if (raw instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) raw;
                Object v = m.get("value");
                if (v instanceof Number) return ((Number) v).doubleValue();
                if (v instanceof String) {
                    String s = ((String) v).replaceAll("[^0-9.\\-]", "");
                    if (s.isEmpty()) return 0.0;
                    return Double.parseDouble(s);
                }
            }
        } catch (Exception ex) {
            Log.w(TAG, "parseAmount error: " + ex.getMessage());
        }
        return 0.0;
    }
}
