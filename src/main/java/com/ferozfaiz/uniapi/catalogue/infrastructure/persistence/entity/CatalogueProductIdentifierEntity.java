package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductIdentifierFamily;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.Nullable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "catalogue_product_identifier")
public class CatalogueProductIdentifierEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Nullable
  private Long id;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProductIdentifierFamily family;

  @Column(nullable = false, length = 14)
  private String value;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "canonical_gtin14", nullable = false, length = 14)
  private String canonicalGtin14;

  @Column(name = "is_primary", nullable = false)
  private boolean primary;

  @Column(name = "created_at", nullable = false)
  @Nullable
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  @Nullable
  private OffsetDateTime updatedAt;

  @PrePersist
  void prePersist() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }
}
