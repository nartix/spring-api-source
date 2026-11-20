package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
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
import org.springframework.lang.Nullable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "catalogue_product")
public class CatalogueProductEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Nullable
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false, length = 180)
  private String slug;

  @Enumerated(EnumType.STRING)
  @Column(name = "product_family", nullable = false, length = 30)
  private ProductFamily family;

  @Column(name = "brand_id", nullable = false)
  private Long brandId;

  @Column(name = "manufacturer_id", nullable = false)
  private Long manufacturerId;

  @Column(name = "main_image_url", length = 1000)
  @Nullable
  private String mainImageUrl;

  @Column(name = "short_description", length = 1000)
  @Nullable
  private String shortDescription;

  @Column(name = "is_active", nullable = false)
  private boolean active;

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
