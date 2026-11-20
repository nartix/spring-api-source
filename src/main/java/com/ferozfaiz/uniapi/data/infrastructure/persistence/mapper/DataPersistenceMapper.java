package com.ferozfaiz.uniapi.data.infrastructure.persistence.mapper;

import com.ferozfaiz.uniapi.data.domain.model.Category;
import com.ferozfaiz.uniapi.data.infrastructure.persistence.entity.CategoryEntity;

public final class DataPersistenceMapper {

  private DataPersistenceMapper() {}

  public static Category toDomain(CategoryEntity entity) {
    return new Category(
      entity.getId(),
      entity.getParentId(),
      entity.getSortRank(),
      entity.getSlug(),
      entity.getName(),
      entity.getDescription(),
      entity.isActive(),
      entity.getCreatedAt(),
      entity.getUpdatedAt(),
      entity.getCreatedBy(),
      entity.getUpdatedBy()
    );
  }

  public static CategoryEntity toEntity(Category category) {
    return CategoryEntity.builder()
      .id(category.id())
      .parentId(category.parentId())
      .sortRank(category.sortRank())
      .slug(category.slug())
      .name(category.name())
      .description(category.description())
      .active(category.active())
      .createdAt(category.createdAt())
      .updatedAt(category.updatedAt())
      .createdBy(category.createdBy())
      .updatedBy(category.updatedBy())
      .build();
  }
}
