package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.HsnGstRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HsnGstRateJpaRepository extends JpaRepository<HsnGstRateEntity, String> {
}
