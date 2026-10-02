package az.innotex.sade;

import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Avtomatik nömrələr: müqavilə INX-YYYY-PS-NNN, akt AKT-YYYY-NNN (həmin ilin ən böyük nömrəsi + 1) */
@Component
public class Numbering {
    private static final Pattern CONTRACT_NO = Pattern.compile("INX-(\\d{4})-PS-(\\d+)");
    private static final Pattern ACT_NO = Pattern.compile("AKT-(\\d{4})-(\\d+)");

    private final R.Deals deals;

    public Numbering(R.Deals deals) { this.deals = deals; }

    private int max(int year, Pattern p, Function<E.Deal, String> get) {
        int max = 0;
        for (E.Deal x : deals.findAll()) {
            String v = get.apply(x);
            if (v == null) continue;
            Matcher m = p.matcher(v);
            if (m.matches() && Integer.parseInt(m.group(1)) == year) max = Math.max(max, Integer.parseInt(m.group(2)));
        }
        return max;
    }

    public String nextContractNo(int year) { return String.format("INX-%d-PS-%03d", year, max(year, CONTRACT_NO, x -> x.contractNo) + 1); }

    public String nextActNo(int year) { return String.format("AKT-%d-%03d", year, max(year, ACT_NO, x -> x.actNo) + 1); }
}
