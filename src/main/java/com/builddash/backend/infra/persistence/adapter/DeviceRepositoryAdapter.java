package com.builddash.backend.infra.persistence.adapter;

import org.springframework.transaction.annotation.Transactional;

import com.builddash.backend.domain.model.Device;
import com.builddash.backend.domain.port.DeviceRepository;
import com.builddash.backend.infra.persistence.mapper.DeviceMapper;
import com.builddash.backend.infra.persistence.repository.DeviceJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class DeviceRepositoryAdapter implements DeviceRepository {

    private final DeviceJpaRepository jpaRepository;


    @Override
    public Device save(Device device) {
        return DeviceMapper.toDomain(jpaRepository.save(DeviceMapper.toEntity(device)));
    }

    @Override
    public Optional<Device> findById(UUID id) {
        return jpaRepository.findById(id).map(DeviceMapper::toDomain);
    }

    @Override
    public Optional<Device> findByIdAndUserId(UUID id, UUID userId) {
        return jpaRepository.findByIdAndUserId(id, userId).map(DeviceMapper::toDomain);
    }

    @Override
    public List<Device> findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(UUID userId) {
        return jpaRepository.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId).stream()
                .map(DeviceMapper::toDomain)
                .toList();
    }

    @Override
    public void revokeAllActiveByUserId(UUID userId, Instant now) {
        jpaRepository.revokeAllActiveByUserId(userId, now);
    }

    @Override
    public java.util.List<Device> findAllByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream()
                .map(DeviceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        jpaRepository.deleteByUserId(userId);
    }
}
