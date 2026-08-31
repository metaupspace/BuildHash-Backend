package com.builddash.backend.infra.excel;

import com.builddash.backend.domain.exception.PoImportValidationException;
import com.builddash.backend.domain.port.PoWorkbookParser;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Streaming XLSX parser (POI event/SAX model). No XSSFWorkbook is ever built
 * and no MultipartFile.getBytes() is used: the workbook is consumed as a stream
 * bounded by the 2MB servlet/app limit, one sheet row at a time. Input larger
 * than the limit never reaches this class in production, and the caller still
 * enforces the size cap independently.
 *
 * Security posture: POI's ZipSecureFile defaults (zip-bomb inflate ratio and
 * entry caps) are left untouched; XMLHelper.newXMLReader() configures secure
 * XML processing (no external entities). Cell typing is strict — formulas,
 * dates, booleans, errors and coerced strings fail the row, never the file.
 */
@Component
public class PoWorkbookParserAdapter implements PoWorkbookParser {

    static final int MAX_ROWS = 5000;

    private static final byte[] ZIP_LOCAL_FILE_HEADER = {0x50, 0x4B, 0x03, 0x04};
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Override
    public List<PoRawRow> parse(InputStream in) {
        java.io.PushbackInputStream pushback = new java.io.PushbackInputStream(in, 4);
        byte[] signature = new byte[4];
        int read;
        try {
            read = pushback.read(signature);
        } catch (IOException e) {
            throw new PoImportValidationException("INVALID_WORKBOOK", "Workbook could not be read");
        }
        if (read != 4) {
            throw new PoImportValidationException("INVALID_WORKBOOK", "File too small to be a workbook");
        }
        if (!Arrays.equals(signature, ZIP_LOCAL_FILE_HEADER)) {
            // Every OOXML package is a ZIP; anything else is not XLSX regardless
            // of the declared content type.
            throw new PoImportValidationException("INVALID_CONTENT_TYPE",
                    "File is not an XLSX workbook (missing OOXML/ZIP signature); expected " + XLSX_MIME);
        }
        try {
            pushback.unread(signature);
        } catch (IOException e) {
            throw new PoImportValidationException("INVALID_WORKBOOK", "Workbook could not be read");
        }

        try (OPCPackage pkg = OPCPackage.open(pushback)) {
            ReadOnlySharedStringsTable sharedStrings = new ReadOnlySharedStringsTable(pkg);
            XSSFReader xssfReader = new XSSFReader(pkg);
            StylesTable styles = xssfReader.getStylesTable();
            Iterator<InputStream> sheets = xssfReader.getSheetsData();
            if (!sheets.hasNext()) {
                throw new PoImportValidationException("INVALID_WORKBOOK", "Workbook has no sheets");
            }
            // Locked: first sheet only, extra sheets ignored.
            try (InputStream sheet = sheets.next()) {
                SheetHandler handler = new SheetHandler(sharedStrings, styles);
                XMLReader xmlReader = XMLHelper.newXMLReader();
                xmlReader.setContentHandler(handler);
                xmlReader.parse(new InputSource(sheet));
                return handler.rows();
            }
        } catch (PoImportValidationException e) {
            throw e;
        } catch (Exception e) {
            // Malformed ZIP/XML, unsupported package structure, POI parse guards.
            throw new PoImportValidationException("INVALID_WORKBOOK",
                    "Workbook is malformed or not a valid XLSX file: " + e.getClass().getSimpleName());
        }
    }

    /**
     * SAX handler over the first sheet's XML. Tracks cell type/format attributes
     * directly (t, s, f) so typing decisions are exact — formatted-value helpers
     * would silently coerce exactly the values the locked rules reject.
     */
    private static final class SheetHandler extends DefaultHandler {

        private static final String CELL_TYPE_INVALID = "CELL_TYPE_INVALID";
        private static final String SKU_REQUIRED = "SKU_REQUIRED";
        private static final String QTY_REQUIRED = "QTY_REQUIRED";

        private final ReadOnlySharedStringsTable sharedStrings;
        private final StylesTable styles;

