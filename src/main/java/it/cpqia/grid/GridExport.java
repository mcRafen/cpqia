package it.cpqia.grid;

import it.cpqia.grid.GridModels.Col;
import it.cpqia.grid.GridModels.Data;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Esportazione di una griglia in Excel (.xlsx): colonne e righe nell'ordine visualizzato, numeri e date veri, totali con SUBTOTAL. */
final class GridExport {

    private GridExport() {
    }

    /** colonne richieste (nell'ordine indicato); se vuote, quelle non nascoste */
    static List<Col> pickColumns(Data data, List<String> columnKeys) {
        Map<String, Col> byKey = new HashMap<>();
        for (Col c : data.meta().columns()) {
            byKey.put(c.key(), c);
        }
        List<Col> cols = new ArrayList<>();
        for (String k : columnKeys == null ? List.<String>of() : columnKeys) {
            Col c = byKey.get(k);
            if (c != null) {
                cols.add(c);
            }
        }
        if (cols.isEmpty()) {
            for (Col c : data.meta().columns()) {
                if (!c.hidden()) {
                    cols.add(c);
                }
            }
        }
        return cols;
    }

    /** righe richieste (nell'ordine indicato); solo quelle visibili all'utente (data e' gia' filtrato per permessi) */
    static List<Map<String, Object>> pickRows(Data data, List<String> rowIds) {
        if (rowIds == null) {
            return new ArrayList<>(data.rows());
        }
        Map<String, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> r : data.rows()) {
            byId.put(String.valueOf(r.get("id")), r);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String id : rowIds) {
            Map<String, Object> r = byId.get(id);
            if (r != null) {
                rows.add(r);
            }
        }
        return rows;
    }

    static byte[] xlsx(Data data, List<String> columnKeys, List<String> rowIds) throws IOException {
        Map<String, Col> byKey = new HashMap<>();
        for (Col c : data.meta().columns()) {
            byKey.put(c.key(), c);
        }
        List<Col> cols = new ArrayList<>();
        for (String k : columnKeys) {
            Col c = byKey.get(k);
            if (c != null) {
                cols.add(c);
            }
        }
        if (cols.isEmpty()) {
            for (Col c : data.meta().columns()) {
                if (!c.hidden()) {
                    cols.add(c);
                }
            }
        }
        Map<String, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> r : data.rows()) {
            byId.put(String.valueOf(r.get("id")), r);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        if (rowIds == null) {
            rows.addAll(data.rows());
        } else {
            for (String id : rowIds) {              // solo righe che l'utente puo' vedere (data e' gia' filtrato per permessi)
                Map<String, Object> r = byId.get(id);
                if (r != null) {
                    rows.add(r);
                }
            }
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CreationHelper ch = wb.getCreationHelper();
            DataFormat df = wb.createDataFormat();
            Sheet sh = wb.createSheet(safeName(data.meta().title()));

            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            head.setBorderBottom(BorderStyle.THIN);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(df.getFormat("#,##0.00 \"€\""));
            CellStyle num = wb.createCellStyle();
            num.setDataFormat(df.getFormat("#,##0.00"));
            CellStyle coord = wb.createCellStyle();
            coord.setDataFormat(df.getFormat("0.000000"));
            CellStyle pct = wb.createCellStyle();
            pct.setDataFormat(df.getFormat("0.00\" %\""));
            CellStyle integer = wb.createCellStyle();
            integer.setDataFormat(df.getFormat("#,##0"));
            CellStyle date = wb.createCellStyle();
            date.setDataFormat(df.getFormat("dd/mm/yyyy"));
            CellStyle datetime = wb.createCellStyle();
            datetime.setDataFormat(df.getFormat("dd/mm/yyyy hh:mm"));
            CellStyle center = wb.createCellStyle();
            center.setAlignment(HorizontalAlignment.CENTER);

            Row h = sh.createRow(0);
            for (int i = 0; i < cols.size(); i++) {
                var cell = h.createCell(i);
                cell.setCellValue(cols.get(i).label());
                cell.setCellStyle(head);
                sh.setColumnWidth(i, Math.min(60, Math.max(12, cols.get(i).width() / 7)) * 256);
            }
            int rn = 1;
            for (Map<String, Object> r : rows) {
                Row row = sh.createRow(rn++);
                for (int i = 0; i < cols.size(); i++) {
                    Col c = cols.get(i);
                    Object v = r.get(c.key());
                    if (v == null || "".equals(v)) {
                        continue;
                    }
                    var cell = row.createCell(i);
                    switch (c.type()) {
                        case "money" -> {
                            cell.setCellValue(((Number) v).doubleValue());
                            cell.setCellStyle(money);
                        }
                        case "percent" -> {
                            cell.setCellValue(((Number) v).doubleValue());
                            cell.setCellStyle(pct);
                        }
                        case "int" -> {
                            cell.setCellValue(((Number) v).doubleValue());
                            cell.setCellStyle(integer);
                        }
                        case "coord" -> {
                            cell.setCellValue(((Number) v).doubleValue());
                            cell.setCellStyle(coord);
                        }
                        case "number" -> {
                            cell.setCellValue(((Number) v).doubleValue());
                            cell.setCellStyle(num);
                        }
                        case "date" -> {
                            cell.setCellValue(LocalDate.parse(v.toString().substring(0, 10)));
                            cell.setCellStyle(date);
                        }
                        case "datetime" -> {
                            cell.setCellValue(LocalDateTime.parse(v.toString().length() == 16 ? v + ":00" : v.toString()));
                            cell.setCellStyle(datetime);
                        }
                        case "bool" -> {
                            cell.setCellValue(Boolean.TRUE.equals(v) ? "Sì" : "No");
                            cell.setCellStyle(center);
                        }
                        case "enum" -> {
                            String label = v.toString();
                            if (c.options() != null) {
                                for (var o : c.options()) {
                                    if (o.value().equals(label)) {
                                        label = o.label();
                                    }
                                }
                            }
                            cell.setCellValue(label);
                        }
                        default -> cell.setCellValue(v.toString());
                    }
                }
            }
            // riga dei totali: SUBTOTAL(109) rispetta i filtri applicati poi in Excel
            boolean anyNum = cols.stream().anyMatch(c -> List.of("money", "number", "percent", "int").contains(c.type()) && !"percent".equals(c.type()));
            if (anyNum && !rows.isEmpty()) {
                Row t = sh.createRow(rn);
                for (int i = 0; i < cols.size(); i++) {
                    Col c = cols.get(i);
                    var cell = t.createCell(i);
                    cell.setCellStyle(head);
                    if (i == 0) {
                        cell.setCellValue("Totale");
                    }
                    if (List.of("money", "number", "int").contains(c.type())) {
                        String L = org.apache.poi.ss.util.CellReference.convertNumToColString(i);
                        cell.setCellFormula("SUBTOTAL(109," + L + "2:" + L + rn + ")");
                        cell.setCellStyle(c.type().equals("money") ? money : c.type().equals("int") ? integer : num);
                        Font f = wb.createFont();
                        f.setBold(true);
                        cell.getCellStyle().setFont(f);
                    }
                }
            }
            sh.createFreezePane(0, 1);
            if (!rows.isEmpty()) {
                sh.setAutoFilter(new CellRangeAddress(0, rows.size(), 0, cols.size() - 1));
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static String safeName(String s) {
        String n = s == null ? "Dati" : s.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        return n.isEmpty() ? "Dati" : n.substring(0, Math.min(31, n.length()));
    }
}
