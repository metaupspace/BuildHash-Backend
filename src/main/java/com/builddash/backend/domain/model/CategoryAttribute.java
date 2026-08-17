package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.AttributeType;

import java.util.List;

/**
 * One entry in a Category's attributeSchema — the mechanism for "variable attributes per
 * category" without per-category code branches (see PLAN_PHASE1.md Section 2). A new
 * category with new attributes is a new row, not new Java.
 */
public record CategoryAttribute(
        String key,
        String label,
        AttributeType type,
        boolean required,
        String unit,
        List<String> enumValues
) {
}
