package com.builddash.backend.domain.model;

import java.util.List;

public record ProductPage(List<Product> items, ProductPageCursor nextCursor) {
}
