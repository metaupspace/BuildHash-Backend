package com.builddash.backend.infra.external;

import com.builddash.backend.domain.port.IntentClassifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one trivial stub test plan Section 7 names — pins the fixed classification AND that
 * its confidence sits below SupportChatServiceImpl's escalation threshold, which is what
 * makes /support/chat always escalate.
 */
class StubIntentClassifierTest {

    private final StubIntentClassifier classifier = new StubIntentClassifier();

    @Test
    void returnsFixedLowConfidenceClassification_belowEscalationThreshold() {
        IntentClassifier.Classification classification = classifier.classify("where is my cement order?");

        assertThat(classification.intent()).isEqualTo("UNKNOWN");
        assertThat(classification.confidence()).isEqualTo(0.2);
        // Threshold is package-private in application.impl — pin the constant here instead
        // of widening its visibility for a test.
        assertThat(classification.confidence()).isLessThan(0.6);
    }

    @Test
    void classificationIsContentIndependent() {
        assertThat(classifier.classify("anything")).isEqualTo(classifier.classify("something else entirely"));
    }
}
