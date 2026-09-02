package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.model.NotificationLog;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.domain.port.NotificationLogRepository;
import com.builddash.backend.domain.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PiiBackfillSweeperTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private NotificationLogRepository notificationLogRepository;

    private PiiBackfillSweeper sweeper;

    @BeforeEach
    void setUp() {
        sweeper = new PiiBackfillSweeper(jdbcTemplate, userRepository, addressRepository, notificationLogRepository);
    }

    @Test
    void nonPrefixedUserRow_loadedAndReSaved() {
        UUID id = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class)))
                .thenReturn(List.of(id)).thenReturn(List.of()).thenReturn(List.of());
        User legacy = new User();
        legacy.setId(id);
        legacy.setPhone("+919876543210");
        when(userRepository.findById(id)).thenReturn(Optional.of(legacy));

        sweeper.sweep();

        verify(userRepository).save(legacy);
        assertThat(sweeper.isCompleted()).isTrue();
    }

    @Test
    void noLegacyRows_completesLatchAndSkipsFutureRuns() {
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class)))
                .thenReturn(List.of()).thenReturn(List.of()).thenReturn(List.of());

        sweeper.sweep();

        verify(userRepository, never()).save(any());
        verify(addressRepository, never()).save(any());
        verify(notificationLogRepository, never()).save(any());
        assertThat(sweeper.isCompleted()).isTrue();

        // Second run when latched skips DB calls entirely
        sweeper.sweep();
        verify(jdbcTemplate, org.mockito.Mockito.times(3)).queryForList(anyString(), eq(UUID.class));
    }

    @Test
    void oneUserFailure_doesNotBlockSiblings() {
        UUID failing = UUID.randomUUID();
        UUID sibling = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class)))
                .thenReturn(List.of(failing, sibling)).thenReturn(List.of()).thenReturn(List.of());
        when(userRepository.findById(failing)).thenThrow(new IllegalStateException("row locked"));
        when(userRepository.findById(sibling)).thenReturn(Optional.empty());

        assertThatCode(() -> sweeper.sweep()).doesNotThrowAnyException();

        verify(userRepository).findById(sibling);
    }

    @Test
    void legacyNotificationRow_reSavedThroughRepository() {
        UUID id = UUID.randomUUID();
        when(jdbcTemplate.queryForList(anyString(), eq(UUID.class)))
                .thenReturn(List.of()).thenReturn(List.of()).thenReturn(List.of(id));
        NotificationLog legacy = new NotificationLog();
        legacy.setId(id);
        legacy.setRecipientPhone("+919876543210");
        when(notificationLogRepository.findById(id)).thenReturn(Optional.of(legacy));

        sweeper.sweep();

        verify(notificationLogRepository).save(legacy);
    }
}
