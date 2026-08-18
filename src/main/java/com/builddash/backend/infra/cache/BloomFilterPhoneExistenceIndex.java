package com.builddash.backend.infra.cache;

import com.builddash.backend.domain.port.PhoneExistenceIndex;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * In-memory only — reset on restart, repopulated at startup by PhoneExistenceIndexInitializer.
 * Sized for ~1M phones at 1% false-positive rate; re-tune capacity if the user base outgrows
 * that (a false positive here only costs a wasted "existing user" hint, never a correctness bug).
 */
@Component
public class BloomFilterPhoneExistenceIndex implements PhoneExistenceIndex {

    private static final int EXPECTED_INSERTIONS = 1_000_000;
    private static final double FALSE_POSITIVE_RATE = 0.01;

    private final BloomFilter<CharSequence> filter = BloomFilter.create(
            Funnels.stringFunnel(StandardCharsets.UTF_8), EXPECTED_INSERTIONS, FALSE_POSITIVE_RATE);

    @Override
    public boolean mightExist(String phone) {
        return filter.mightContain(phone);
    }

    @Override
    public void markExists(String phone) {
        filter.put(phone);
    }
}
