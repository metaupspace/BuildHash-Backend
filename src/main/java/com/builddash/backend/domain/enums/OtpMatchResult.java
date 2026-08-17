package com.builddash.backend.domain.enums;

/**
 * Models "no OTP on file" as a distinct outcome from "wrong code" so callers never have to
 * infer meaning from an ambiguous boolean — an interface contract issue the LSP guideline flags.
 */
public enum OtpMatchResult {
    NOT_FOUND, MISMATCH, MATCH
}
