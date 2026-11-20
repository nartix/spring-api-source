package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryDeletionPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryGeneralValidationPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryHierarchyPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryLifecyclePolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryMutationValidationPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryOrderingPolicy;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryQueryPolicy;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryApiProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierCodec;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping.Binding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyObligation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRegistrationValidationException;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimeBinding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimePorts;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.FieldQueryDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.HierarchyDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.HierarchySemantics;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LifecycleState;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalOrder;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OperationalLimits;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.OrderingDeclaration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.PlacementMode;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.RootModel;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.SortDirection;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistrationSource;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationCategory;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryValidationComponent;
import com.ferozfaiz.uniapi.category.application.registration.internal.AdminCategoryRegistrationFailures;
import com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc.policy.DescriptorAdminCategoryRuntimePolicies;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableOperations;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.FallbackOrderDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseRankOperations;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class DescriptorDrivenAdminCategoryJdbcProviderFactory
  implements AdminCategorySystemRegistrationSource
{

  private static final Logger log = LoggerFactory.getLogger(
    DescriptorDrivenAdminCategoryJdbcProviderFactory.class
  );

  private final List<AdminCategorySystemRegistration> registrations;

  public DescriptorDrivenAdminCategoryJdbcProviderFactory(
    AdminCategoryJdbcProviderProperties properties,
    CategorySystemRegistry categorySystems,
    NamedParameterJdbcTemplate jdbc,
    ClosureTableOperations closureTables,
    SparseRankOperations rankOperations,
    PlatformTransactionManager transactionManager,
    AdminCategoryIdentifierCodec<Long> identifierCodec,
    AdminCategoryGeneralValidationPolicy generalValidation,
    AdminCategoryQueryPolicy query,
    AdminCategoryHierarchyPolicy hierarchy,
    AdminCategoryMutationValidationPolicy mutationValidation,
    AdminCategoryOrderingPolicy ordering,
    AdminCategoryDeletionPolicy deletion,
    AdminCategoryLifecyclePolicy lifecycle
  ) {
    Objects.requireNonNull(properties, "properties");
    Objects.requireNonNull(categorySystems, "categorySystems");
    Objects.requireNonNull(jdbc, "jdbc");
    Objects.requireNonNull(closureTables, "closureTables");
    Objects.requireNonNull(rankOperations, "rankOperations");
    Objects.requireNonNull(transactionManager, "transactionManager");
    Objects.requireNonNull(identifierCodec, "identifierCodec");
    var policies = new DescriptorAdminCategoryRuntimePolicies(
      generalValidation,
      query,
      hierarchy,
      mutationValidation,
      ordering,
      deletion,
      lifecycle
    );
    this.registrations = buildRegistrations(
      properties,
      categorySystems,
      jdbc,
      closureTables,
      rankOperations,
      new TransactionTemplate(transactionManager),
      identifierCodec,
      policies
    );
  }

  @Override
  public List<AdminCategorySystemRegistration> registrations() {
    return registrations;
  }

  private static List<AdminCategorySystemRegistration> buildRegistrations(
    AdminCategoryJdbcProviderProperties properties,
    CategorySystemRegistry categorySystems,
    NamedParameterJdbcTemplate jdbc,
    ClosureTableOperations closureTables,
    SparseRankOperations rankOperations,
    TransactionTemplate transactions,
    AdminCategoryIdentifierCodec<Long> identifierCodec,
    DescriptorAdminCategoryRuntimePolicies policies
  ) {
    List<AdminCategorySystemRegistration> built = new ArrayList<>();
    for (String systemKey : properties.enabledSystemKeys()) {
      CategorySystemDescriptor categorySystem = categorySystems
        .findDescriptor(systemKey)
        .orElseThrow(() -> missingDescriptor(systemKey));
      AdminCategoryJdbcProviderProperties.SystemProperties systemProperties = properties
        .getSystems()
        .getOrDefault(systemKey, new AdminCategoryJdbcProviderProperties.SystemProperties());
      AdminCategoryNodeTableDescriptor node = nodeDescriptor(
        systemKey,
        categorySystem,
        systemProperties
      );
      DescriptorDrivenAdminCategoryJdbcProvider provider =
        new DescriptorDrivenAdminCategoryJdbcProvider(
          systemKey,
          categorySystem,
          node,
          jdbc,
          closureTables,
          rankOperations,
          transactions,
          new AdminCategoryParentOptionsCursorCodec()
        );
      AdminCategorySystemDefinition definition = definition(categorySystem);
      AdminCategoryRuntimePorts ports = new AdminCategoryRuntimePorts(
        Optional.of(provider),
        Optional.of(provider),
        Optional.of(provider),
        Optional.of(provider),
        Optional.of(provider),
        Optional.of(provider)
      );
      built.add(
        AdminCategorySystemRegistration.of(
          definition,
          new AdminCategoryRuntimeBinding(
            identifierCodec,
            permissions(systemKey),
            policies.bindings(),
            ports
          )
        )
      );
      log
        .atInfo()
        .addKeyValue("category.event", "descriptor_provider.enabled")
        .addKeyValue("systemKey", systemKey)
        .addKeyValue("outcome", "enabled")
        .log("descriptor-backed category provider enabled");
    }
    if (built.isEmpty()) {
      log
        .atInfo()
        .addKeyValue("category.event", "descriptor_provider.disabled")
        .addKeyValue("outcome", "disabled")
        .log("descriptor-backed category providers disabled");
    }
    return List.copyOf(built);
  }

  private static AdminCategoryRegistrationValidationException missingDescriptor(String systemKey) {
    log
      .atError()
      .addKeyValue("category.event", "descriptor_provider.missing_descriptor")
      .addKeyValue("systemKey", systemKey)
      .addKeyValue("outcome", "rejected")
      .log("descriptor-backed category provider descriptor missing");
    return AdminCategoryRegistrationFailures.failure(
      systemKey,
      AdminCategoryValidationCategory.MISSING_COMPONENT,
      AdminCategoryValidationComponent.REGISTRATION_SOURCE
    );
  }

  private static AdminCategorySystemDefinition definition(CategorySystemDescriptor categorySystem) {
    if (
      !categorySystem.ranking().rankingEnabled() ||
      !categorySystem.ranking().sortRankExposed() ||
      categorySystem.ranking().placementModes().size() != PlacementMode.values().length
    ) {
      throw invalidConfiguration(categorySystem.systemKey());
    }
    List<LogicalOrder> fallbackOrder = categorySystem
      .fallbackOrder()
      .orders()
      .stream()
      .map(DescriptorDrivenAdminCategoryJdbcProviderFactory::logicalOrder)
      .toList();
    return new AdminCategorySystemDefinition(
      categorySystem.systemKey(),
      categorySystem.owningModule(),
      AdminCategoryApiProfile.STANDARD_CATEGORY_V1_NUMERIC,
      AdminCategoryIdentifierProfile.NUMERIC_LONG_V1,
      new OperationDeclaration(
        List.of(Operation.values()),
        List.of(AdminCategoryPolicyObligation.values())
      ),
      new HierarchyDeclaration(
        true,
        Optional.of(HierarchySemantics.PARENT_CHILD_TREE),
        Optional.of(RootModel.IMPLICIT_NULL_PARENT),
        List.of(Operation.TREE, Operation.PARENT_OPTIONS, Operation.BREADCRUMB, Operation.MOVE)
      ),
      new OrderingDeclaration(
        true,
        true,
        true,
        true,
        List.of(PlacementMode.values()),
        fallbackOrder
      ),
      new LifecycleDeclaration(
        true,
        List.of(LifecycleState.ACTIVE, LifecycleState.INACTIVE),
        List.of(Operation.ACTIVATE, Operation.DEACTIVATE)
      ),
      new FieldQueryDeclaration(
        List.of(LogicalField.values()),
        List.of(LogicalField.NAME, LogicalField.SLUG),
        List.of(LogicalField.PARENT, LogicalField.SLUG, LogicalField.ACTIVE),
        List.of(
          LogicalField.IDENTIFIER,
          LogicalField.PARENT,
          LogicalField.NAME,
          LogicalField.SLUG,
          LogicalField.ACTIVE,
          LogicalField.CREATED_AT,
          LogicalField.UPDATED_AT,
          LogicalField.RANK
        )
      ),
      new OperationalLimits(
        Optional.of(1_000L),
        Optional.of(120L),
        Optional.of(4L),
        Optional.of(5L),
        Optional.of(10_000L),
        Optional.of(50L),
        Optional.of(25L),
        Optional.of((long) Integer.MAX_VALUE)
      )
    );
  }

  private static LogicalOrder logicalOrder(FallbackOrderDescriptor.Order order) {
    LogicalField field = switch (order.property()) {
      case "id" -> LogicalField.IDENTIFIER;
      case "parentId" -> LogicalField.PARENT;
      case "name" -> LogicalField.NAME;
      case "slug" -> LogicalField.SLUG;
      case "active" -> LogicalField.ACTIVE;
      case "createdAt" -> LogicalField.CREATED_AT;
      case "updatedAt" -> LogicalField.UPDATED_AT;
      case "sortRank" -> LogicalField.RANK;
      default -> throw invalidConfiguration("<unavailable>");
    };
    SortDirection direction =
      order.direction() == FallbackOrderDescriptor.Direction.ASC
        ? SortDirection.ASC
        : SortDirection.DESC;
    return new LogicalOrder(field, direction);
  }

  private static AdminCategoryPermissionMapping permissions(String systemKey) {
    List<Binding> bindings = new ArrayList<>();
    for (Operation operation : Operation.values()) {
      String access = operation.ordinal() <= Operation.BREADCRUMB.ordinal() ? "read" : "write";
      bindings.add(new Binding(operation, "admin:" + systemKey + "-categories:" + access));
    }
    return new AdminCategoryPermissionMapping(bindings);
  }

  private static AdminCategoryNodeTableDescriptor nodeDescriptor(
    String systemKey,
    CategorySystemDescriptor categorySystem,
    AdminCategoryJdbcProviderProperties.SystemProperties systemProperties
  ) {
    try {
      return AdminCategoryNodeTableDescriptor.from(categorySystem, systemProperties);
    } catch (IllegalArgumentException exception) {
      throw invalidConfiguration(systemKey);
    }
  }

  private static AdminCategoryRegistrationValidationException invalidConfiguration(
    String systemKey
  ) {
    log
      .atError()
      .addKeyValue("category.event", "descriptor_provider.invalid_configuration")
      .addKeyValue("systemKey", systemKey)
      .addKeyValue("outcome", "rejected")
      .log("descriptor-backed category provider configuration rejected");
    return AdminCategoryRegistrationFailures.failure(
      systemKey,
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      AdminCategoryValidationComponent.REGISTRATION_SOURCE
    );
  }
}
