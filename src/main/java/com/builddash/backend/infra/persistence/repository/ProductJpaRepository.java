package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.infra.persistence.entity.ProductEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

    /**
     * Keyset (seek) pagination on (created_at, id): strictly greater than the cursor pair in
     * ascending order. When cursorCreatedAt is null, the whole tiebreak clause is skipped —
     * that's the first page.
     */
    @Query("select p from ProductEntity p where p.status = :status "
            + "and (cast(:categoryId as uuid) is null or p.categoryId = :categoryId) "
            + "and (cast(:brand as string) is null or p.brand = :brand) "
            + "and (cast(:cursorCreatedAt as timestamp) is null "
            + "     or p.createdAt > :cursorCreatedAt "
            + "     or (p.createdAt = :cursorCreatedAt and p.id > :cursorId)) "
            + "order by p.createdAt asc, p.id asc")
    List<ProductEntity> findActivePage(@Param("status") ProductStatus status,
                                        @Param("categoryId") UUID categoryId,
                                        @Param("brand") String brand,
                                        @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                        @Param("cursorId") UUID cursorId,
                                        Pageable pageable);
}
