package com.ferozfaiz.uniapi.catalogue.domain.model;

import java.time.OffsetDateTime;

public record CatalogueMerchantStore(
  Long id,
  String name,
  String slug,
  boolean active,
  OffsetDateTime createdAt,
  OffsetDateTime updatedAt
) {}
