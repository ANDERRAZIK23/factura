package ma.facture.invoicing.invoice.pdf;

import org.springframework.stereotype.Service;

import java.math.*;
import java.util.*;

@Service
public class FrenchAmountInWordsService {
    private static final String[] SMALL = {"zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept",
            "huit", "neuf", "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept",
            "dix-huit", "dix-neuf"};
    private static final String[] TENS = {"", "", "vingt", "trente", "quarante", "cinquante", "soixante"};
    private static final String[] SCALES = {"", "mille", "million", "milliard", "billion", "billiard"};
    private static final BigInteger THOUSAND = BigInteger.valueOf(1_000);

    public String convert(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("Amount is required");
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() < 0) throw new IllegalArgumentException("Amount must not be negative");
        BigInteger whole = normalized.toBigInteger();
        int cents = normalized.remainder(BigDecimal.ONE).movePointRight(2).intValueExact();
        String result = integerWords(whole) + (whole.equals(BigInteger.ONE) ? " dirham" : " dirhams");
        if (cents > 0) result += " et " + underOneHundred(cents, true) + (cents == 1 ? " centime" : " centimes");
        return Character.toUpperCase(result.charAt(0)) + result.substring(1);
    }

    String integerWords(BigInteger value) {
        if (value.signum() == 0) return SMALL[0];
        List<String> groups = new ArrayList<>();
        int scale = 0;
        while (value.signum() > 0) {
            if (scale >= SCALES.length) throw new IllegalArgumentException("Amount exceeds the supported French wording range");
            BigInteger[] division = value.divideAndRemainder(THOUSAND);
            int group = division[1].intValueExact();
            if (group > 0) groups.add(groupWords(group, scale));
            value = division[0];
            scale++;
        }
        Collections.reverse(groups);
        return String.join(" ", groups);
    }

    private static String groupWords(int group, int scale) {
        if (scale == 1 && group == 1) return "mille";
        String number = underOneThousand(group, scale == 0);
        if (scale == 0) return number;
        String label = SCALES[scale];
        if (scale > 1 && group > 1) label += "s";
        return number + " " + label;
    }

    private static String underOneThousand(int value, boolean terminal) {
        if (value < 100) return underOneHundred(value, terminal);
        int hundreds = value / 100;
        int remainder = value % 100;
        String prefix = hundreds == 1 ? "cent" : SMALL[hundreds] + " cent";
        if (hundreds > 1 && remainder == 0 && terminal) prefix += "s";
        return remainder == 0 ? prefix : prefix + " " + underOneHundred(remainder, terminal);
    }

    private static String underOneHundred(int value, boolean terminal) {
        if (value < 20) return SMALL[value];
        if (value < 70) {
            int tens = value / 10;
            int units = value % 10;
            if (units == 0) return TENS[tens];
            return TENS[tens] + (units == 1 ? " et " : "-") + SMALL[units];
        }
        if (value < 80) {
            int remainder = value - 60;
            return "soixante" + (remainder == 11 ? " et " : "-") + SMALL[remainder];
        }
        int remainder = value - 80;
        if (remainder == 0) return terminal ? "quatre-vingts" : "quatre-vingt";
        return "quatre-vingt-" + SMALL[remainder];
    }
}
