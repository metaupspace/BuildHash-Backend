package com.builddash.backend.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DPDP deletion knobs (PLAN_PHASE8 decision 9 + OQ-9). supportTickets is a REAL config
 * switch read by AccountDeletionSweeper's classification — flipping it to RETAIN must be a
 * one-config change made by the product owner, not a code change.
 */
@Component
@ConfigurationProperties(prefix = "account.deletion")
@Getter
@Setter
public class AccountDeletionProperties {

    public enum SupportTicketDeletion { HARD_DELETE, RETAIN }

    /** Grace period before a pending deletion request becomes due. */
    private int graceDays = 30;

    /** OQ-9: product/legal decision — HARD_DELETE is the executable default the flag ships with. */
    private SupportTicketDeletion supportTickets = SupportTicketDeletion.HARD_DELETE;
}
