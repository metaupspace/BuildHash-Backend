package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.DeleteRequestService;
import com.builddash.backend.domain.exception.DeleteRequestPendingException;
import com.builddash.backend.domain.model.DeleteRequest;
import com.builddash.backend.domain.port.DeleteRequestRepository;
import com.builddash.backend.infra.config.AccountDeletionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteRequestServiceImpl implements DeleteRequestService {

    private final DeleteRequestRepository deleteRequestRepository;
    private final AccountDeletionProperties properties;

    @Override
    public DeleteRequest requestDeletion(UUID userId) {
        // Fast path: the common case is a plain duplicate request.
        deleteRequestRepository.findPendingByUserId(userId).ifPresent(pending -> {
            throw new DeleteRequestPendingException(userId, pending.deletionScheduledAt());
        });

        Instant now = Instant.now();
        Instant scheduledFor = now.plus(java.time.Duration.ofDays(properties.getGraceDays()));
        DeleteRequest request = DeleteRequest.pending(UUID.randomUUID(), userId, now, scheduledFor);
        try {
            return deleteRequestRepository.save(request);
        } catch (DataIntegrityViolationException e) {
            // Two racing POSTs both passed the existence check; the partial unique index
            // (user_id WHERE status='PENDING') is the backstop — loser translates to 409,
            // never a 500 (ContractPrice overlap precedent).
            log.info("Concurrent delete-request race resolved by unique index for user {}", userId);
            throw new DeleteRequestPendingException(userId, scheduledFor);
        }
    }
}
