package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.model.StatementCompanyInfo;
import com.builddash.backend.domain.model.StatementOrderRow;

import java.util.List;

/** Statement PDF rendering (9-E) — one row per order, invoice-renderer conventions. */
public interface StatementRenderer {

    byte[] render(Statement statement, StatementCompanyInfo company, List<StatementOrderRow> orderRows);
}
