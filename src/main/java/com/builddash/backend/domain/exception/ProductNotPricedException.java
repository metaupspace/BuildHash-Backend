package com.builddash.backend.domain.exception;

import java.util.UUID;

public class ProductNotPricedException extends NotFoundException {

    public ProductNotPricedException(UUID productId) {
        super("PRODUCT_BASE_PRICE_NOT_FOUND", "No base price found for product: " + productId);
    }
}
