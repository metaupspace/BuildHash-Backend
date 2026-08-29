package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.SupportTicketMessage;
import com.builddash.backend.domain.port.SupportTicketMessageRepository;
import com.builddash.backend.infra.persistence.mapper.SupportTicketMessageMapper;
import com.builddash.backend.infra.persistence.repository.SupportTicketMessageJpaRepository;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class SupportTicketMessageRepositoryAdapter implements SupportTicketMessageRepository {

    private final SupportTicketMessageJpaRepository jpaRepository;


    @Override
    public SupportTicketMessage save(SupportTicketMessage message) {
        return SupportTicketMessageMapper.toDomain(jpaRepository.save(SupportTicketMessageMapper.toEntity(message)));
    }

    @Override
    public List<SupportTicketMessage> findByTicketId(UUID ticketId) {
        return jpaRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(SupportTicketMessageMapper::toDomain)
                .toList();
    }
}
