package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.infra.persistence.mapper.CategoryMapper;
import com.builddash.backend.infra.persistence.repository.CategoryJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpaRepository;


    @Override
    public List<Category> findAll() {
        return jpaRepository.findAll().stream().map(CategoryMapper::toDomain).toList();
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return jpaRepository.findById(id).map(CategoryMapper::toDomain);
    }

    @Override
    public Category save(Category category) {
        return CategoryMapper.toDomain(jpaRepository.save(CategoryMapper.toEntity(category)));
    }
}
