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
@Table(name = "catalogue_graphics_card_product")
public class CatalogueGraphicsCardProductEntity {

  @Id
  @Column(name = "product_id")
  private Long productId;

  @Column(name = "gpu_chipset")
  @Nullable
  private String gpuChipset;

  @Column(name = "vram_gb")
  @Nullable
  private Integer vramGb;

  @Column(name = "memory_type")
  @Nullable
  private String memoryType;

  @Column(name = "boost_clock_mhz")
  @Nullable
  private Integer boostClockMhz;

  @Column(name = "interface_type")
  @Nullable
  private String interfaceType;

  @Column(name = "length_mm")
  @Nullable
  private Integer lengthMm;

  @Column(name = "tdp_watts")
  @Nullable
  private Integer tdpWatts;
}
