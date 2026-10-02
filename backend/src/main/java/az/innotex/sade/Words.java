package az.innotex.sade;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Məbləği Azərbaycan dilində sözlə yazır: "yüz manat 50 qəpik" */
public final class Words {
    private Words() {}

    private static final String[] ONES = {"", "bir", "iki", "üç", "dörd", "beş", "altı", "yeddi", "səkkiz", "doqquz"};
    private static final String[] TENS = {"", "on", "iyirmi", "otuz", "qırx", "əlli", "altmış", "yetmiş", "səksən", "doxsan"};
    private static final String[] SCALES = {"", "min", "milyon", "milyard", "trilyon"};

    /** 1..999 */
    private static String below1000(int n) {
        StringBuilder sb = new StringBuilder();
        int h = n / 100, t = (n % 100) / 10, o = n % 10;
        if (h > 0) sb.append(h == 1 ? "yüz" : ONES[h] + " yüz");
        if (t > 0) sb.append(sb.length() > 0 ? " " : "").append(TENS[t]);
        if (o > 0) sb.append(sb.length() > 0 ? " " : "").append(ONES[o]);
        return sb.toString();
    }

    /** Tam ədədin sözlə yazılışı ("min" 1000 üçün "bir" yazılmır) */
    public static String number(long n) {
        if (n == 0) return "sıfır";
        StringBuilder sb = new StringBuilder();
        int[] groups = new int[SCALES.length];
        for (int i = 0; i < groups.length; i++) { groups[i] = (int) (n % 1000); n /= 1000; }
        for (int i = groups.length - 1; i >= 0; i--) {
            if (groups[i] == 0) continue;
            String part = (i == 1 && groups[i] == 1) ? "" : below1000(groups[i]);
            if (sb.length() > 0) sb.append(' ');
            sb.append(part);
            if (i > 0) sb.append(part.isEmpty() ? "" : " ").append(SCALES[i]);
        }
        return sb.toString();
    }

    /** Məbləğ: manat sözlə, qəpik iki rəqəmlə, məs. "yüz manat 00 qəpik" */
    public static String amount(BigDecimal v) {
        BigDecimal a = Money.r2(v).abs();
        long manat = a.setScale(0, RoundingMode.DOWN).longValueExact();
        int qepik = a.subtract(new BigDecimal(manat)).movePointRight(2).intValueExact();
        return number(manat) + " manat " + String.format("%02d", qepik) + " qəpik";
    }
}
