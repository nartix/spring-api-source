package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "catalogue_cpu_product")
public class CatalogueCpuProductEntity {

  @Id
  @Column(name = "product_id")
  private Long productId;

  @Nullable
  private String socket;

  @Column(name = "core_count")
  @Nullable
  private Integer coreCount;

  @Column(name = "thread_count")
  @Nullable
  private Integer threadCount;

  @Column(name = "base_clock_ghz")
  @Nullable
  private BigDecimal baseClockGhz;

  @Column(name = "boost_clock_ghz")
  @Nullable
  private BigDecimal boostClockGhz;

  @Nullable
  private String architecture;

  @Column(name = "integrated_graphics")
  @Nullable
  private Boolean integratedGraphics;

  @Column(name = "tdp_watts")
  @Nullable
  private Integer tdpWatts;
}
