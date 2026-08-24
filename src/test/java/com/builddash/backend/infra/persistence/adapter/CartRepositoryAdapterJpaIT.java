package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.Cart;
import com.builddash.backend.domain.model.CartLineItem;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.CartLineItemRepository;
import com.builddash.backend.domain.port.CartRepository;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CartRepositoryAdapterJpaIT extends AbstractIntegrationTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartLineItemRepository cartLineItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void saveAndFindCartWithItems_persistsCorrectly() {
        User u = new User();
        u.setPhone("+919876543210");
        User user = userRepository.save(u);

        Category category = new Category();
        category.setName("Cement");
        category.setSlug("cement-" + UUID.randomUUID());
        Category savedCategory = categoryRepository.save(category);

        Product product = new Product();
        product.setName("UltraTech Cement");
        product.setSlug("ultratech-cement-" + UUID.randomUUID());
        product.setHsnCode("2523");
        product.setCategoryId(savedCategory.getId());
        Product savedProduct = productRepository.save(product);

        Cart cart = new Cart(
                UUID.randomUUID(),
                user.getId(),
                null,
                com.builddash.backend.domain.enums.CartType.PRIMARY, "WELCOME50",
                List.of()
        );
        cartRepository.save(cart);

        CartLineItem item = new CartLineItem(
                UUID.randomUUID(),
                cart.id(),
                savedProduct.getId(),
                5,
                null
        );
        cartLineItemRepository.save(item);

        Optional<Cart> found = cartRepository.findById(cart.id());
        assertThat(found).isPresent();
        assertThat(found.get().appliedCartCoupon()).isEqualTo("WELCOME50");
        assertThat(found.get().items()).hasSize(1);
        assertThat(found.get().items().get(0).quantity()).isEqualTo(5);
    }
}
