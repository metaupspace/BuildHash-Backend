package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.ProductDetailResponse;
import com.builddash.backend.api.dto.response.ProductListItemResponse;
import com.builddash.backend.api.dto.response.ProductListResponse;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductDetail;
import com.builddash.backend.domain.model.ProductPage;
import com.builddash.backend.domain.model.ProductPageCursor;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductPageCursor toCursor(String cursor) {
        return cursor == null ? null : ProductPageCursor.decode(cursor);
    }

    public ProductListResponse toListResponse(ProductPage page) {
        var items = page.items().stream().map(this::toListItem).toList();
        String nextCursor = page.nextCursor() == null ? null : page.nextCursor().encode();
        return new ProductListResponse(items, nextCursor);
    }

    private ProductListItemResponse toListItem(Product product) {
        String primaryImageUrl = product.getImages().isEmpty() ? null : product.getImages().get(0).url();
        return new ProductListItemResponse(product.getId().toString(), product.getName(), product.getSlug(),
                product.getBrand(), product.getCategoryId().toString(), primaryImageUrl);
    }

    public ProductDetailResponse toDetailResponse(ProductDetail detail) {
        return new ProductDetailResponse(
                detail.id().toString(),
                detail.name(),
                detail.slug(),
                detail.categoryId().toString(),
                detail.categoryName(),
                detail.brand(),
                detail.hsnCode(),
                detail.gstRatePercent(),
                detail.attributes(),
                detail.images(),
                detail.inStock() ? "in_stock" : "out_of_stock",
                detail.status()
        );
    }
}
