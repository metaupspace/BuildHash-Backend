package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.VendorAdminService;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.RfqValidationException;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Vendor;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VendorAdminServiceImplTest {

    private VendorRepository vendorRepository;
    private CategoryRepository categoryRepository;
    private VendorAdminService vendorAdminService;

    private UUID categoryId;
    private UUID otherCategoryId;

    @BeforeEach
    void setUp() {
        vendorRepository = mock(VendorRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        vendorAdminService = new VendorAdminServiceImpl(vendorRepository, categoryRepository);

        categoryId = UUID.randomUUID();
        otherCategoryId = UUID.randomUUID();
        lenient().when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(mock(Category.class)));
        lenient().when(categoryRepository.findById(otherCategoryId)).thenReturn(Optional.of(mock(Category.class)));
        lenient().when(vendorRepository.save(any(Vendor.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_requiresCategories() {
        assertThatThrownBy(() -> vendorAdminService.create("V", List.of()))
                .isInstanceOf(RfqValidationException.class)
                .extracting("code")
                .isEqualTo("VENDOR_CATEGORY_REQUIRED");
    }

    @Test
    void create_requiresName() {
        assertThatThrownBy(() -> vendorAdminService.create("  ", List.of(categoryId)))
                .isInstanceOf(RfqValidationException.class)
                .extracting("code")
                .isEqualTo("VENDOR_NAME_REQUIRED");
    }

    @Test
    void create_unknownCategory_throwsNotFound() {
        UUID missing = UUID.randomUUID();
        when(categoryRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorAdminService.create("V", List.of(missing)))
                .isInstanceOf(NotFoundException.class)
                .extracting("code")
                .isEqualTo("CATEGORY_NOT_FOUND");
    }

    @Test
    void create_happyPath_activeVendorWithDistinctCategories() {
        Vendor created = vendorAdminService.create("V", List.of(categoryId, categoryId, otherCategoryId));

        assertThat(created.active()).isTrue();
        assertThat(created.categoryIds()).containsExactlyInAnyOrder(categoryId, otherCategoryId);
        verify(vendorRepository).save(created);
    }

    @Test
    void update_missingVendor_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(vendorRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vendorAdminService.update(id, "V", List.of(categoryId), null))
                .isInstanceOf(NotFoundException.class)
                .extracting("code")
                .isEqualTo("VENDOR_NOT_FOUND");
    }

    @Test
    void update_partialPatch_keepsUntouchedFields() {
        UUID id = UUID.randomUUID();
        Vendor existing = new Vendor(id, "Old", false, List.of(categoryId), null, null);
        when(vendorRepository.findById(id)).thenReturn(Optional.of(existing));

        Vendor updated = vendorAdminService.update(id, "New", null, null);

        assertThat(updated.name()).isEqualTo("New");
        assertThat(updated.active()).isFalse();        // untouched
        assertThat(updated.categoryIds()).containsExactly(categoryId); // untouched
    }

    @Test
    void update_deactivationBlocksNewQuotesOnly() {
        UUID id = UUID.randomUUID();
        Vendor existing = new Vendor(id, "V", true, List.of(categoryId), null, null);
        when(vendorRepository.findById(id)).thenReturn(Optional.of(existing));

        Vendor updated = vendorAdminService.update(id, null, null, false);

        assertThat(updated.active()).isFalse();
        assertThat(updated.categoryIds()).containsExactly(categoryId);
    }

    @Test
    void update_categoryReplacement_requiresNonEmpty() {
        UUID id = UUID.randomUUID();
        when(vendorRepository.findById(id)).thenReturn(Optional.of(
                new Vendor(id, "V", true, List.of(categoryId), null, null)));

        assertThatThrownBy(() -> vendorAdminService.update(id, null, List.of(), null))
                .isInstanceOf(RfqValidationException.class)
                .extracting("code")
                .isEqualTo("VENDOR_CATEGORY_REQUIRED");
    }
}
