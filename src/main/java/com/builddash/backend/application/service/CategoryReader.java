package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Category;

import java.util.List;
import java.util.UUID;

public interface CategoryReader {

    List<Category> listAll();

    Category getById(UUID id);
}
