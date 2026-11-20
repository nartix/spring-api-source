package com.ferozfaiz.uniapi.iam.infrastructure.persistence.entity;

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
@Table(name = "iam_user")
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Nullable
  private Long id;

  @Column(nullable = false, unique = true)
  private String username;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 512)
  private String passwordHash;

  @Column(name = "first_name")
  @Nullable
  private String firstName;

  @Column(name = "last_name")
  @Nullable
  private String lastName;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "is_account_non_expired", nullable = false)
  private boolean accountNonExpired;

  @Column(name = "is_account_non_locked", nullable = false)
  private boolean accountNonLocked;

  @Column(name = "is_credentials_non_expired", nullable = false)
  private boolean credentialsNonExpired;

  @Column(name = "last_login_at")
  @Nullable
  private OffsetDateTime lastLoginAt;

  @Column(name = "last_activity_at")
  @Nullable
  private OffsetDateTime lastActivityAt;

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
