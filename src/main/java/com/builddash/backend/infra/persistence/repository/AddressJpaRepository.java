package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.AddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AddressJpaRepository extends JpaRepository<AddressEntity, UUID> {
    List<AddressEntity> findByUserId(UUID userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update AddressEntity a set a.line1 = '', a.line2 = null, a.lat = null, a.lng = null "
            + "where a.userId = :userId and exists (select o.id from OrderEntity o where o.addressId = a.id)")
    void anonymizeOrderReferencedByUserId(@org.springframework.lang.NonNull UUID userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from AddressEntity a where a.userId = :userId "
            + "and not exists (select o.id from OrderEntity o where o.addressId = a.id)")
    void deleteUnreferencedByUserId(@org.springframework.lang.NonNull UUID userId);
}
