package com.builddash.backend.infra.persistence.converter;

/** addresses-table PII strings (line1, line2). */
public class AddressPiiStringConverter extends EncryptedStringConverter {

    @Override
    protected String keyLabel() {
        return "pii:addresses:string:aes";
    }
}
