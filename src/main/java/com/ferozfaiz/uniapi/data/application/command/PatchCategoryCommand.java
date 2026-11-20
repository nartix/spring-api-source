package com.ferozfaiz.uniapi.data.application.command;

import org.springframework.lang.Nullable;

public record PatchCategoryCommand(
  @Nullable String slug,
  @Nullable String name,
  @Nullable String description,
  @Nullable Boolean active
) {}
