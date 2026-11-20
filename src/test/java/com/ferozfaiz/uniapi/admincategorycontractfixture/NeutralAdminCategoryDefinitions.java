package com.ferozfaiz.uniapi.admincategorycontractfixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryApiProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyObligation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.FieldQueryDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.HierarchyDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalOrder;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationalLimits;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OrderingDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.PlacementMode;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.SortDirection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public final class NeutralAdminCategoryDefinitions {

  static final String FLAT_SYSTEM_KEY = "neutral-flat";
  static final String RESTRICTED_SYSTEM_KEY = "neutral-restricted";
  static final String OWNING_MODULE = "neutral-owner";

  private static final List<LogicalField> CORE_FIELDS = List.of(
    LogicalField.IDENTIFIER,
    LogicalField.NAME,
    LogicalField.SLUG,
    LogicalField.ACTIVE,
    LogicalField.CREATED_AT,
    LogicalField.UPDATED_AT
  );
  private static final List<LogicalField> SEARCHABLE_FIELDS = List.of(
    LogicalField.NAME,
    LogicalField.SLUG
  );
  private static final List<LogicalField> ALLOWED_FILTERS = List.of(
    LogicalField.SLUG,
    LogicalField.ACTIVE
  );
  private static final List<LogicalField> ALLOWED_SORTS = CORE_FIELDS;
  private static final AdminCategorySystemDefinition FLAT_DEFINITION = definition(
    FLAT_SYSTEM_KEY,
    new OperationDeclaration(
      List.of(Operation.CAPABILITIES, Operation.LIST, Operation.GET),
      List.of(AdminCategoryPolicyObligation.GENERAL_VALIDATION, AdminCategoryPolicyObligation.QUERY)
    ),
    new OrderingDeclaration(false, false, false, false, List.of(), List.of())
  );
  private static final AdminCategorySystemDefinition RESTRICTED_DEFINITION = definition(
    RESTRICTED_SYSTEM_KEY,
    new OperationDeclaration(
      List.of(Operation.CAPABILITIES, Operation.LIST, Operation.CREATE),
      List.of(
        AdminCategoryPolicyObligation.GENERAL_VALIDATION,
        AdminCategoryPolicyObligation.QUERY,
        AdminCategoryPolicyObligation.MUTATION_VALIDATION
      )
    ),
    new OrderingDeclaration(
      true,
      false,
      false,
      false,
      List.of(PlacementMode.APPEND),
      List.of(
        new LogicalOrder(LogicalField.UPDATED_AT, SortDirection.DESC),
        new LogicalOrder(LogicalField.IDENTIFIER, SortDirection.ASC)
      )
    )
  );

  private NeutralAdminCategoryDefinitions() {}

  public static AdminCategorySystemDefinition flatDefinition() {
    return FLAT_DEFINITION;
  }

  public static AdminCategorySystemDefinition restrictedDefinition() {
    return RESTRICTED_DEFINITION;
  }

  private static AdminCategorySystemDefinition definition(
    String systemKey,
    OperationDeclaration operations,
    OrderingDeclaration ordering
  ) {
    return new AdminCategorySystemDefinition(
      systemKey,
      OWNING_MODULE,
      AdminCategoryApiProfile.STANDARD_CATEGORY_V1_NUMERIC,
      AdminCategoryIdentifierProfile.NUMERIC_LONG_V1,
      operations,
      new HierarchyDeclaration(false, Optional.empty(), Optional.empty(), List.of()),
      ordering,
      new LifecycleDeclaration(false, List.of(), List.of()),
      new FieldQueryDeclaration(CORE_FIELDS, SEARCHABLE_FIELDS, ALLOWED_FILTERS, ALLOWED_SORTS),
      new OperationalLimits(
        Optional.of(1_000L),
        Optional.of(120L),
        Optional.of(4L),
        Optional.of(5L),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
      )
    );
  }
}

final class NeutralAdminCategoryDefinitionsTest {

