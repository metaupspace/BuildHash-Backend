package com.builddash.backend.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 9-E statement knobs. Attachment cap is enforced from the persisted size columns
 *  BEFORE artifact bytes are loaded for email. */
@Component
@ConfigurationProperties(prefix = "statement")
@Getter
@Setter
public class StatementProperties {

    private Generation generation = new Generation();
    private Email email = new Email();

    @Getter
    @Setter
    public static class Generation {
        private int maxAttempts = 3;
        private int staleMinutes = 15;
        /** Bound on (company, period) pairs handled per scheduler pass. */
        private int sweepBatchLimit = 25;
    }

    @Getter
    @Setter
    public static class Email {
        private int maxAttempts = 5;
        /** Conservative default: 10 MiB combined PDF + XLSX. */
        private long maxAttachmentBytes = 10_485_760L;
        private int sweepBatchLimit = 25;
    }
}
