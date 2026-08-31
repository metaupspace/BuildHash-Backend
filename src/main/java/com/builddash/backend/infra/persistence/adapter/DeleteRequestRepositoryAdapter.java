package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.DeleteRequestStatus;
import com.builddash.backend.domain.model.DeleteRequest;
import com.builddash.backend.domain.port.DeleteRequestRepository;
import com.builddash.backend.infra.persistence.mapper.DeleteRequestMapper;
import com.builddash.backend.infra.persistence.repository.DeleteRequestJpaRepository;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class DeleteRequestRepositoryAdapter implements DeleteRequestRepository {

    private final DeleteRequestJpaRepository jpaRepository;

    @Override
    public DeleteRequest save(DeleteRequest request) {
        return DeleteRequestMapper.toDomain(jpaRepository.save(DeleteRequestMapper.toEntity(request)));
    }

    @Override
    public Optional<DeleteRequest> findPendingByUserId(UUID userId) {
        return jpaRepository.findByUserIdAndStatus(userId, DeleteRequestStatus.PENDING)
                .map(DeleteRequestMapper::toDomain);
    }

    @Override
    public List<DeleteRequest> findDue(Instant now) {
        return jpaRepository.findByStatusAndDeletionScheduledAtLessThanEqual(DeleteRequestStatus.PENDING, now).stream()
                .map(DeleteRequestMapper::toDomain)
                .toList();
    }
}
