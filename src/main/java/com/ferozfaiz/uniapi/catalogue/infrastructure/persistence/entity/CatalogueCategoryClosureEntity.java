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
@IdClass(CatalogueCategoryClosureId.class)
@Table(name = "catalogue_category_closure")
public class CatalogueCategoryClosureEntity {

  @Id
  @Column(name = "ancestor_id")
  private Long ancestorId;

  @Id
  @Column(name = "descendant_id")
  private Long descendantId;

  @Column(nullable = false)
  private int depth;
}
