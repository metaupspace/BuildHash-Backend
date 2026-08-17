package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.GstinStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class User {

    private UUID id;
    private String phone;
    private String email;
    private String googleId;
    private String name;
    private String businessName;
    private String gstNumber;
    private GstinStatus gstinStatus;
    private Instant createdAt;
    private Instant updatedAt;
}
