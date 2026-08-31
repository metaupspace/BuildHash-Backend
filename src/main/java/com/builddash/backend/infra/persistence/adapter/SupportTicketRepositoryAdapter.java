package com.builddash.backend.infra.persistence.adapter;

import org.springframework.transaction.annotation.Transactional;

import com.builddash.backend.domain.model.SupportTicket;
import com.builddash.backend.domain.port.SupportTicketRepository;
import com.builddash.backend.infra.persistence.mapper.SupportTicketMapper;
import com.builddash.backend.infra.persistence.repository.SupportTicketJpaRepository;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class SupportTicketRepositoryAdapter implements SupportTicketRepository {

    private final SupportTicketJpaRepository jpaRepository;


    @Override
    public SupportTicket save(SupportTicket ticket) {
        return SupportTicketMapper.toDomain(jpaRepository.save(SupportTicketMapper.toEntity(ticket)));
    }

    @Override
    public Optional<SupportTicket> findById(UUID id) {
        return jpaRepository.findById(id).map(SupportTicketMapper::toDomain);
    }

    @Override
    public List<SupportTicket> findByUserId(UUID userId) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(SupportTicketMapper::toDomain)
                .toList();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}
