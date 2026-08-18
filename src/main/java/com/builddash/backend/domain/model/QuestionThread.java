package com.builddash.backend.domain.model;

import java.util.List;

/**
 * Enriched read-model for a product's Q&A list — same rationale as ProductDetail: keeps
 * Question itself free of view-specific composition.
 */
public record QuestionThread(Question question, List<Answer> answers) {
}
