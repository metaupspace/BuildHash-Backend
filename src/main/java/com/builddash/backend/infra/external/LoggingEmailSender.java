package com.builddash.backend.infra.external;

import com.builddash.backend.domain.port.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Non-prod EmailSender (9-E) — LoggingSmsNotificationSender precedent: logs only. */
@Component
@Profile("!prod")
@Slf4j
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(EmailRequest request) {
        int totalBytes = request.attachments() == null ? 0
                : request.attachments().stream().mapToInt(a -> a.data() == null ? 0 : a.data().length).sum();
        log.info("[EMAIL] to='{}' subject='{}' attachments={} totalBytes={}",
                request.to(), request.subject(),
                request.attachments() == null ? 0 : request.attachments().size(), totalBytes);
    }
}
