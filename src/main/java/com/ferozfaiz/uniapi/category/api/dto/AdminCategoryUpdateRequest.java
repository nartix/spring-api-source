package com.ferozfaiz.uniapi.category.api.dto;

public record AdminCategoryUpdateRequest(Long parentId, String name, String slug, Boolean active) {}
