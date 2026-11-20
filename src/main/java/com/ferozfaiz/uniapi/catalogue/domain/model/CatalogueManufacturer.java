package com.ferozfaiz.uniapi.catalogue.domain.model;

import java.time.OffsetDateTime;

public record CatalogueManufacturer(
  Long id,
  String name,
  String slug,
  boolean active,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {}
