package com.ferozfaiz.uniapi.data.application.command;

import org.springframework.lang.Nullable;

public record UpdateCategoryCommand(
  String slug,
  String name,
  @Nullable String description,
  boolean active
) {}
