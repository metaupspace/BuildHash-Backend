package com.builddash.backend.infra.excel;

import com.builddash.backend.domain.exception.PoImportValidationException;
import com.builddash.backend.domain.port.PoWorkbookParser;
import com.builddash.backend.support.PoTestWorkbooks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoWorkbookParserAdapterTest {

    private PoWorkbookParser parser;

    @BeforeEach
    void setUp() {
        parser = new PoWorkbookParserAdapter();
    }

    private List<PoWorkbookParser.PoRawRow> parse(byte[] bytes) {
        return parser.parse(new ByteArrayInputStream(bytes));
    }

    @Test
    void validWorkbook_parsesTextSkuAndWholeNumberQty() {
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"},
                {"cement-bag-50", 100},
                {"steel-rod-12", 5}
        }));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).productSlug()).isEqualTo("cement-bag-50");
        assertThat(rows.get(0).quantity()).isEqualTo(100L);
        assertThat(rows.get(0).errorCode()).isNull();
        assertThat(rows.get(1).productSlug()).isEqualTo("steel-rod-12");
        assertThat(rows.get(1).quantity()).isEqualTo(5L);
    }

    @Test
    void headers_caseInsensitive_fixedOrder() {
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(new Object[][]{
                {"SKU", "Quantity"},
                {"a", 1}
        }));
        assertThat(rows).hasSize(1);
    }

    @Test
    void wrongHeaderOrder_rejected() {
        assertThatThrownBy(() -> parse(PoTestWorkbooks.workbook(new Object[][]{
                {"quantity", "sku"},
                {"a", 1}
        })))
                .isInstanceOf(PoImportValidationException.class)
                .extracting("code").isEqualTo("HEADER_INVALID");
    }

    @Test
    void missingQuantityHeader_rejected() {
        assertThatThrownBy(() -> parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku"},
                {"a"}
        })))
                .isInstanceOf(PoImportValidationException.class)
                .extracting("code").isEqualTo("HEADER_INVALID");
    }

    @Test
    void duplicateRequiredHeader_rejected() {
        assertThatThrownBy(() -> parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity", "sku"},
                {"a", 1, "x"}
        })))
                .isInstanceOf(PoImportValidationException.class)
                .extracting("code").isEqualTo("HEADER_DUPLICATE");
    }

    @Test
    void extraColumns_ignored() {
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity", "remark", "vendor"},
                {"a", 1, "note", "acme"}
        }));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).errorCode()).isNull();
    }

    @Test
    void extraSheets_ignored() {
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbookWithExtraSheet(
                new Object[][]{{"sku", "quantity"}, {"first", 1}},
                new Object[][]{{"sku", "quantity"}, {"second", 2}}));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).productSlug()).isEqualTo("first");
    }

    @Test
    void fullyBlankRows_skippedAndNotCounted() {
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"},
                {"a", 1},
                {null, null},
                {null, null},
                {"b", 2}
        }));
        assertThat(rows).hasSize(2); // blank rows neither parsed nor counted
    }

    @Test
    void blankSku_rejectedWithSkuRequired() {
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"},
                {null, 1},
                {"   ", 2}
        }));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).errorCode()).isEqualTo("SKU_REQUIRED");
        assertThat(rows.get(0).productSlug()).isNull();
        assertThat(rows.get(1).errorCode()).isEqualTo("SKU_REQUIRED");
    }

    @Test
    void numericSku_rejectedWithCellTypeInvalid() {
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"},
                {12345, 1}
        }));
        assertThat(rows.get(0).errorCode()).isEqualTo("CELL_TYPE_INVALID");
        assertThat(rows.get(0).productSlug()).isNull();
    }

    @Test
    void formulaSku_failClosed() {
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"},
                {PoTestWorkbooks.Formula.of("CONCATENATE(\"a\",\"b\")"), 1}
        }));
        assertThat(rows.get(0).errorCode()).isEqualTo("CELL_TYPE_INVALID");
    }

    @Test
    void formulaQuantity_failClosed() {
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"},
                {"a", PoTestWorkbooks.Formula.of("1+2")}
        }));
        assertThat(rows.get(0).errorCode()).isEqualTo("CELL_TYPE_INVALID");
    }

    @Test
    void dateQuantity_rejectedNotCoerced() {
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"},
                {"a", LocalDate.of(2026, 9, 1)}
        }));
        assertThat(rows.get(0).errorCode()).isEqualTo("CELL_TYPE_INVALID");
    }

    @Test
    void stringQuantity_rejectedWithCellTypeInvalid() {
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"},
                {"a", "10pcs"}
        }));
        assertThat(rows.get(0).errorCode()).isEqualTo("CELL_TYPE_INVALID");
    }

    @Test
    void decimalQuantity_rejectedWithCellTypeInvalid() {
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"},
                {"a", 10.5}
        }));
        assertThat(rows.get(0).errorCode()).isEqualTo("CELL_TYPE_INVALID");
    }

    @Test
    void blankQuantity_rejectedWithQtyRequired() {
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"},
                {"a", null}
        }));
        assertThat(rows.get(0).errorCode()).isEqualTo("QTY_REQUIRED");
    }

    @Test
    void scientificNotationWholeNumber_accepted() {
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"},
                {"a", 1.0E2} // stored as 100, integral
        }));
        assertThat(rows.get(0).quantity()).isEqualTo(100L);
        assertThat(rows.get(0).errorCode()).isNull();
    }

    @Test
    void exactly5000Rows_accepted() {
        Object[][] sheet = new Object[5001][];
        sheet[0] = new Object[]{"sku", "quantity"};
        for (int i = 1; i <= 5000; i++) {
            sheet[i] = new Object[]{"sku-" + i, 1};
        }
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(sheet));
        assertThat(rows).hasSize(5000);
    }

    @Test
    void rows5001_rejectedWholeFile() {
        Object[][] sheet = new Object[5002][];
        sheet[0] = new Object[]{"sku", "quantity"};
        for (int i = 1; i <= 5001; i++) {
            sheet[i] = new Object[]{"sku-" + i, 1};
        }
        assertThatThrownBy(() -> parse(PoTestWorkbooks.workbook(sheet)))
                .isInstanceOf(PoImportValidationException.class)
                .extracting("code").isEqualTo("ROWS_EXCEEDED");
    }

    @Test
    void blankTrailingRows_doNotCountTowardCap() {
        // 5000 data rows + blank trailing rows: still within the cap
        Object[][] sheet = new Object[5004][];
        sheet[0] = new Object[]{"sku", "quantity"};
        for (int i = 1; i <= 5000; i++) {
            sheet[i] = new Object[]{"sku-" + i, 1};
        }
        sheet[5001] = new Object[]{null, null};
        sheet[5002] = new Object[]{null, null};
        sheet[5003] = new Object[]{null, null};
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(sheet));
        assertThat(rows).hasSize(5000);
    }

    @Test
    void emptyStream_rejected() {
        assertThatThrownBy(() -> parser.parse(InputStream.nullInputStream()))
                .isInstanceOf(PoImportValidationException.class)
                .extracting("code").isEqualTo("INVALID_WORKBOOK");
    }

    @Test
    void nonZipSignature_rejected() {
        assertThatThrownBy(() -> parse("this is not a zip file at all..".getBytes()))
                .isInstanceOf(PoImportValidationException.class)
                .extracting("code").isEqualTo("INVALID_CONTENT_TYPE");
    }

    @Test
    void malformedZip_rejected() {
        byte[] fake = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x01, 0x02, 0x03};
        assertThatThrownBy(() -> parse(fake))
                .isInstanceOf(PoImportValidationException.class)
                .extracting("code").isEqualTo("INVALID_WORKBOOK");
    }

    @Test
    void longSkuValue_preservedVerbatimForServiceValidation() {
        String longSlug = "x".repeat(300);
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"},
                {longSlug, 1}
        }));
        // Parser passes it through; SKU length validation is the service's call
        assertThat(rows.get(0).productSlug()).hasSize(300);
        assertThat(rows.get(0).errorCode()).isNull();
    }

    @Test
    void headerOnly_noDataRows_yieldsEmptyResult() {
        List<PoWorkbookParser.PoRawRow> rows = parse(PoTestWorkbooks.workbook(new Object[][]{
                {"sku", "quantity"}
        }));
        assertThat(rows).isEmpty();
    }
}
