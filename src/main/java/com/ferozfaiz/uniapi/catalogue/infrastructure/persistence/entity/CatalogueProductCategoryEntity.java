package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(CatalogueProductCategoryId.class)
@Table(name = "catalogue_product_category")
public class CatalogueProductCategoryEntity {

  @Id
  @Column(name = "product_id")
  private Long productId;

  @Id
  @Column(name = "category_id")
  private Long categoryId;
}
