package com.nureal.ide.core.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import javax.swing.table.TableModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Exporta um ou mais TableModel para um arquivo .xlsx.
 * Cada TableSheet vira uma aba (planilha) no mesmo arquivo, como no PL/SQL Developer.
 *
 * Usa SXSSF (escrita em streaming) para manter o uso de memoria baixo mesmo com
 * muitos registros.
 */
public final class ExcelExporter {

    /** Uma aba a ser escrita: nome + dados. */
    public record TableSheet(String name, TableModel model) {
    }

    private ExcelExporter() {
    }

    @SuppressWarnings("deprecation")
	public static void export(List<TableSheet> sheets, File file) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            CellStyle headerStyle = headerStyle(wb);
            Set<String> usedNames = new HashSet<>();

            if (sheets.isEmpty()) {
                wb.createSheet("Vazio");
            }
            for (TableSheet ts : sheets) {
                Sheet sheet = wb.createSheet(uniqueName(ts.name(), usedNames));
                writeModel(sheet, ts.model(), headerStyle);
            }

            try (FileOutputStream out = new FileOutputStream(file)) {
                wb.write(out);
            }
            wb.dispose();
        }
    }

    private static void writeModel(Sheet sheet, TableModel model, CellStyle headerStyle) {
        int cols = model.getColumnCount();

        Row header = sheet.createRow(0);
        for (int c = 0; c < cols; c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(model.getColumnName(c));
            cell.setCellStyle(headerStyle);
        }

        int rows = model.getRowCount();
        for (int r = 0; r < rows; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < cols; c++) {
                Object value = model.getValueAt(r, c);
                Cell cell = row.createCell(c);
                if (value == null) {
                    continue; // celula vazia
                }
                if (value instanceof Number number) {
                    cell.setCellValue(number.doubleValue());
                } else if (value instanceof Boolean bool) {
                    cell.setCellValue(bool);
                } else {
                    cell.setCellValue(value.toString());
                }
            }
        }
    }

    private static CellStyle headerStyle(SXSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    /** Nome de aba valido (sem caracteres proibidos, <= 31 chars) e unico no arquivo. */
    private static String uniqueName(String raw, Set<String> used) {
        String safe = WorkbookUtil.createSafeSheetName(raw == null ? "Resultado" : raw);
        int n = 1;
        while (used.contains(safe.toLowerCase(Locale.ROOT))) {
            String suffix = "_" + (++n);
            String base = WorkbookUtil.createSafeSheetName(raw == null ? "Resultado" : raw);
            if (base.length() + suffix.length() > 31) {
                base = base.substring(0, 31 - suffix.length());
            }
            safe = base + suffix;
        }
        used.add(safe.toLowerCase(Locale.ROOT));
        return safe;
    }
}
