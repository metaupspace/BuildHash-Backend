package com.builddash.backend.infra.external;

import com.builddash.backend.domain.port.EmailSender;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Prod EmailSender (9-E) — spring-boot-starter-mail, config via spring.mail.*. */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    @Override
    public void send(EmailRequest request) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(request.to());
            helper.setSubject(request.subject());
            helper.setText(request.body(), false);
            if (request.attachments() != null) {
                for (Attachment attachment : request.attachments()) {
                    helper.addAttachment(attachment.filename(),
                            new org.springframework.core.io.ByteArrayResource(attachment.data()),
                            attachment.contentType());
                }
            }
            mailSender.send(message);
        } catch (jakarta.mail.MessagingException e) {
            throw new IllegalStateException("Failed to send statement email to " + request.to(), e);
        }
    }
}
