package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.RfqItem;
import com.builddash.backend.domain.port.RfqItemRepository;
import com.builddash.backend.infra.persistence.entity.RfqItemEntity;
import com.builddash.backend.infra.persistence.repository.RfqItemJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class RfqItemRepositoryAdapter implements RfqItemRepository {

    private final RfqItemJpaRepository jpaRepository;

    @Override
    public RfqItem save(RfqItem item) {
        RfqItemEntity entity = new RfqItemEntity();
        entity.setId(item.id());
        entity.setRfqId(item.rfqId());
        entity.setProductId(item.productId());
        entity.setQuantity(item.quantity());
        RfqItemEntity saved = jpaRepository.save(entity);
        return new RfqItem(saved.getId(), saved.getRfqId(), saved.getProductId(), saved.getQuantity());
    }

    @Override
    public List<RfqItem> findByRfqId(UUID rfqId) {
        return jpaRepository.findByRfqId(rfqId).stream()
                .map(e -> new RfqItem(e.getId(), e.getRfqId(), e.getProductId(), e.getQuantity()))
                .toList();
    }
}
