package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.model.StatementOrderRow;
import com.builddash.backend.domain.port.StatementAccountingRepository.StatementLine;

import java.util.List;
import java.util.UUID;

/**
 * Statement XLSX rendering (9-E): SXSSF streaming over keyset-paged lines — bounded
 * memory by construction, designed for 100k+ line items.
 */
public interface StatementWorkbookWriter {

    byte[] write(Statement statement, StatementRenderer.CompanyInfo company,
                 List<StatementOrderRow> orderRows, StatementLineSource lineSource);

    /** Supplies line pages in (order_id, line id) order; the writer drains it page by page. */
    interface StatementLineSource {
        List<StatementLine> next(UUID afterOrderId, UUID afterLineId);
    }
}
