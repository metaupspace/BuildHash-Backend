package com.builddash.backend.application.impl;

import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private UserRepository userRepository;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserServiceImpl(userRepository);
    }

    @Test
    void findOrCreateByPhone_newPhone_createsUser() {
        User created = new User();
        created.setPhone("+911111100011");
        when(userRepository.findByPhone("+911111100011")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(created);

        User result = userService.findOrCreateByPhone("+911111100011");

        assertThat(result.getPhone()).isEqualTo("+911111100011");
    }

    @Test
    void findOrCreateByPhone_concurrentDuplicateInsert_returnsExistingUser() {
        // Two concurrent first logins: both miss the read, one wins the phone unique
        // index, the loser must get the winner's row instead of a 500
        User winner = new User();
        winner.setPhone("+911111100012");
        when(userRepository.findByPhone("+911111100012"))
                .thenReturn(Optional.empty())        // first read: nobody yet
                .thenReturn(Optional.of(winner));    // re-read after conflict
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uq_users_phone"));

        User result = userService.findOrCreateByPhone("+911111100012");

        assertThat(result).isSameAs(winner);
    }
}