  @Test
  void flatDefinitionIsExactAndImmutable() {
    AdminCategorySystemDefinition definition = NeutralAdminCategoryDefinitions.flatDefinition();

    assertCommonDefinition(
      definition,
      NeutralAdminCategoryDefinitions.FLAT_SYSTEM_KEY,
      List.of(Operation.CAPABILITIES, Operation.LIST, Operation.GET),
      List.of(AdminCategoryPolicyObligation.GENERAL_VALIDATION, AdminCategoryPolicyObligation.QUERY)
    );
    assertThat(definition.ordering().orderingEnabled()).isFalse();
    assertThat(definition.ordering().rankingEnabled()).isFalse();
    assertThat(definition.ordering().rankExposed()).isFalse();
    assertThat(definition.ordering().reorderSupported()).isFalse();
    assertThat(definition.ordering().placementModes()).isEmpty();
    assertThat(definition.ordering().fallbackOrder()).isEmpty();
    assertThat(NeutralAdminCategoryDefinitions.flatDefinition()).isSameAs(definition);
    assertThatThrownBy(() -> definition.operations().supportedOperations().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
  }

  @Test
  void restrictedDefinitionIsExactAndImmutable() {
    AdminCategorySystemDefinition definition =
      NeutralAdminCategoryDefinitions.restrictedDefinition();

    assertCommonDefinition(
      definition,
      NeutralAdminCategoryDefinitions.RESTRICTED_SYSTEM_KEY,
      List.of(Operation.CAPABILITIES, Operation.LIST, Operation.CREATE),
      List.of(
        AdminCategoryPolicyObligation.GENERAL_VALIDATION,
        AdminCategoryPolicyObligation.QUERY,
        AdminCategoryPolicyObligation.MUTATION_VALIDATION
      )
    );
    assertThat(definition.ordering().orderingEnabled()).isTrue();
    assertThat(definition.ordering().rankingEnabled()).isFalse();
    assertThat(definition.ordering().rankExposed()).isFalse();
    assertThat(definition.ordering().reorderSupported()).isFalse();
    assertThat(definition.ordering().placementModes()).containsExactly(PlacementMode.APPEND);
    assertThat(definition.ordering().fallbackOrder()).containsExactly(
      new LogicalOrder(LogicalField.UPDATED_AT, SortDirection.DESC),
      new LogicalOrder(LogicalField.IDENTIFIER, SortDirection.ASC)
    );
    assertThat(NeutralAdminCategoryDefinitions.restrictedDefinition()).isSameAs(definition);
    assertThatThrownBy(() -> definition.ordering().fallbackOrder().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
  }

  private static void assertCommonDefinition(
    AdminCategorySystemDefinition definition,
    String systemKey,
    List<Operation> operations,
    List<AdminCategoryPolicyObligation> obligations
  ) {
    assertThat(definition.systemKey()).isEqualTo(systemKey);
    assertThat(definition.owningModule()).isEqualTo(NeutralAdminCategoryDefinitions.OWNING_MODULE);
    assertThat(definition.apiProfile()).isSameAs(
      AdminCategoryApiProfile.STANDARD_CATEGORY_V1_NUMERIC
    );
    assertThat(definition.identifierProfile()).isSameAs(
      AdminCategoryIdentifierProfile.NUMERIC_LONG_V1
    );
    assertThat(definition.operations().supportedOperations()).containsExactlyElementsOf(operations);
    assertThat(definition.operations().requiredPolicyObligations()).containsExactlyElementsOf(
      obligations
    );
    assertThat(definition.hierarchy().enabled()).isFalse();
    assertThat(definition.hierarchy().semantics()).isEmpty();
    assertThat(definition.hierarchy().rootModel()).isEmpty();
    assertThat(definition.hierarchy().supportedOperations()).isEmpty();
    assertThat(definition.lifecycle().enabled()).isFalse();
    assertThat(definition.lifecycle().states()).isEmpty();
    assertThat(definition.lifecycle().actions()).isEmpty();
    assertThat(definition.fieldsAndQueries().fields()).containsExactlyElementsOf(
      List.of(
        LogicalField.IDENTIFIER,
        LogicalField.NAME,
        LogicalField.SLUG,
        LogicalField.ACTIVE,
        LogicalField.CREATED_AT,
        LogicalField.UPDATED_AT
      )
    );
    assertThat(definition.fieldsAndQueries().searchableFields()).containsExactly(
      LogicalField.NAME,
      LogicalField.SLUG
    );
    assertThat(definition.fieldsAndQueries().allowedFilters()).containsExactly(
      LogicalField.SLUG,
      LogicalField.ACTIVE
    );
    assertThat(definition.fieldsAndQueries().allowedSorts()).containsExactly(
      LogicalField.IDENTIFIER,
      LogicalField.NAME,
      LogicalField.SLUG,
      LogicalField.ACTIVE,
      LogicalField.CREATED_AT,
      LogicalField.UPDATED_AT
    );
    assertThat(definition.operationalLimits().pageSize()).contains(1_000L);
    assertThat(definition.operationalLimits().searchInputLength()).contains(120L);
    assertThat(definition.operationalLimits().filterSelections()).contains(4L);
    assertThat(definition.operationalLimits().sortSelections()).contains(5L);
    assertThat(definition.operationalLimits().treeSize()).isEmpty();
    assertThat(definition.operationalLimits().parentOptionPageSize()).isEmpty();
    assertThat(definition.operationalLimits().includeIdCount()).isEmpty();
    assertThat(definition.operationalLimits().hierarchyDepth()).isEmpty();
  }
}
