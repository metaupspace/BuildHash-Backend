package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.WishlistReader;
import com.builddash.backend.application.service.WishlistWriter;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.WishlistEntry;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class WishlistServiceImpl implements WishlistReader, WishlistWriter {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;


    @Override
    public List<WishlistEntry> list(UUID userId) {
        return wishlistRepository.findByUserId(userId);
    }

    @Override
    @Transactional
    public WishlistEntry add(UUID userId, UUID productId) {
        return wishlistRepository.findByUserIdAndProductId(userId, productId)
                .orElseGet(() -> {
                    productRepository.findById(productId)
                            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found: " + productId));
                    WishlistEntry entry = new WishlistEntry();
                    entry.setUserId(userId);
                    entry.setProductId(productId);
                    return wishlistRepository.save(entry);
                });
    }

    @Override
    @Transactional
    public void remove(UUID userId, UUID productId) {
        wishlistRepository.deleteByUserIdAndProductId(userId, productId);
    }
}
