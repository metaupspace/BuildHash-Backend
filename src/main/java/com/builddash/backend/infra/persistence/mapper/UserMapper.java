package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.User;
import com.builddash.backend.infra.persistence.entity.UserEntity;

public final class UserMapper {

    private UserMapper() {
    }

    public static User toDomain(UserEntity entity) {
        User user = new User();
        user.setId(entity.getId());
        user.setPhone(entity.getPhone());
        user.setEmail(entity.getEmail());
        user.setGoogleId(entity.getGoogleId());
        user.setName(entity.getName());
        user.setBusinessName(entity.getBusinessName());
        user.setGstNumber(entity.getGstNumber());
        user.setGstinStatus(entity.getGstinStatus());
        user.setCreatedAt(entity.getCreatedAt());
        user.setUpdatedAt(entity.getUpdatedAt());
        return user;
    }

    public static UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.getId());
        entity.setPhone(user.getPhone());
        entity.setEmail(user.getEmail());
        entity.setGoogleId(user.getGoogleId());
        entity.setName(user.getName());
        entity.setBusinessName(user.getBusinessName());
        entity.setGstNumber(user.getGstNumber());
        entity.setGstinStatus(user.getGstinStatus());
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());
        return entity;
    }
}
