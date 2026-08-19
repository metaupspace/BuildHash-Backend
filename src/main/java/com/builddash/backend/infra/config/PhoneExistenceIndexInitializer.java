package com.builddash.backend.infra.config;

import com.builddash.backend.domain.port.PhoneExistenceIndex;
import com.builddash.backend.domain.port.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

/**
 * The Bloom filter is in-memory only and resets on restart — this repopulates it from Postgres
 * once at startup so existing users aren't misreported as new until they next verify an OTP.
 */
@RequiredArgsConstructor
@Component
public class PhoneExistenceIndexInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PhoneExistenceIndex phoneExistenceIndex;


    @Override
    public void run(ApplicationArguments args) {
        userRepository.findAllPhones().forEach(phoneExistenceIndex::markExists);
    }
}
