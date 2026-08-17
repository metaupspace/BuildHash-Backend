package com.builddash.backend.domain.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class Category {

    private UUID id;
    private String name;
    private String slug;
    private UUID parentId;
    private List<CategoryAttribute> attributeSchema = new ArrayList<>();
}
