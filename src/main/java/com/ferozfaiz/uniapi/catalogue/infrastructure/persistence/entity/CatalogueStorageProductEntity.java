package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "catalogue_storage_product")
public class CatalogueStorageProductEntity {

  @Id
  @Column(name = "product_id")
  private Long productId;

  @Column(name = "storage_type", length = 60)
  @Nullable
  private String storageType;

  @Column(name = "capacity_gb")
  @Nullable
  private Integer capacityGb;

  @Column(name = "interface_type", length = 80)
  @Nullable
  private String interfaceType;

  @Column(name = "form_factor", length = 80)
  @Nullable
  private String formFactor;

  @Column(name = "read_speed_mbps")
  @Nullable
  private Integer readSpeedMbps;

  @Column(name = "write_speed_mbps")
  @Nullable
  private Integer writeSpeedMbps;

  @Nullable
  private Integer rpm;

  @Column(name = "cache_mb")
  @Nullable
  private Integer cacheMb;
}