        private final List<PoRawRow> rows = new ArrayList<>();
        private final List<String> headerCells = new ArrayList<>();
        private boolean headerSeen;

        // current row state
        private int currentRowNumber;
        private boolean rowHasContent;
        private String pendingSku;
        private Long pendingQty;
        private String pendingError;

        // current cell state
        private String cellRef;
        private String cellType;
        private String cellStyle;
        private boolean cellFormula;
        private boolean inlineString;
        private boolean cellHasValue;
        private final StringBuilder buffer = new StringBuilder();
        private boolean collecting;

        SheetHandler(ReadOnlySharedStringsTable sharedStrings, StylesTable styles) {
            this.sharedStrings = sharedStrings;
            this.styles = styles;
        }

        List<PoRawRow> rows() {
            return rows;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            switch (tag(localName, qName)) {
                case "row" -> {
                    currentRowNumber = attributes.getValue("r") != null
                            ? Integer.parseInt(attributes.getValue("r"))
                            : currentRowNumber + 1;
                    rowHasContent = false;
                    pendingSku = null;
                    pendingQty = null;
                    pendingError = null;
                }
                case "c" -> {
                    cellRef = attributes.getValue("r");
                    cellType = attributes.getValue("t");
                    cellStyle = attributes.getValue("s");
                    cellFormula = false;
                    inlineString = false;
                    cellHasValue = false;
                    buffer.setLength(0);
                    collecting = false;
                }
                case "f" -> cellFormula = true;
                case "is" -> inlineString = true;
                case "v", "t" -> {
                    collecting = true;
                }
                default -> { /* structure elements ignored */ }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (collecting) {
                buffer.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            switch (tag(localName, qName)) {
                case "v", "t" -> {
                    collecting = false;
                    if (!buffer.isEmpty()) {
                        cellHasValue = true;
                    }
                }
                case "is" -> inlineString = false;
                case "c" -> finishCell();
                case "row" -> finishRow();
                default -> { /* ignored */ }
            }
        }

        private void finishCell() {
            String raw = buffer.toString();
            boolean hasValue = cellHasValue;
            if (cellRef == null) {
                return; // no reference: cannot map to a column — ignore
            }
            int col = new CellReference(cellRef).getCol();
            String kind = cellKind(raw);

            if (!headerSeen) {
                // sparse by column index: a header starting at B does not shift names
                while (headerCells.size() <= col) {
                    headerCells.add(null);
                }
                headerCells.set(col, hasValue ? resolvedText(raw) : null);
                rowHasContent |= hasValue;
                return;
            }

            if (col == 0) {
                rowHasContent |= hasValue || "formula".equals(kind);
                if ("formula".equals(kind)) {
                    // formula with no cached value still has no v — fail closed, not REQUIRED
                    pendingError = CELL_TYPE_INVALID;
                } else if (!hasValue) {
                    if (pendingError == null) pendingError = SKU_REQUIRED;
                } else if (!"text".equals(kind)) {
                    pendingError = CELL_TYPE_INVALID; // numeric/bool/error/date sku
                    pendingSku = null;
                } else {
                    pendingSku = resolvedText(raw).trim();
                    if (pendingSku.isEmpty() && pendingError == null) {
                        pendingError = SKU_REQUIRED;
                    }
                }
            } else if (col == 1) {
                rowHasContent |= hasValue || "formula".equals(kind);
                if ("formula".equals(kind)) {
                    if (pendingError == null) pendingError = CELL_TYPE_INVALID;
                } else if (!hasValue) {
                    if (pendingError == null) pendingError = QTY_REQUIRED;
                } else if (!"numeric".equals(kind)) {
                    if (pendingError == null) pendingError = CELL_TYPE_INVALID; // string/bool/date qty
                } else {
                    Long qty = parseWholeNumber(raw);
                    if (qty == null) {
                        if (pendingError == null) pendingError = CELL_TYPE_INVALID; // decimal or overflow
                    } else {
                        pendingQty = qty;
                    }
                }
            } else {
                rowHasContent |= hasValue; // extra columns: content counts toward blankness only
            }
        }

        /**
         * Exact cell kind from the XML attributes: formula beats everything
         * (fail closed — cached results are never trusted); shared/inline text
         * is "text"; untyped numeric is "numeric" unless styled as a date.
         */
        private String cellKind(String raw) {
            if (cellFormula) {
                return "formula";
            }
            if (cellType == null || cellType.isBlank() || "n".equals(cellType)) {
                // "n" is the explicit numeric type writers emit; untyped cells are numeric too
                return isDateStyled() ? "date" : "numeric";
            }
            return switch (cellType) {
                case "s", "inlineStr", "str" -> "text";
                default -> cellType; // b (bool), e (error)
            };
        }

        private boolean isDateStyled() {
            if (cellStyle == null) {
                return false;
            }
            try {
                XSSFCellStyle style = styles.getStyleAt(Integer.parseInt(cellStyle));
                return style != null && DateUtil.isADateFormat(
                        style.getDataFormat(), style.getDataFormatString());
            } catch (NumberFormatException e) {
                return false;
            }
        }

        /** Resolves the cell's textual value (shared string index or raw text). */
        private String resolvedText(String raw) {
            if ("s".equals(cellType)) {
                try {
                    return sharedStrings.getItemAt(Integer.parseInt(raw.trim())).getString();
                } catch (NumberFormatException e) {
                    return null; // malformed shared-string index — treated as no text
                }
            }
            return raw;
        }

        /** Whole-number numeric value, or null when decimal/overflow/NaN. */
        private Long parseWholeNumber(String raw) {
            try {
                BigDecimal value = new BigDecimal(raw.trim());
                if (value.scale() > 0 && value.stripTrailingZeros().scale() > 0) {
                    return null; // e.g. 10.5
                }
                return value.longValueExact();
            } catch (NumberFormatException | ArithmeticException e) {
                return null;
            }
        }

        private void finishRow() {
            if (!headerSeen) {
                // blank leading rows are skipped; the first row with content is the header
                if (rowHasContent) {
                    validateHeader();
                    headerSeen = true;
                }
                return;
            }
            if (!rowHasContent) {
                return; // fully blank row: skipped, not counted
            }
            if (rows.size() >= MAX_ROWS) {
                throw new PoImportValidationException("ROWS_EXCEEDED",
                        "Workbook exceeds the maximum of " + MAX_ROWS + " data rows");
            }
            rows.add(new PoRawRow(currentRowNumber, pendingSku, pendingQty, pendingError));
        }

        /**
         * Locked header rules: first nonblank row must be exactly "sku" then
         * "quantity" (case-insensitive, fixed order); extra columns are ignored
         * unless they repeat a required name.
         */
        private void validateHeader() {
            String first = headerCells.size() > 0 ? headerCells.get(0) : null;
            String second = headerCells.size() > 1 ? headerCells.get(1) : null;
            if (!"sku".equalsIgnoreCase(first == null ? "" : first.trim())) {
                throw new PoImportValidationException("HEADER_INVALID",
                        "Header row must start with 'sku' then 'quantity'");
            }
            if (!"quantity".equalsIgnoreCase(second == null ? "" : second.trim())) {
                throw new PoImportValidationException("HEADER_INVALID",
                        "Header row must start with 'sku' then 'quantity'");
            }
            for (int i = 2; i < headerCells.size(); i++) {
                String extra = headerCells.get(i);
                if (extra != null && ("sku".equalsIgnoreCase(extra.trim())
                        || "quantity".equalsIgnoreCase(extra.trim()))) {
                    throw new PoImportValidationException("HEADER_DUPLICATE",
                            "Header row repeats required column '" + extra.trim() + "'");
                }
            }
        }

        private static String tag(String localName, String qName) {
            if (localName != null && !localName.isEmpty()) {
                return localName;
            }
            int colon = qName.indexOf(':');
            return colon >= 0 ? qName.substring(colon + 1) : qName;
        }
    }
}
