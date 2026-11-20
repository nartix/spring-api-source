package com.ferozfaiz.uniapi.data.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
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
@Table(name = "data_category")
public class CategoryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Nullable
  private Long id;

  @Column(name = "parent_id")
  @Nullable
  private Long parentId;

  @Column(name = "sort_rank", nullable = false)
  private Long sortRank;

  @Column(nullable = false, length = 150)
  private String slug;

  @Column(nullable = false)
  private String name;

  @Column(length = 1000)
  @Nullable
  private String description;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "created_by")
  @Nullable
  private String createdBy;

  @Column(name = "updated_by")
  @Nullable
  private String updatedBy;
}
