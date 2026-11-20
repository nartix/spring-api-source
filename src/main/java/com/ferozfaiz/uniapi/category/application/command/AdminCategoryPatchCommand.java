package com.ferozfaiz.uniapi.category.application.command;

public record AdminCategoryPatchCommand(
  Long parentId,
  boolean parentIdPresent,
  String name,
  boolean namePresent,
  String slug,
  boolean slugPresent,
  Boolean active,
  boolean activePresent
) {}
