package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.UserAccountService;
import com.builddash.backend.application.service.UserProfileReader;
import com.builddash.backend.application.service.UserProfileWriter;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.enums.GstinStatus;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserAccountService, UserProfileReader, UserProfileWriter {

    private final UserRepository userRepository;


    @Override
    public User findOrCreateByPhone(String phone) {
        // No wrapping @Transactional: the recovery read after a constraint violation
        // needs a fresh transaction, not the aborted one
        return userRepository.findByPhone(phone).orElseGet(() -> {
            try {
                User user = new User();
                user.setPhone(phone);
                return userRepository.save(user);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Concurrent first login won the phone unique index — return their row
                return userRepository.findByPhone(phone).orElseThrow(() -> e);
            }
        });
    }

    @Override
    @Transactional
    public User findOrCreateByGoogle(String googleId, String email, String name) {
        return userRepository.findByGoogleId(googleId)
                .or(() -> email == null ? Optional.empty() : userRepository.findByEmail(email))
                .map(user -> {
                    if (user.getGoogleId() == null) {
                        user.setGoogleId(googleId);
                        return userRepository.save(user);
                    }
                    return user;
                })
                .orElseGet(() -> {
                    User user = new User();
                    user.setGoogleId(googleId);
                    user.setEmail(email);
                    user.setName(name);
                    return userRepository.save(user);
                });
    }

    @Override
    public User getProfile(UUID userId) {
        return getUserOrThrow(userId);
    }

    @Override
    @Transactional
    public User updateProfile(UUID userId, String name, String businessName, String gstNumber) {
        User user = getUserOrThrow(userId);
        if (name != null) {
            user.setName(name);
        }
        if (businessName != null) {
            user.setBusinessName(businessName);
        }
        if (gstNumber != null) {
            user.setGstNumber(gstNumber);
            user.setGstinStatus(gstNumber.isBlank() ? null : GstinStatus.PENDING);
        }
        return userRepository.save(user);
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
    }

    @Override
    public User createGuestUser() {
        User guest = new User();
        guest.setGuest(true);
        return userRepository.save(guest);
    }

    @Override
    public void markGuestMerged(java.util.UUID guestUserId, java.util.UUID realUserId) {
        User guest = getUserOrThrow(guestUserId);
        guest.setMergedIntoUserId(realUserId);
        userRepository.save(guest);
    }
}
