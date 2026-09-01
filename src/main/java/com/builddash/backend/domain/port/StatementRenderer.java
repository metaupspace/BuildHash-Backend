package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.model.StatementOrderRow;

import java.util.List;

/** Statement PDF rendering (9-E) — one row per order, invoice-renderer conventions. */
public interface StatementRenderer {

    byte[] render(Statement statement, CompanyInfo company, List<StatementOrderRow> orderRows);

    record CompanyInfo(String name, String gstNumber, String statementEmail) {
    }
}
