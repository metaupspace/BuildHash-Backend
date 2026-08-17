package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {

    List<Category> findAll();

    Optional<Category> findById(UUID id);

    Category save(Category category);
}
