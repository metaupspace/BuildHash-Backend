package com.builddash.backend.domain.port;

import com.builddash.backend.domain.exception.PoImportValidationException;

import java.io.InputStream;
import java.util.List;

/**
 * Streaming XLSX parser for PO bulk imports.
 *
 * External contract (locked): the workbook's first sheet, header row
 * "sku | quantity" (case-insensitive, fixed order, extra columns ignored),
 * at most 5000 nonblank data rows (fully blank rows are skipped and not
 * counted).
 *
 * Identifier mapping (locked): the XLSX column "sku" is the catalog's business
 * identifier products.slug. The system has no SKU field; the parsed field is
 * therefore named productSlug everywhere in domain and API. The header is NOT
 * renamed — only this mapping bridges it.
 *
 * Cell typing is strict: sku must be a text cell, quantity a whole numeric
 * cell; formulas, dates and coerced strings fail the row (CELL_TYPE_INVALID),
 * never the file. Structural problems (bad ZIP/XML, header violations, empty
 * or oversized input, row cap) throw PoImportValidationException — the whole
 * file is rejected.
 */
public interface PoWorkbookParser {

    /** At most 5000 entries; rowIndex is the 1-based sheet row number. */
    List<PoRawRow> parse(InputStream in);

    record PoRawRow(int rowIndex, String productSlug, Long quantity, String errorCode) {

        public boolean valid() {
            return errorCode == null;
        }
    }
}
