package com.builddash.backend.support;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

/**
 * Test-only XLSX builder. Full XSSFWorkbook is fine HERE: production code must
 * stream untrusted workbooks (PoWorkbookParserAdapter), but tests construct
 * small trusted fixtures in memory.
 *
 * Cell typing: String -> text cell, Number -> numeric cell, LocalDate -> date
 * cell, Formula -> formula cell, null -> cell skipped.
 */
public final class PoTestWorkbooks {

    private PoTestWorkbooks() {
    }

    public static byte[] workbook(Object[][] rows) {
        return workbook("PO", rows);
    }

    public static byte[] workbook(String sheetName, Object[][] rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet(sheetName);
            // Date cells need an explicit date style: POI's setCellValue(LocalDate)
            // writes a bare numeric serial, which is (correctly) indistinguishable
            // from a plain number without the style.
            org.apache.poi.ss.usermodel.CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.createDataFormat().getFormat("m/d/yy"));
            for (int r = 0; r < rows.length; r++) {
                XSSFRow row = sheet.createRow(r);
                Object[] cells = rows[r];
                for (int c = 0; c < cells.length; c++) {
                    setCell(row.createCell(c), cells[c], dateStyle);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("test workbook build failed", e);
        }
    }

    /** Two sheets: the parser must read only the first. */
    public static byte[] workbookWithExtraSheet(Object[][] first, Object[][] second) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill(wb.createSheet("PO"), first);
            fill(wb.createSheet("IgnoreMe"), second);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("test workbook build failed", e);
        }
    }

    private static void fill(XSSFSheet sheet, Object[][] rows) {
        for (int r = 0; r < rows.length; r++) {
            XSSFRow row = sheet.createRow(r);
            for (int c = 0; c < rows[r].length; c++) {
                setCell(row.createCell(c), rows[r][c], null);
            }
        }
    }

    private static void setCell(XSSFCell cell, Object value, org.apache.poi.ss.usermodel.CellStyle dateStyle) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof String text) {
            cell.setCellValue(text);
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(date);
            if (dateStyle != null) {
                cell.setCellStyle(dateStyle);
            }
        } else if (value instanceof Formula formula) {
            cell.setCellFormula(formula.expression());
        } else {
            throw new IllegalArgumentException("Unsupported cell type: " + value.getClass());
        }
    }

    public record Formula(String expression) {
        public static Formula of(String expression) {
            return new Formula(expression);
        }
    }
}
