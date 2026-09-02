package com.builddash.backend.infra.excel;

import com.builddash.backend.domain.exception.PoImportValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoWorkbookParserSecurityTest {

    private PoWorkbookParserAdapter parser;

    @BeforeEach
    void setUp() {
        parser = new PoWorkbookParserAdapter();
    }

    @Test
    void parse_plainTextOrNonZip_rejectedWithInvalidContentType() {
        byte[] nonZipBytes = "this is not a zip file or excel workbook".getBytes(StandardCharsets.UTF_8);
        InputStream in = new ByteArrayInputStream(nonZipBytes);

        assertThatThrownBy(() -> parser.parse(in))
                .isInstanceOf(PoImportValidationException.class)
                .hasMessageContaining("missing OOXML/ZIP signature")
                .satisfies(ex -> assertThat(((PoImportValidationException) ex).getCode()).isEqualTo("INVALID_CONTENT_TYPE"));
    }

    @Test
    void parse_tooSmall_rejectedWithInvalidWorkbook() {
        byte[] tinyBytes = new byte[]{0x01, 0x02};
        InputStream in = new ByteArrayInputStream(tinyBytes);

        assertThatThrownBy(() -> parser.parse(in))
                .isInstanceOf(PoImportValidationException.class)
                .hasMessageContaining("File too small")
                .satisfies(ex -> assertThat(((PoImportValidationException) ex).getCode()).isEqualTo("INVALID_WORKBOOK"));
    }

    @Test
    void parse_zipSignatureWithMalformedStructure_rejectedWithInvalidWorkbook() {
        byte[] fakeZipBytes = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00};
        InputStream in = new ByteArrayInputStream(fakeZipBytes);

        assertThatThrownBy(() -> parser.parse(in))
                .isInstanceOf(PoImportValidationException.class)
                .satisfies(ex -> assertThat(((PoImportValidationException) ex).getCode()).isEqualTo("INVALID_WORKBOOK"));
    }
}
