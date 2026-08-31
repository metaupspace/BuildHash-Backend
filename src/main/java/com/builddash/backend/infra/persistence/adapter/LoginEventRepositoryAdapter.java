package com.builddash.backend.infra.persistence.adapter;

import org.springframework.transaction.annotation.Transactional;

import com.builddash.backend.domain.model.LoginEvent;
import com.builddash.backend.domain.port.LoginEventRepository;
import com.builddash.backend.infra.persistence.mapper.LoginEventMapper;
import com.builddash.backend.infra.persistence.repository.LoginEventJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class LoginEventRepositoryAdapter implements LoginEventRepository {

    private final LoginEventJpaRepository jpaRepository;


    @Override
    public LoginEvent save(LoginEvent event) {
        return LoginEventMapper.toDomain(jpaRepository.save(LoginEventMapper.toEntity(event)));
    }

    @Override
    public List<LoginEvent> findByUserIdOrderByCreatedAtDesc(UUID userId) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(LoginEventMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        jpaRepository.deleteByUserId(userId);
    }
}
