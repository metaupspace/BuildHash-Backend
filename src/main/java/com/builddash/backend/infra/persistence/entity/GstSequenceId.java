package com.builddash.backend.infra.persistence.entity;

import com.builddash.backend.domain.enums.GstSequenceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GstSequenceId implements Serializable {
    private GstSequenceType sequenceType;
    private String fiscalYear;
}
