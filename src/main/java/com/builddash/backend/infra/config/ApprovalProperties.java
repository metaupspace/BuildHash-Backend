package com.builddash.backend.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 9-D escalation defaults. stage-hours is snapshotted onto each ApprovalRequest at
 *  creation — later config changes affect only new requests. */
@Component
@ConfigurationProperties(prefix = "approval.escalation")
@Getter
@Setter
public class ApprovalProperties {

    private int stageHours = 24;
}
