package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CatalogueProductCategoryId implements Serializable {

  private Long productId;

  private Long categoryId;
}
