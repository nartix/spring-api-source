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
  name = "iam_user_permission",
  uniqueConstraints = @UniqueConstraint(
    name = "uc_iam_user_permission",
    columnNames = { "user_id", "permission_id" }
  )
)
public class UserPermissionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Nullable
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "permission_id", nullable = false)
  private Long permissionId;

  @Column(name = "granted_at", nullable = false)
  private OffsetDateTime grantedAt;

  @Column(name = "expires_at")
  @Nullable
  private OffsetDateTime expiresAt;

  @Column(name = "created_by")
  @Nullable
  private String createdBy;
}
