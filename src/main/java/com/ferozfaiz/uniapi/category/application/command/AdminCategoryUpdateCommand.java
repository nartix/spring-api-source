package com.ferozfaiz.uniapi.category.application.command;

public record AdminCategoryUpdateCommand(Long parentId, String name, String slug, boolean active) {}
