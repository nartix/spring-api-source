package com.ferozfaiz.uniapi.iam.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
  name = "iam_user_role",
  uniqueConstraints = @UniqueConstraint(
    name = "uc_iam_user_role",
    columnNames = { "user_id", "role_id" }
  )
)
public class UserRoleEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Nullable
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "role_id", nullable = false)
  private Long roleId;

  @Column(name = "assigned_at", nullable = false)
  private OffsetDateTime assignedAt;

  @Column(name = "expires_at")
  @Nullable
  private OffsetDateTime expiresAt;

  @Column(name = "created_by")
  @Nullable
  private String createdBy;
}
