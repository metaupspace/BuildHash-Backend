package com.builddash.backend.domain.port;

/**
 * OQ-5: port + stub is the entire honest scope — zero real NLU, no intent taxonomy, no
 * answer generation. A real implementation (future phase) is a new implementation of this
 * interface, ImageSearchProvider's swap shape.
 */
public interface IntentClassifier {

    Classification classify(String text);

    record Classification(String intent, double confidence) {
    }
}
