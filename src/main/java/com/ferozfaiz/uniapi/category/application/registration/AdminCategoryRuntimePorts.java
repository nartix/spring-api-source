package com.ferozfaiz.uniapi.category.application.registration;

import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryBasicMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyReadPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryLifecyclePort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOperationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOrderingPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.modulith.NamedInterface;

@NamedInterface("registration")
public final class AdminCategoryRuntimePorts {

  private final Optional<AdminCategoryReadPort> categoryRead;
  private final Optional<AdminCategoryHierarchyReadPort> hierarchyRead;
  private final Optional<AdminCategoryBasicMutationPort> basicMutation;
  private final Optional<AdminCategoryHierarchyMutationPort> hierarchyMutation;
  private final Optional<AdminCategoryOrderingPort> ordering;
  private final Optional<AdminCategoryLifecyclePort> lifecycle;

  public AdminCategoryRuntimePorts(
    Optional<AdminCategoryReadPort> categoryRead,
    Optional<AdminCategoryHierarchyReadPort> hierarchyRead,
    Optional<AdminCategoryBasicMutationPort> basicMutation,
    Optional<AdminCategoryHierarchyMutationPort> hierarchyMutation,
    Optional<AdminCategoryOrderingPort> ordering,
    Optional<AdminCategoryLifecyclePort> lifecycle
  ) {
    if (
      categoryRead == null ||
      hierarchyRead == null ||
      basicMutation == null ||
      hierarchyMutation == null ||
      ordering == null ||
      lifecycle == null
    ) throw new IllegalArgumentException("runtime port slots must not be null");
    this.categoryRead = categoryRead;
    this.hierarchyRead = hierarchyRead;
    this.basicMutation = basicMutation;
    this.hierarchyMutation = hierarchyMutation;
    this.ordering = ordering;
    this.lifecycle = lifecycle;
  }

  public static AdminCategoryRuntimePorts empty() {
    return new AdminCategoryRuntimePorts(
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty()
    );
  }

  public Optional<AdminCategoryReadPort> categoryRead() {
    return categoryRead;
  }

  public Optional<AdminCategoryHierarchyReadPort> hierarchyRead() {
    return hierarchyRead;
  }

  public Optional<AdminCategoryBasicMutationPort> basicMutation() {
    return basicMutation;
  }

  public Optional<AdminCategoryHierarchyMutationPort> hierarchyMutation() {
    return hierarchyMutation;
  }

  public Optional<AdminCategoryOrderingPort> ordering() {
    return ordering;
  }

  public Optional<AdminCategoryLifecyclePort> lifecycle() {
    return lifecycle;
  }

  public Optional<AdminCategoryOperationPort> portFor(Operation operation) {
    return AdminCategoryProviderCapability.forOperation(operation).flatMap(
      capability ->
        switch (capability) {
          case CATEGORY_READ -> categoryRead.map(port -> port);
          case HIERARCHY_READ -> hierarchyRead.map(port -> port);
          case BASIC_MUTATION -> basicMutation.map(port -> port);
          case HIERARCHY_MUTATION -> hierarchyMutation.map(port -> port);
          case ORDERING -> ordering.map(port -> port);
          case LIFECYCLE -> lifecycle.map(port -> port);
        }
    );
  }

  public Set<AdminCategoryProviderCapability> populatedCapabilities() {
    EnumSet<AdminCategoryProviderCapability> result = EnumSet.noneOf(
      AdminCategoryProviderCapability.class
    );
    if (categoryRead.isPresent()) result.add(AdminCategoryProviderCapability.CATEGORY_READ);
    if (hierarchyRead.isPresent()) result.add(AdminCategoryProviderCapability.HIERARCHY_READ);
    if (basicMutation.isPresent()) result.add(AdminCategoryProviderCapability.BASIC_MUTATION);
    if (hierarchyMutation.isPresent()) result.add(
      AdminCategoryProviderCapability.HIERARCHY_MUTATION
    );
    if (ordering.isPresent()) result.add(AdminCategoryProviderCapability.ORDERING);
    if (lifecycle.isPresent()) result.add(AdminCategoryProviderCapability.LIFECYCLE);
    return Collections.unmodifiableSet(result);
  }
}
