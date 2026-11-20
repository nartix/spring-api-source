package com.ferozfaiz.uniapi.catalogue.api.dto;

import java.time.OffsetDateTime;

public record ManufacturerResponse(
  Long id,
  String name,
  String slug,
  boolean active,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {}
