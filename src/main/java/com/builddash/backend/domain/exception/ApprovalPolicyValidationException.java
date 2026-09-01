package com.builddash.backend.domain.exception;

public class ApprovalPolicyValidationException extends DomainException {

    public ApprovalPolicyValidationException(String code, String message) {
        super(code, message);
    }

    public static ApprovalPolicyValidationException emptyRoleStages() {
        return new ApprovalPolicyValidationException("POLICY_ROLE_STAGES_REQUIRED",
                "At least one ordered role stage is required");
    }

    public static ApprovalPolicyValidationException duplicateRoleStage() {
        return new ApprovalPolicyValidationException("POLICY_ROLE_STAGES_DISTINCT",
                "Role stages must be distinct");
    }

    public static ApprovalPolicyValidationException invalidEscalationHours() {
        return new ApprovalPolicyValidationException("POLICY_ESCALATION_HOURS_INVALID",
                "escalationHours must be >= 1");
    }

    public static ApprovalPolicyValidationException negativeThreshold() {
        return new ApprovalPolicyValidationException("POLICY_THRESHOLD_INVALID",
                "amountThreshold must be >= 0 when present");
    }
}
