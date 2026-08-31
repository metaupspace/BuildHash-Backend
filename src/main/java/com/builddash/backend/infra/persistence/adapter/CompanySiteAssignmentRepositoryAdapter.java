package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.port.CompanySiteAssignmentRepository;
import com.builddash.backend.infra.persistence.entity.CompanySiteAssignmentEntity;
import com.builddash.backend.infra.persistence.repository.CompanySiteAssignmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MANDATORY joins an existing transaction (the membership mutation that already holds
 * the last-admin locks) so the delete+insert of replaceForMember is atomic with the
 * membership change the caller is committing.
 */
@Repository
@RequiredArgsConstructor
class CompanySiteAssignmentRepositoryAdapter implements CompanySiteAssignmentRepository {

    private final CompanySiteAssignmentJpaRepository jpaRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceForMember(UUID memberId, List<UUID> siteIds) {
        jpaRepository.deleteByMemberId(memberId);
        jpaRepository.flush();
        for (UUID siteId : siteIds) {
            jpaRepository.save(new CompanySiteAssignmentEntity(memberId, siteId));
        }
    }

    @Override
    public List<UUID> findSiteIdsByMemberId(UUID memberId) {
        return jpaRepository.findByMemberId(memberId).stream()
                .map(CompanySiteAssignmentEntity::getSiteId)
                .toList();
    }

    @Override
    public boolean existsByMemberIdAndSiteId(UUID memberId, UUID siteId) {
        return jpaRepository.existsByMemberIdAndSiteId(memberId, siteId);
    }

    @Override
    public List<UUID> findMemberIdsBySiteId(UUID siteId) {
        List<UUID> memberIds = new ArrayList<>();
        jpaRepository.findBySiteId(siteId).forEach(a -> memberIds.add(a.getMemberId()));
        return memberIds;
    }
}
