package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.PoImportRow;

import java.util.List;
import java.util.UUID;

public interface PoImportRowRepository {

    List<PoImportRow> saveAll(List<PoImportRow> rows);

    List<PoImportRow> findByImportId(UUID importId);

    long countValidByImportId(UUID importId);
}
