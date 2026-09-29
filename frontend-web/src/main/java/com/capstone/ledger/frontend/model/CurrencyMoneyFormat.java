package com.capstone.ledger.frontend.model;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class CurrencyMoneyFormat {

    private CurrencyMoneyFormat() {
    }

    public static String symbol(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) return "¤ ";
        return switch (currencyCode.trim().toUpperCase(Locale.ROOT)) {
            case "PHP" -> "₱";
            case "USD" -> "$";
            case "EUR" -> "€";
            case "GBP" -> "£";
            case "SGD" -> "S$";
            case "JPY" -> "¥";
            default -> currencyCode.trim().toUpperCase(Locale.ROOT) + " ";
        };
    }

    public static String format(BigDecimal amount, String currencyCode) {
        if (amount == null) return "—";
        DecimalFormat formatter = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return symbol(currencyCode) + formatter.format(amount);
    }
}
