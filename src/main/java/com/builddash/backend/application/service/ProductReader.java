package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.ProductDetail;
import com.builddash.backend.domain.model.ProductPage;
import com.builddash.backend.domain.model.ProductPageCursor;

import java.util.UUID;

public interface ProductReader {

    ProductPage list(UUID categoryId, String brand, ProductPageCursor cursor, int limit);

    ProductDetail getDetail(UUID id);
}
