package com.builddash.backend.domain.exception;

public class InvalidSupportTicketStateException extends DomainException {
    public InvalidSupportTicketStateException(String currentStatus, String targetStatus) {
        super("INVALID_SUPPORT_TICKET_STATE", "Cannot transition support ticket from " + currentStatus + " to " + targetStatus);
    }
}
