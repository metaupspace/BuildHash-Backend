package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.UserProfileResponse;
import com.builddash.backend.domain.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getPhone(),
                user.getEmail(),
                user.getName(),
                user.getBusinessName(),
                user.getGstNumber(),
                user.getGstinStatus()
        );
    }
}
