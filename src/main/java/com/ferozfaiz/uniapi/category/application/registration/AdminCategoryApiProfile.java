package com.ferozfaiz.uniapi.category.application.registration;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.RootModel;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum AdminCategoryApiProfile {
  STANDARD_CATEGORY_V1_NUMERIC(
    AdminCategoryIdentifierProfile.NUMERIC_LONG_V1,
    new HardMaxima(1_000L, 120L, 4L, 5L, 10_000L, 50L, 25L, Integer.MAX_VALUE),
    Collections.unmodifiableSet(EnumSet.allOf(Operation.class)),
    Collections.unmodifiableSet(EnumSet.allOf(LogicalField.class)),
    RootModel.IMPLICIT_NULL_PARENT
  );

  private final AdminCategoryIdentifierProfile identifierProfile;
  private final HardMaxima hardMaxima;
  private final Set<Operation> supportedOperations;
  private final Set<LogicalField> standardLogicalFields;
  private final RootModel rootModel;

  AdminCategoryApiProfile(
    AdminCategoryIdentifierProfile identifierProfile,
    HardMaxima hardMaxima,
    Set<Operation> supportedOperations,
    Set<LogicalField> standardLogicalFields,
    RootModel rootModel
  ) {
    this.identifierProfile = identifierProfile;
    this.hardMaxima = hardMaxima;
    this.supportedOperations = supportedOperations;
    this.standardLogicalFields = standardLogicalFields;
    this.rootModel = rootModel;
  }

  public AdminCategoryIdentifierProfile identifierProfile() {
    return identifierProfile;
  }

  public HardMaxima hardMaxima() {
    return hardMaxima;
  }

  public Set<Operation> supportedOperations() {
    return supportedOperations;
  }

  public Set<LogicalField> standardLogicalFields() {
    return standardLogicalFields;
  }

  public RootModel rootModel() {
    return rootModel;
  }

  public record HardMaxima(
    long pageSize,
    long searchInputLength,
    long filterSelections,
    long sortSelections,
    long treeSize,
    long parentOptionPageSize,
    long includeIdCount,
    long hierarchyDepth
  ) {}
}
