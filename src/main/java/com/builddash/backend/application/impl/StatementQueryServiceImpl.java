package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.StatementQueryService;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.StatementStatus;
import com.builddash.backend.domain.model.Statement;
import com.builddash.backend.domain.port.ObjectStorage;
import com.builddash.backend.domain.port.StatementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Statement reads (9-E). STATEMENT_VIEW via B2bAuthorizer (live DB state, company-wide
 * — site scope never filters statements). Signed URLs only; storage keys stay internal.
 */
@Service
@RequiredArgsConstructor
public class StatementQueryServiceImpl implements StatementQueryService {

    private static final Duration SIGNED_URL_TTL = Duration.ofHours(1);

    private final StatementRepository statementRepository;
    private final B2bAuthorizer b2bAuthorizer;
    private final ObjectStorage objectStorage;

    @Override
    @Transactional(readOnly = true)
    public List<StatementView> list(UUID userId, UUID companyId) {
        b2bAuthorizer.authorize(userId, companyId, CompanyPermission.STATEMENT_VIEW, null, false);
        List<Statement> latestPerPeriod = new ArrayList<>();
        Set<String> seenPeriods = new HashSet<>();
        for (Statement statement : statementRepository.findByCompanyIdOrderByPeriodStartDescVersionDesc(companyId)) {
            if (seenPeriods.add(statement.periodKey())) {
                latestPerPeriod.add(statement);
            }
        }
        return latestPerPeriod.stream().map(this::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StatementView get(UUID userId, UUID statementId) {
        Statement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new com.builddash.backend.domain.exception.NotFoundException(
                        "STATEMENT_NOT_FOUND", "Statement not found: " + statementId));
        b2bAuthorizer.authorize(userId, statement.companyId(), CompanyPermission.STATEMENT_VIEW, null, false);
        return view(statement);
    }

    private StatementView view(Statement statement) {
        if (statement.status() != StatementStatus.READY || statement.pdfStorageKey() == null) {
            return new StatementView(statement, null, null, null, null);
        }
        Instant expiresAt = Instant.now().plus(SIGNED_URL_TTL);
        return new StatementView(statement,
                objectStorage.signedUrl(statement.pdfStorageKey(), SIGNED_URL_TTL), expiresAt,
                objectStorage.signedUrl(statement.xlsxStorageKey(), SIGNED_URL_TTL), expiresAt);
    }
}
