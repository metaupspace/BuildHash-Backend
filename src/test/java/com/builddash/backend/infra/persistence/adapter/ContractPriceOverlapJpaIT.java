package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.ContractPrice;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ContractPriceRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.infra.persistence.entity.ContractPriceEntity;
import com.builddash.backend.infra.persistence.repository.ContractPriceJpaRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ContractPriceRepositoryAdapterTest (Mockito) already proves the application-level overlap
 * check. This proves the DB-level backstop actually rejects too — by going straight through
 * ContractPriceJpaRepository, which bypasses the adapter's overlap check entirely. If this
 * test ever passed with two overlapping rows both committed, excl_contract_pricing_no_overlap
 * would not be doing its job.
 */
class ContractPriceOverlapJpaIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ContractPriceJpaRepository contractPriceJpaRepository;
    @Autowired
    private ContractPriceRepository contractPriceRepository;

    private UUID saveUser() {
        User user = new User();
        user.setPhone("+91" + (9000000000L + (Math.abs(UUID.randomUUID().getMostSignificantBits()) % 100000000L)));
        return userRepository.save(user).getId();
    }

    private UUID saveProduct() {
        Category category = new Category();
        category.setName("Cement");
        category.setSlug("cement-" + UUID.randomUUID());
        UUID categoryId = categoryRepository.save(category).getId();

        Product product = new Product();
        product.setName("Contract Test Product");
        product.setSlug("contract-test-" + UUID.randomUUID());
        product.setCategoryId(categoryId);
        product.setHsnCode("1001");
        return productRepository.save(product).getId();
    }

    private static ContractPriceEntity entity(UUID userId, UUID productId, Instant from, Instant to) {
        ContractPriceEntity entity = new ContractPriceEntity();
        entity.setUserId(userId);
        entity.setProductId(productId);
        entity.setUnitPrice(new BigDecimal("50.00"));
        entity.setEffectiveFrom(from);
        entity.setEffectiveTo(to);
        return entity;
    }

    @Test
    void rawConcurrentInsert_bypassingTheAdapter_isRejectedByTheDbExclusionConstraint() {
        UUID userId = saveUser();
        UUID productId = saveProduct();
        Instant now = Instant.now();

        contractPriceJpaRepository.saveAndFlush(entity(userId, productId, now.minusSeconds(3600), now.plusSeconds(3600)));

        ContractPriceEntity overlapping = entity(userId, productId, now, now.plusSeconds(7200));
        assertThatThrownBy(() -> contractPriceJpaRepository.saveAndFlush(overlapping))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonOverlappingWindows_forSameUserAndProduct_bothCommitFine() {
        UUID userId = saveUser();
        UUID productId = saveProduct();
        Instant now = Instant.now();

        contractPriceJpaRepository.saveAndFlush(entity(userId, productId, now.minusSeconds(7200), now.minusSeconds(3600)));
        ContractPriceEntity later = contractPriceJpaRepository.saveAndFlush(
                entity(userId, productId, now.minusSeconds(1800), null));

        assertThat(later.getId()).isNotNull();
    }

    @Test
    void adapterSave_overlappingWindow_translatesToDomainException() {
        UUID userId = saveUser();
        UUID productId = saveProduct();
        Instant now = Instant.now();

        contractPriceJpaRepository.saveAndFlush(entity(userId, productId, now.minusSeconds(3600), now.plusSeconds(3600)));

        ContractPrice overlapping = new ContractPrice(null, userId, productId, new BigDecimal("60.00"),
                now, now.plusSeconds(7200), null, null);
        assertThatThrownBy(() -> contractPriceRepository.save(overlapping))
                .isInstanceOf(com.builddash.backend.domain.exception.ContractPriceOverlapException.class);
    }
}
