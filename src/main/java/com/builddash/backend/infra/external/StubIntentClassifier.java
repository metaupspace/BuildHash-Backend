package com.builddash.backend.infra.external;

import com.builddash.backend.domain.port.IntentClassifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * NAMED BEHAVIOR — ALWAYS ESCALATES, BY DESIGN: this stub's fixed confidence (0.2) is
 * always below SupportChatServiceImpl's threshold (0.6), so every /support/chat message
 * becomes an escalated ticket regardless of content. The classify() call and threshold
 * comparison are real code paths that always resolve the same way given the stub — the
 * same honesty as StubImageSearchProvider's "stub always returns zero matches". The
 * real-vendor phase swaps in a real IntentClassifier implementation per OCP.
 */
@Component
@Profile("!prod")
@Slf4j
public class StubIntentClassifier implements IntentClassifier {

    static final double STUB_CONFIDENCE = 0.2;

    @Override
    public Classification classify(String text) {
        return new Classification("UNKNOWN", STUB_CONFIDENCE);
    }
}
