package com.builddash.backend.domain.exception;

import lombok.Getter;

@Getter
public class GstRateUnresolvedException extends NotFoundException {

    private final String hsnCode;

    public GstRateUnresolvedException(String hsnCode) {
        super("GST_RATE_NOT_FOUND", "No GST rate found for HSN code: " + hsnCode);
        this.hsnCode = hsnCode;
    }
}
