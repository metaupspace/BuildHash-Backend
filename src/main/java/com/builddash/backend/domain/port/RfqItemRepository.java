package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.RfqItem;

import java.util.List;
import java.util.UUID;

public interface RfqItemRepository {

    RfqItem save(RfqItem item);

    List<RfqItem> findByRfqId(UUID rfqId);
}
