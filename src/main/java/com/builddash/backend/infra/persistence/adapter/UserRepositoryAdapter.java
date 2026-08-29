package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.infra.crypto.HmacIndex;
import com.builddash.backend.infra.crypto.PiiKeyProvider;
import com.builddash.backend.infra.persistence.entity.UserEntity;
import com.builddash.backend.infra.persistence.mapper.UserMapper;
import com.builddash.backend.infra.persistence.repository.UserJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Blind-index boundary (PLAN_PHASE8 decision 3): lookups HMAC-then-query the *_idx columns;
 * saves populate the idx columns from the domain's plaintext values. Callers still speak
 * plaintext phone/email/googleId — the crypto stays entirely inside this adapter.
 */
@Repository
@RequiredArgsConstructor
class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final PiiKeyProvider keyProvider;


    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        return jpaRepository.findByPhoneIdx(HmacIndex.index(phone, phoneIndexKey())).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmailIdx(HmacIndex.index(email, emailIndexKey())).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByGoogleId(String googleId) {
        return jpaRepository.findByGoogleIdIdx(HmacIndex.index(googleId, googleIdIndexKey())).map(UserMapper::toDomain);
    }

    @Override
    public List<String> findAllPhones() {
        return jpaRepository.findAllPhones();
    }

    @Override
    public User save(User user) {
        UserEntity entity = UserMapper.toEntity(user);
        entity.setPhoneIdx(HmacIndex.index(user.getPhone(), phoneIndexKey()));
        entity.setEmailIdx(HmacIndex.index(user.getEmail(), emailIndexKey()));
        entity.setGoogleIdIdx(HmacIndex.index(user.getGoogleId(), googleIdIndexKey()));
        return UserMapper.toDomain(jpaRepository.save(entity));
    }

    private byte[] phoneIndexKey() {
        return keyProvider.derivedKey("pii:users:phone:hmac");
    }

    private byte[] emailIndexKey() {
        return keyProvider.derivedKey("pii:users:email:hmac");
    }

    private byte[] googleIdIndexKey() {
        return keyProvider.derivedKey("pii:users:google-id:hmac");
    }
}
