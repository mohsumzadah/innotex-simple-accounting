package az.innotex.sade;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pul: BigDecimal, 2 rəqəm, HALF_UP. JSON-da string. */
public final class Money {
    private Money() {}

    public static final BigDecimal ZERO = new BigDecimal("0.00");
    public static final BigDecimal HUNDRED = new BigDecimal("100");

    public static BigDecimal r2(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** JSON üçün: həmişə 2 rəqəm ("100.00"), null olarsa null */
    public static String s(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Faiz dərəcəsi kimi göstərmək üçün (məs. "20", "0.5") */
    public static String rate(BigDecimal v) {
        if (v == null) return null;
        BigDecimal x = v.stripTrailingZeros();
        return (x.scale() < 2 ? x.setScale(2) : x).toPlainString();
    }

    public static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    public static BigDecimal sum(Iterable<BigDecimal> it) {
        BigDecimal t = BigDecimal.ZERO;
        for (BigDecimal b : it) t = t.add(nz(b));
        return t;
    }
}
