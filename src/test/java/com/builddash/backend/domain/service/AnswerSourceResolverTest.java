package com.builddash.backend.domain.service;

import com.builddash.backend.domain.enums.AnswerSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerSourceResolverTest {

    private final AnswerSourceResolver resolver = new AnswerSourceResolver();

    @Test
    void resolve_vendorRole_returnsVendor() {
        assertThat(resolver.resolve(List.of("USER", "VENDOR"))).isEqualTo(AnswerSource.VENDOR);
    }

    @Test
    void resolve_staffRole_returnsStaff() {
        assertThat(resolver.resolve(List.of("STAFF"))).isEqualTo(AnswerSource.STAFF);
    }

    @Test
    void resolve_plainUserRole_returnsCustomer() {
        assertThat(resolver.resolve(List.of("USER"))).isEqualTo(AnswerSource.CUSTOMER);
    }

    @Test
    void resolve_nullRoles_returnsCustomer() {
        assertThat(resolver.resolve(null)).isEqualTo(AnswerSource.CUSTOMER);
    }

    @Test
    void resolve_vendorTakesPrecedenceOverStaff() {
        assertThat(resolver.resolve(List.of("STAFF", "VENDOR"))).isEqualTo(AnswerSource.VENDOR);
    }
}
