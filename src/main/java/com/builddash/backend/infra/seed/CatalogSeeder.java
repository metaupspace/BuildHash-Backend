package com.builddash.backend.infra.seed;

import com.builddash.backend.domain.enums.AttributeType;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.CategoryAttribute;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductImage;
import com.builddash.backend.domain.model.StockEntry;
import com.builddash.backend.application.service.CatalogWriteService;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.service.ProductFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Dev-only seed data so /categories and /products are smoke-testable via Swagger without a
 * product-creation endpoint (none exists yet — Catalog writes are vendor/admin-side, out of
 * scope for this customer backend, per builddash-backend-phase-plan.md). Mirrors Phase 0's
 * HSN seed migration: real data seeded early so the phase is actually exercisable.
 */
@Component
@Profile("dev")
public class CatalogSeeder implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final CatalogWriteService catalogWriteService;
    private final ProductFactory productFactory;

    public CatalogSeeder(CategoryRepository categoryRepository, CatalogWriteService catalogWriteService,
                          ProductFactory productFactory) {
        this.categoryRepository = categoryRepository;
        this.catalogWriteService = catalogWriteService;
        this.productFactory = productFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!categoryRepository.findAll().isEmpty()) {
            return;
        }

        Category cement = new Category();
        cement.setName("Cement");
        cement.setSlug("cement");
        cement.setAttributeSchema(List.of(
                new CategoryAttribute("weightKg", "Weight (kg)", AttributeType.NUMBER, true, "kg", null),
                new CategoryAttribute("gradeType", "Grade", AttributeType.ENUM, true, null,
                        List.of("OPC33", "OPC43", "OPC53", "PPC"))
        ));
        cement = categoryRepository.save(cement);

        Category paint = new Category();
        paint.setName("Paint");
        paint.setSlug("paint");
        paint.setAttributeSchema(List.of(
                new CategoryAttribute("volumeLitres", "Volume (L)", AttributeType.NUMBER, true, "L", null),
                new CategoryAttribute("finish", "Finish", AttributeType.ENUM, true, null,
                        List.of("MATTE", "GLOSS", "SATIN"))
        ));
        paint = categoryRepository.save(paint);

        seedProduct(cement, "UltraTech Cement OPC 53 Grade 50kg", "UltraTech", "2523",
                Map.of("weightKg", 50, "gradeType", "OPC53"),
                List.of(new ProductImage("https://picsum.photos/seed/cement1/400", "UltraTech Cement bag", 0)));

        seedProduct(cement, "ACC Gold Water Shield PPC 50kg", "ACC", "2523",
                Map.of("weightKg", 50, "gradeType", "PPC"),
                List.of(new ProductImage("https://picsum.photos/seed/cement2/400", "ACC Cement bag", 0)));

        seedProduct(paint, "Asian Paints Apcolite Matte 4L", "Asian Paints", "3208",
                Map.of("volumeLitres", 4, "finish", "MATTE"),
                List.of(new ProductImage("https://picsum.photos/seed/paint1/400", "Paint can", 0)));
    }

    private void seedProduct(Category category, String name, String brand, String hsnCode,
                              Map<String, Object> attributes, List<ProductImage> images) {
        Product product = productFactory.build(category, name, brand, hsnCode, attributes, images);
        product.setStock(List.of(new StockEntry("WH-DEFAULT", 100)));
        catalogWriteService.saveProductAndEnqueueSync(product);
    }
}
