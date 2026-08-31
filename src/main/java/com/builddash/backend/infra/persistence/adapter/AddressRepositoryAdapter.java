package com.builddash.backend.infra.persistence.adapter;

import org.springframework.transaction.annotation.Transactional;

import com.builddash.backend.domain.model.Address;
import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.infra.persistence.mapper.AddressMapper;
import com.builddash.backend.infra.persistence.repository.AddressJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class AddressRepositoryAdapter implements AddressRepository {

    private final AddressJpaRepository jpaRepository;

    @Override
    public Address save(Address address) {
        return AddressMapper.toDomain(jpaRepository.save(AddressMapper.toEntity(address)));
    }

    @Override
    public Optional<Address> findById(UUID id) {
        return jpaRepository.findById(id).map(AddressMapper::toDomain);
    }

    @Override
    public List<Address> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream().map(AddressMapper::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void anonymizeOrderReferencedByUserId(UUID userId) {
        jpaRepository.anonymizeOrderReferencedByUserId(userId);
    }

    @Override
    @Transactional
    public void deleteUnreferencedByUserId(UUID userId) {
        jpaRepository.deleteUnreferencedByUserId(userId);
    }
}
