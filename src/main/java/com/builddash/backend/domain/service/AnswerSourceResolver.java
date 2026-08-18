package com.builddash.backend.domain.service;

import com.builddash.backend.domain.enums.AnswerSource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves answer.source from the caller's JWT roles. Every token issued today hardcodes
 * roles=["USER"] (no vendor/staff auth exists yet), so this always resolves CUSTOMER in
 * practice until a vendor/staff role is actually issued — see PROGRESS.md Wave 2.
 */
@Component
public class AnswerSourceResolver {

    public AnswerSource resolve(List<String> roles) {
        if (roles == null) {
            return AnswerSource.CUSTOMER;
        }
        if (roles.contains("VENDOR")) {
            return AnswerSource.VENDOR;
        }
        if (roles.contains("STAFF")) {
            return AnswerSource.STAFF;
        }
        return AnswerSource.CUSTOMER;
    }
}
