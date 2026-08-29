package com.builddash.backend.infra.persistence.converter;

/** users-table PII strings (phone, email, name, business_name, gst_number, google_id). */
public class UserPiiStringConverter extends EncryptedStringConverter {

    @Override
    protected String keyLabel() {
        return "pii:users:string:aes";
    }
}
