package az.innotex.sade;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Hesablama metodu: xərc sənəd tarixinə, maaş aid olduğu aya aiddir. 2031 (xərc) və 2020 (maaş xəbərdarlıqları) ayrıca dövrlər. */
class AccrualTest extends ApiTest {

    @Test
    void xercSenedTarixiIleHesabaDaxilOlur() throws Exception {
        long acc = newAccount("Hesablama bankı", "BANK", "0.00");
        // sənəd Q3-də, ödəniş Q4-də
        String e = POST("/api/expenses", "{\"date\":\"2031-10-05\",\"docDate\":\"2031-09-04\",\"category\":\"Abunəlik\",\"amount\":\"50.00\",\"accountId\":" + acc + "}");
        assertThat((String) read(e, "$.docDate")).isEqualTo("2031-09-04");
        assertThat((String) read(e, "$.date")).isEqualTo("2031-10-05");
        String q3 = GET("/api/reports/quarter?year=2031&q=3");
        String q4 = GET("/api/reports/quarter?year=2031&q=4");
        assertThat((String) read(q3, "$.expenses")).isEqualTo("50.00");
        assertThat((String) read(q4, "$.expenses")).isEqualTo("0.00");
        assertThat((String) read(q3, "$.method")).isEqualTo("ACCRUAL");
        // siyahı sənəd tarixi ilə süzülür
        assertThat((List<?>) read(GET("/api/expenses?from=2031-09-01&to=2031-09-30"), "$")).hasSize(1);
        assertThat((List<?>) read(GET("/api/expenses?from=2031-10-01&to=2031-10-31"), "$")).isEmpty();
        // docDate verilməsə ödəniş tarixi götürülür
        String d = POST("/api/expenses", "{\"date\":\"2031-02-03\",\"category\":\"Digər\",\"amount\":\"5.00\",\"accountId\":" + acc + "}");
        assertThat((String) read(d, "$.docDate")).isEqualTo("2031-02-03");
        // hesab hərəkəti ödəniş tarixində qalır
        assertThat((String) read(GET("/api/movements?from=2031-10-01&to=2031-10-31"), "$[0].date")).isEqualTo("2031-10-05");
        assertThat((String) read(GET("/api/settings"), "$.taxMethod")).isEqualTo("ACCRUAL");
    }

    @Test
    void maasVeYoxlayinXeberdarliqlari() throws Exception {
        long emp = id(POST("/api/employees", "{\"name\":\"Accrual-1\",\"position\":\"Test\",\"workplace\":\"MAIN\",\"gross\":\"1000.00\",\"active\":true}"));
        long acc = newAccount("Xəbərdarlıq bankı", "BANK", "0.00");
        POST("/api/expenses", "{\"date\":\"2026-01-10\",\"category\":\"Digər\",\"amount\":\"5.00\",\"accountId\":" + acc + ",\"note\":\"YOXLAYIN: məbləğ\"}");
        long run = id(POST("/api/payroll-runs", "{\"month\":\"2026-03\"}"));   // DRAFT
        try {
            List<String> w = read(GET("/api/reports/quarter?year=2026&q=1"), "$.warnings");
            assertThat(w).anyMatch(s -> s.startsWith("03.2026 maaş cədvəli yekunlaşdırılmayıb — hesabata daxil deyil"));
            assertThat(w).anyMatch(s -> s.startsWith("02.2026 maaş cədvəli yekunlaşdırılmayıb"));
            assertThat(w).anyMatch(s -> s.contains("YOXLAYIN"));
        } finally {
            DELETE("/api/payroll-runs/" + run);
            PUT("/api/employees/" + emp, "{\"name\":\"Accrual-1\",\"position\":\"Test\",\"workplace\":\"MAIN\",\"gross\":\"1000.00\",\"active\":false}");
        }
    }
}
