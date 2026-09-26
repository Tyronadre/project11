package de.tyro.project11.costs;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class Money {
    private Money() {}
    public static long cents(String input) {
        String value = input == null ? "" : input.strip();
        if (!value.matches("(?:0|[1-9][0-9]{0,6})(?:[.,][0-9]{1,2})?")) throw invalid();
        long cents = new BigDecimal(value.replace(',', '.')).movePointRight(2).longValueExact();
        if (cents <= 0) throw invalid();
        return cents;
    }
    public static Long optionalCents(String input) { return input == null || input.isBlank() ? null : cents(input); }
    public static String display(long cents) { return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(BigDecimal.valueOf(cents, 2)); }
    public static String input(long cents) { return BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ','); }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Bitte einen Betrag von 0,01 bis 9.999.999,99 € mit höchstens zwei Nachkommastellen und ohne Tausendertrennzeichen eingeben.");
    }
}
