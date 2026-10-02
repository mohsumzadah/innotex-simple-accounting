package az.innotex.sade;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

/** POI üçün kiçik köməkçi: bir vərəq, sətir-sətir yazma. Pul mətnləri ədədə çevrilir. */
public class Xlsx {
    private final Workbook wb = new XSSFWorkbook();
    private Sheet sh;
    private final CellStyle boldStyle, moneyStyle, boldMoney, headStyle;
    private final CellStyle dateStyle, dateTimeStyle;
    private int r = 0;

    public Xlsx(String sheetName) {
        sh = wb.createSheet(sheetName.replaceAll("[\\\\/?*\\[\\]:]", "-"));
        Font bold = wb.createFont();
        bold.setBold(true);
        boldStyle = wb.createCellStyle(); boldStyle.setFont(bold);
        DataFormat df = wb.createDataFormat();
        dateStyle = wb.createCellStyle(); dateStyle.setDataFormat(df.getFormat("dd.mm.yyyy"));
        dateTimeStyle = wb.createCellStyle(); dateTimeStyle.setDataFormat(df.getFormat("dd.mm.yyyy hh:mm"));
        moneyStyle = wb.createCellStyle(); moneyStyle.setDataFormat(df.getFormat("#,##0.00"));
        boldMoney = wb.createCellStyle(); boldMoney.setFont(bold); boldMoney.setDataFormat(df.getFormat("#,##0.00"));
        headStyle = wb.createCellStyle(); headStyle.setFont(bold);
        headStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headStyle.setWrapText(true);
    }

    /** YYYY-MM -> mm.yyyy */
    public static String my(String ym) { return ym == null || !ym.matches("\\d{4}-\\d{2}") ? ym : ym.substring(5) + "." + ym.substring(0, 4); }
    /** YYYY-MM-DD -> dd.mm.yyyy */
    public static String dmy(Object d) { return d == null ? null : java.time.LocalDate.parse(d.toString()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")); }

    private void write(CellStyle textStyle, CellStyle numStyle, Object... vals) {
        Row row = sh.createRow(r++);
        for (int i = 0; i < vals.length; i++) {
            Object v = vals[i];
            Cell c = row.createCell(i);
            if (v == null) continue;
            if (v instanceof Integer n) { c.setCellValue(n); if (textStyle != null) c.setCellStyle(textStyle); continue; }
            if (v instanceof java.time.LocalDate d) { c.setCellValue(d); c.setCellStyle(dateStyle); continue; }
            if (v instanceof java.time.LocalDateTime d) { c.setCellValue(d); c.setCellStyle(dateTimeStyle); continue; }
            if (v instanceof java.time.YearMonth ym) { c.setCellValue(my(ym.toString())); if (textStyle != null) c.setCellStyle(textStyle); continue; }
            String s = v.toString();
            if (v instanceof BigDecimal b) { c.setCellValue(b.doubleValue()); c.setCellStyle(numStyle); continue; }
            // "123.45" kimi pul mətnləri ədəd kimi yazılır
            if (s.matches("-?\\d+\\.\\d{2}")) { c.setCellValue(Double.parseDouble(s)); c.setCellStyle(numStyle); continue; }
            c.setCellValue(s);
            if (textStyle != null) c.setCellStyle(textStyle);
        }
    }

    /** Yeni vərəq açır, sonrakı yazılar ona gedir */
    public void sheet(String name) { sh = wb.createSheet(name); r = 0; }
    public void title(String t) { write(boldStyle, boldMoney, t); }
    public void header(Object... h) { write(headStyle, headStyle, h); }
    public void row(Object... v) { write(null, moneyStyle, v); }
    public void bold(Object... v) { write(boldStyle, boldMoney, v); }
    public void blank() { r++; }

    public byte[] bytes() {
        for (Sheet sh : wb) {
        int cols = 0;
        for (Row row : sh) cols = Math.max(cols, row.getLastCellNum());
        for (int i = 0; i < cols; i++) { sh.autoSizeColumn(i); if (sh.getColumnWidth(i) > 12000) sh.setColumnWidth(i, 12000); }
        }
        try (wb; ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            wb.write(o);
            return o.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Excel yaradılmadı", e);
        }
    }
}
