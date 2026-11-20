package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryCreateCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryMoveCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryPatchCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryReorderCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryUpdateCommand;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryCreateValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryParentOptionsValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPatchValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPlacementValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryReplaceValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryTreeValidationInput;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryBasicMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyReadPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryLifecyclePort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOrderingPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryTreeQuery;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimeBinding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimePorts;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryContractAssertions;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContract;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractFixture;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.Applicability;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.InteractionExpectation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.OperationExpectation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.PortSlot;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.QueryCountExpectation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.SeedRecord;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.StableErrorExpectation;
import com.ferozfaiz.uniapi.category.support.log.StructuredLogCapture;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Applies the shared provider contract to the production descriptor-backed JDBC adapter. */
@SpringBootTest(
  properties = {
    "app.category.admin.jdbc.enabled-systems[0]=descriptor-a",
    "app.category.admin.jdbc.systems.descriptor-a.name-column=name",
    "app.category.admin.jdbc.systems.descriptor-a.slug-column=slug",
    "app.category.admin.jdbc.systems.descriptor-a.active-column=active",
    "app.category.admin.jdbc.systems.descriptor-a.created-at-column=created_at",
    "app.category.admin.jdbc.systems.descriptor-a.updated-at-column=updated_at",
    "uniapi.category-systems.descriptor-a.owning-module=category",
    "uniapi.category-systems.descriptor-a.node-table=test_category_a",
    "uniapi.category-systems.descriptor-a.closure-table=test_category_a_closure",
    "uniapi.category-systems.descriptor-a.ranking.enabled=true",
    "uniapi.category-systems.descriptor-a.ranking.column-name=sort_rank",
    "uniapi.category-systems.descriptor-a.ranking.placement-modes[0]=APPEND",
    "uniapi.category-systems.descriptor-a.ranking.placement-modes[1]=BEFORE_ID",
    "uniapi.category-systems.descriptor-a.ranking.placement-modes[2]=AFTER_ID",
    "uniapi.category-systems.descriptor-a.ranking.placement-modes[3]=TARGET_INDEX",
    "uniapi.category-systems.descriptor-a.ranking.sort-rank-exposed=true",
    "uniapi.category-systems.descriptor-a.fallback-order[0].property=name",
    "uniapi.category-systems.descriptor-a.fallback-order[0].column-name=name",
    "uniapi.category-systems.descriptor-a.fallback-order[0].direction=ASC",
    "uniapi.category-systems.descriptor-a.fallback-order[1].property=id",
    "uniapi.category-systems.descriptor-a.fallback-order[1].column-name=id",
    "uniapi.category-systems.descriptor-a.fallback-order[1].direction=ASC",
  }
)
final class DescriptorAdminCategoryProviderContractTest implements AdminCategoryProviderContract {

  @Autowired
  private DescriptorDrivenAdminCategoryJdbcProviderFactory factory;

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Override
  public AdminCategoryProviderContractProfile profile() {
    return DescriptorContractFixture.profile(factoryRegistration());
  }

  @Override
  public AdminCategoryProviderContractFixture newFixture() {
    return new DescriptorContractFixture(factoryRegistration(), jdbc, transactionManager);
  }

  private AdminCategorySystemRegistration factoryRegistration() {
    return factory
      .registrations()
      .stream()
      .filter(registration -> registration.systemKey().filter("descriptor-a"::equals).isPresent())
      .findFirst()
      .orElseThrow();
  }

  private static final class DescriptorContractFixture
    implements AdminCategoryProviderContractFixture
  {

    private static final Object DATABASE_MONITOR = new Object();
    private static final String SYSTEM_KEY = "descriptor-a";
    private static final long SELECTED_ID = 11L;
    private static final Set<String> FORBIDDEN_INTERACTIONS = Set.of(
      "registry.wrong-key",
      "registry.enumeration",
      "registry.extra-lookup",
      "registry.unexpected-method",
      "provider.systemKey",
      "provider.get",
      "provider.fallback"
    );
    private static final List<SeedRecord> SEEDS = List.of(
      seed(10L, null, 1_000_000_000L, "Storage", "storage", true),
      seed(11L, 10L, 1_000_000_000L, "Boxes", "boxes", true),
      seed(12L, null, 2_000_000_000L, "Supplies", "supplies", false)
    );

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final RecordingProvider provider;
    private final AdminCategorySystemRegistration registration;
    private final AdminCategorySystemRegistry registry;
    private final AdminCategoryApplicationService applicationService;
    private final AuthorizationState authorization = new AuthorizationState();
    private final List<String> calls = new ArrayList<>();
    private final List<String> providerForbiddenInteractions = new ArrayList<>();

    private DatabaseState ownedState = DatabaseState.empty();
    private OutputObservation output = emptyOutput();
    private int commits;
    private int rollbacks;
    private boolean activeDuringProviderInvocation;

    private DescriptorContractFixture(
      AdminCategorySystemRegistration factoryRegistration,
      JdbcTemplate jdbc,
      PlatformTransactionManager transactionManager
    ) {
      this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
      this.transactions = new TransactionTemplate(
        Objects.requireNonNull(transactionManager, "transactionManager")
      );
      AdminCategoryRuntimeBinding factoryBinding = factoryRegistration
        .runtimeBinding()
        .orElseThrow();
      this.provider = new RecordingProvider(
        factoryBinding
          .ports()
          .categoryRead()
          .filter(DescriptorDrivenAdminCategoryJdbcProvider.class::isInstance)
          .map(DescriptorDrivenAdminCategoryJdbcProvider.class::cast)
          .orElseThrow()
      );
      AdminCategoryRuntimePorts recordingPorts = new AdminCategoryRuntimePorts(
        Optional.of(provider),
        Optional.of(provider),
        Optional.of(provider),
        Optional.of(provider),
        Optional.of(provider),
        Optional.of(provider)
      );
      AdminCategoryRuntimeBinding recordingBinding = new AdminCategoryRuntimeBinding(
        factoryBinding.identifierCodec(),
        factoryBinding.permissionMapping(),
        factoryBinding.policyBindings(),
        recordingPorts
      );
      this.registration = AdminCategorySystemRegistration.of(
        factoryRegistration.definition().orElseThrow(),
        recordingBinding
      );
      this.registry = registry(registration);
      this.applicationService = new AdminCategoryApplicationService(registry);
      synchronized (DATABASE_MONITOR) {
        DescriptorBackedAdminCategoryJdbcFixture.createSchema(jdbc);
      }
    }

    private static AdminCategoryProviderContractProfile profile(
      AdminCategorySystemRegistration factoryRegistration
    ) {
      AdminCategorySystemDefinition definition = factoryRegistration.definition().orElseThrow();
      Set<Operation> operations = Set.copyOf(EnumSet.allOf(Operation.class));
      Map<Operation, PortSlot> ports = new EnumMap<>(Operation.class);
      Map<Operation, OperationExpectation> expectations = new EnumMap<>(Operation.class);
      Map<Operation, Applicability> rollback = new EnumMap<>(Operation.class);
      for (Operation operation : Operation.values()) {
        ports.put(operation, port(operation));
        expectations.put(
          operation,
          new OperationExpectation(
            "V(" + operation + ")",
            successResult(operation),
            AdminCategoryContractAssertions.NO_STABLE_ERROR,
            logOutcome(operation),
            new Applicability(true, "descriptor-a declares and implements " + operation.code())
          )
        );
        rollback.put(
          operation,
          new Applicability(
            isMutation(operation),
            isMutation(operation)
              ? "descriptor-a mutation joins the real outer JDBC transaction"
              : "descriptor-a read performs no mutation to roll back"
          )
        );
      }
      return new AdminCategoryProviderContractProfile(
        SYSTEM_KEY,
        definition,
        () -> factoryRegistration,
        operations,
        ports,
        SEEDS,
        List.of("10", "11", "12"),
        expectations,
        new StableErrorExpectation(
          "CATEGORY_NOT_FOUND",
          "category was not found",
          "descriptor-a maps an absent JDBC node to the stable category error"
        ),
        rollback,
        new InteractionExpectation(
          operations
            .stream()
            .map(AdminCategoryProviderContractProfile::canonicalCall)
            .flatMap(Optional::stream)
            .collect(java.util.stream.Collectors.toUnmodifiableSet()),
          FORBIDDEN_INTERACTIONS
        ),
        List.of(
          new QueryCountExpectation(
            "descriptor-query-count-owned-by-t039",
            1,
            25,
            0,
            new Applicability(
              false,
              "T039 owns descriptor SQL statement-count instrumentation and cardinality evidence"
            )
          )
        )
      );
    }

    @Override
    public AdminCategorySystemRegistry registry() {
      return registry;
    }

    @Override
    public AdminCategoryApplicationService applicationService() {
      return applicationService;
    }

    @Override
    public AdminCategorySystemRegistration registration() {
      return registration;
    }

    @Override
    public AdminCategoryRuntimePorts ports() {
      return registration.runtimeBinding().orElseThrow().ports();
    }

    @Override
    public AuthorizationFixture authorization() {
      return authorization;
    }

    @Override
    public void reset() {
      synchronized (DATABASE_MONITOR) {
        truncate();
        ownedState = DatabaseState.empty();
      }
      authorization.authenticated(
        Set.of("admin:descriptor-a-categories:read", "admin:descriptor-a-categories:write")
      );
      clearObservations();
    }

    @Override
    public void seed(List<SeedRecord> records) {
      synchronized (DATABASE_MONITOR) {
        truncate();
        List<SeedRecord> selected = List.copyOf(records);
        for (SeedRecord record : selected) {
          Map<String, String> attributes = record.attributes();
          jdbc.update(
            """
            INSERT INTO test_category_a
              (id, parent_id, sort_rank, name, slug, active, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            Long.valueOf(record.recordId()),
            nullableLong(attributes.get("parentId")),
            Long.valueOf(required(attributes, "sortRank")),
            required(attributes, "name"),
            required(attributes, "slug"),
            Boolean.valueOf(required(attributes, "active")),
            OffsetDateTime.parse(required(attributes, "createdAt")),
            OffsetDateTime.parse(required(attributes, "updatedAt"))
          );
        }
        insertClosureRows(selected);
        ownedState = captureDatabase();
      }
      clearObservations();
    }

    @Override
    public OperationObservation invoke(Operation operation) {
      Objects.requireNonNull(operation, "operation");
      synchronized (DATABASE_MONITOR) {
        restoreOwnedState();
        beginInvocation();
        InvocationResult result = null;
        Optional<String> stableError = Optional.empty();
        String publicMessage = "";
        try (
          StructuredLogCapture capture = StructuredLogCapture.attach(
            AdminCategoryApplicationService.class
          )
        ) {
          try {
            result = invokeInTransaction(operation);
          } catch (AdminCategoryException failure) {
            stableError = Optional.of(failure.code());
            publicMessage = Objects.requireNonNullElse(failure.getMessage(), "none");
            result = new InvocationResult(
              failure.status().value() + " " + failure.code(),
              new StateSnapshot(
                Map.of(
                  "errorCode",
                  failure.code(),
                  "status",
                  Integer.toString(failure.status().value()),
                  "message",
                  publicMessage
                )
              )
            );
          } finally {
            ownedState = captureDatabase();
            output = output(result, stableError, publicMessage, capture.events());
          }
        }
        return new OperationObservation(
          result.resultId(),
          stableError,
          result.publicResult(),
          output
        );
      }
    }

    @Override
    public OperationObservation invokeThenFailAfterWrite(Operation operation) {
      if (!isMutation(operation)) {
        throw new IllegalArgumentException("descriptor rollback applies only to mutations");
      }
      synchronized (DATABASE_MONITOR) {
        restoreOwnedState();
        beginInvocation();
        try (
          StructuredLogCapture capture = StructuredLogCapture.attach(
            AdminCategoryApplicationService.class
          )
        ) {
          try {
            transactions.executeWithoutResult(ignored -> {
              observeTransactionCompletion();
              invokeApplication(operation);
              throw new ControlledOuterFailure();
            });
            throw new AssertionError("controlled outer failure was not raised");
          } catch (ControlledOuterFailure expected) {
            ownedState = captureDatabase();
            Optional<String> stableError = Optional.of("CONTROLLED_OUTER_FAILURE");
            InvocationResult result = new InvocationResult("rollback", snapshot(ownedState));
            output = output(result, stableError, "controlled outer failure", capture.events());
            return new OperationObservation("rollback", stableError, result.publicResult(), output);
          }
        }
      }
    }

    @Override
    public StateSnapshot snapshot() {
      return liveStateSnapshot();
    }

    @Override
    public InteractionLedger interactions() {
      return new InteractionLedger(List.copyOf(calls), observedForbiddenInteractions());
    }

    @Override
    public TransactionObservation transactionObservation() {
      return new TransactionObservation(commits, rollbacks, activeDuringProviderInvocation);
    }

    @Override
    public OutputObservation outputObservation() {
      return output;
    }

    @Override
    public Optional<QueryObservation> queryObservation() {
      return Optional.empty();
    }

    @Override
    public StateSnapshot providerState() {
      return liveStateSnapshot();
    }

    private InvocationResult invokeInTransaction(Operation operation) {
      if (operation == Operation.CAPABILITIES) {
        return invokeApplication(operation);
      }
      return transactions.execute(ignored -> {
        observeTransactionCompletion();
        return invokeApplication(operation);
      });
    }

    private InvocationResult invokeApplication(Operation operation) {
      AdminCategorySystemDefinition definition = registration.definition().orElseThrow();
      return switch (operation) {
        case CAPABILITIES -> capabilities(applicationService.capabilities(SYSTEM_KEY));
        case LIST -> page(Operation.LIST, applicationService.list(SYSTEM_KEY, list(definition)));
        case TREE -> page(Operation.TREE, applicationService.tree(SYSTEM_KEY, tree(definition)));
        case PARENT_OPTIONS -> parentOptions(
          applicationService.parentOptions(SYSTEM_KEY, parentOptions(definition))
        );
        case GET -> category(
          Operation.GET,
          applicationService.getNode(SYSTEM_KEY, SELECTED_ID).category()
        );
        case BREADCRUMB -> breadcrumb(applicationService.breadcrumb(SYSTEM_KEY, SELECTED_ID));
        case CREATE -> category(
          Operation.CREATE,
          applicationService.create(SYSTEM_KEY, create(definition))
        );
        case REPLACE -> category(
          Operation.REPLACE,
          applicationService.update(SYSTEM_KEY, SELECTED_ID, replace(definition))
        );
        case PATCH -> category(
          Operation.PATCH,
          applicationService.patch(SYSTEM_KEY, SELECTED_ID, patch(definition))
        );
        case DELETE -> {
          applicationService.delete(SYSTEM_KEY, SELECTED_ID);
          yield status(Operation.DELETE);
        }
        case MOVE -> {
          applicationService.move(SYSTEM_KEY, SELECTED_ID, placement(definition, Operation.MOVE));
          yield status(Operation.MOVE);
        }
        case REORDER -> category(
          Operation.REORDER,
          applicationService.reorder(
            SYSTEM_KEY,
            SELECTED_ID,
            placement(definition, Operation.REORDER)
          )
        );
        case ACTIVATE -> {
          applicationService.activate(SYSTEM_KEY, SELECTED_ID);
          yield status(Operation.ACTIVATE);
        }
        case DEACTIVATE -> {
          applicationService.deactivate(SYSTEM_KEY, SELECTED_ID);
          yield status(Operation.DEACTIVATE);
        }
      };
    }

    private void observeTransactionCompletion() {
      TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
              commits++;
            } else if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
              rollbacks++;
            }
          }
        }
      );
    }

    private void beginInvocation() {
      clearObservations();
    }

    private void clearObservations() {
      calls.clear();
      providerForbiddenInteractions.clear();
      clearInvocations(registry);
      commits = 0;
      rollbacks = 0;
      activeDuringProviderInvocation = false;
      output = emptyOutput();
    }

    private StateSnapshot liveStateSnapshot() {
      synchronized (DATABASE_MONITOR) {
        restoreOwnedState();
        DatabaseState liveState = captureDatabase();
        ownedState = liveState;
        return snapshot(liveState);
      }
    }

    private List<String> observedForbiddenInteractions() {
      List<String> observed = new ArrayList<>(providerForbiddenInteractions);
      int selectedLookups = 0;
      for (org.mockito.invocation.Invocation invocation : mockingDetails(
        registry
      ).getInvocations()) {
        String method = invocation.getMethod().getName();
        if (method.equals("findRegistration")) {
          Object rawKey = invocation.getArgument(0);
          if (!SYSTEM_KEY.equals(rawKey)) {
            observed.add("registry.wrong-key");
          } else if (++selectedLookups > 1) {
            observed.add("registry.extra-lookup");
          }
        } else if (method.equals("registrations")) {
          observed.add("registry.enumeration");
        } else if (!method.equals("toString")) {
          observed.add("registry.unexpected-method");
        }
      }
      return List.copyOf(observed);
    }

    private void truncate() {
      jdbc.execute("TRUNCATE TABLE test_category_a_closure, test_category_a RESTART IDENTITY");
    }

    private void restoreOwnedState() {
      truncate();
      for (NodeRow node : ownedState.nodes()) {
        jdbc.update(
          """
          INSERT INTO test_category_a
            (id, parent_id, sort_rank, name, slug, active, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          """,
          node.id(),
          node.parentId(),
          node.sortRank(),
          node.name(),
          node.slug(),
          node.active(),
          node.createdAt(),
          node.updatedAt()
        );
      }
      for (ClosureRow closure : ownedState.closure()) {
        DescriptorBackedAdminCategoryJdbcFixture.insertClosure(
          jdbc,
          DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_A,
          closure.ancestorId(),
          closure.descendantId(),
          closure.depth()
        );
      }
    }

    private DatabaseState captureDatabase() {
      List<NodeRow> nodes = jdbc.query(
        """
        SELECT id, parent_id, sort_rank, name, slug, active, created_at, updated_at
        FROM test_category_a
        ORDER BY id ASC
        """,
        (resultSet, rowNumber) ->
          new NodeRow(
            resultSet.getLong("id"),
            resultSet.getObject("parent_id", Long.class),
            resultSet.getLong("sort_rank"),
            resultSet.getString("name"),
            resultSet.getString("slug"),
            resultSet.getBoolean("active"),
            resultSet.getObject("created_at", OffsetDateTime.class),
            resultSet.getObject("updated_at", OffsetDateTime.class)
          )
      );
      List<ClosureRow> closure = jdbc.query(
        """
        SELECT ancestor_id, descendant_id, depth
        FROM test_category_a_closure
        ORDER BY ancestor_id ASC, descendant_id ASC
        """,
        (resultSet, rowNumber) ->
          new ClosureRow(
            resultSet.getLong("ancestor_id"),
            resultSet.getLong("descendant_id"),
            resultSet.getInt("depth")
          )
      );
      return new DatabaseState(nodes, closure);
    }

    private void insertClosureRows(List<SeedRecord> records) {
      Map<Long, Long> parents = new java.util.LinkedHashMap<>();
      for (SeedRecord record : records) {
        Long id = Long.valueOf(record.recordId());
        if (parents.containsKey(id)) {
          throw new IllegalArgumentException("descriptor seed identifiers must be unique");
        }
        parents.put(id, nullableLong(record.attributes().get("parentId")));
      }
      for (Long descendant : parents.keySet()) {
        DescriptorBackedAdminCategoryJdbcFixture.insertClosure(
          jdbc,
          DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_A,
          descendant,
          descendant,
          0
        );
        Long ancestor = parents.get(descendant);
        int depth = 1;
        while (ancestor != null) {
          DescriptorBackedAdminCategoryJdbcFixture.insertClosure(
            jdbc,
            DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_A,
            ancestor,
            descendant,
            depth++
          );
          ancestor = parents.get(ancestor);
        }
      }
    }

    private static StateSnapshot snapshot(DatabaseState state) {
      String nodes = state
        .nodes()
        .stream()
        .map(NodeRow::stableValue)
        .collect(java.util.stream.Collectors.joining(","));
      String closure = state
        .closure()
        .stream()
        .map(ClosureRow::stableValue)
        .collect(java.util.stream.Collectors.joining(","));
      return new StateSnapshot(
        Map.of(
          "rowCount",
          Integer.toString(state.nodes().size()),
          "rows",
          nodes,
          "closureRowCount",
          Integer.toString(state.closure().size()),
          "closureRows",
          closure
        )
      );
    }

    private static InvocationResult capabilities(AdminCategorySystemDefinition definition) {
      return new InvocationResult(
        successResult(Operation.CAPABILITIES),
        new StateSnapshot(
          Map.of(
            "systemKey",
            definition.systemKey(),
            "operations",
            definition
              .operations()
              .supportedOperations()
              .stream()
              .sorted(Comparator.comparingInt(Enum::ordinal))
              .map(Operation::code)
              .collect(java.util.stream.Collectors.joining(","))
          )
        )
      );
    }

    private static InvocationResult page(
      Operation operation,
      PageResult<AdminCategoryModels.CategoryNode> page
    ) {
      return new InvocationResult(
        successResult(operation),
        new StateSnapshot(
          Map.of(
            "ids",
            page
              .content()
              .stream()
              .map(node -> node.category().id().toString())
              .collect(java.util.stream.Collectors.joining(",")),
            "totalElements",
            Long.toString(page.totalElements()),
            "page",
            Integer.toString(page.page()),
            "size",
            Integer.toString(page.size())
          )
        )
      );
    }

    private static InvocationResult parentOptions(AdminCategoryModels.ParentOptionSlice slice) {
      return new InvocationResult(
        successResult(Operation.PARENT_OPTIONS),
        new StateSnapshot(
          Map.of(
            "ids",
            slice
              .data()
              .stream()
              .map(option -> option.category().id().toString())
              .collect(java.util.stream.Collectors.joining(",")),
            "hasMore",
            Boolean.toString(slice.hasMore()),
            "nextCursor",
            Objects.requireNonNullElse(slice.nextCursor(), "none")
          )
        )
      );
    }

    private static InvocationResult breadcrumb(List<AdminCategoryModels.BreadcrumbItem> items) {
      return new InvocationResult(
        successResult(Operation.BREADCRUMB),
        new StateSnapshot(
          Map.of(
            "ids",
            items
              .stream()
              .map(item -> item.category().id().toString())
              .collect(java.util.stream.Collectors.joining(",")),
            "depths",
            items
              .stream()
              .map(item -> Integer.toString(item.depth()))
              .collect(java.util.stream.Collectors.joining(","))
          )
        )
      );
    }

    private static InvocationResult category(
      Operation operation,
      AdminCategoryModels.Category category
    ) {
      return new InvocationResult(successResult(operation), categorySnapshot(category));
    }

    private static InvocationResult status(Operation operation) {
      return new InvocationResult(
        successResult(operation),
        new StateSnapshot(Map.of("operation", operation.code(), "status", "completed"))
      );
    }

    private static StateSnapshot categorySnapshot(AdminCategoryModels.Category category) {
      return new StateSnapshot(
        Map.of(
          "id",
          category.id().toString(),
          "parentId",
          Objects.toString(category.parentId(), "root"),
          "sortRank",
          Objects.toString(category.sortRank(), "unranked"),
          "name",
          category.name(),
          "slug",
          category.slug(),
          "active",
          Boolean.toString(category.active()),
          "createdAt",
          category.createdAt().toString(),
          "updatedAt",
          category.updatedAt().toString()
        )
      );
    }

    private static OutputObservation output(
      InvocationResult result,
      Optional<String> stableError,
      String publicMessage,
      List<StructuredLogCapture.CapturedEvent> events
    ) {
      List<String> publicValues = new ArrayList<>();
      if (result != null) {
        publicValues.add(result.resultId());
        publicValues.addAll(result.publicResult().facts().values());
      }
      stableError.ifPresent(publicValues::add);
      if (!publicMessage.isBlank()) {
        publicValues.add(publicMessage);
      }
      List<String> logValues = events
        .stream()
        .flatMap(event -> event.searchableSurfaces().stream())
        .filter(value -> value != null && !value.isBlank())
        .toList();
      List<String> causes = events
        .stream()
        .flatMap(event -> event.attachedThrowable().stream())
        .flatMap(throwable ->
          java.util.stream.Stream.of(throwable.className(), throwable.message())
        )
        .filter(value -> value != null && !value.isBlank())
        .toList();
      return new OutputObservation(publicValues, logValues, causes);
    }

    private static AdminCategoryListValidationInput list(AdminCategorySystemDefinition definition) {
      return new AdminCategoryListValidationInput(
        definition,
        0,
        100,
        null,
        null,
        null,
        null,
        List.of(),
        null
      );
    }

    private static AdminCategoryTreeValidationInput tree(AdminCategorySystemDefinition definition) {
      return new AdminCategoryTreeValidationInput(definition, 0, 100, List.of(), null);
    }

    private static AdminCategoryParentOptionsValidationInput parentOptions(
      AdminCategorySystemDefinition definition
    ) {
      return new AdminCategoryParentOptionsValidationInput(
        definition,
        null,
        25,
        null,
        List.of(),
        null,
        "true"
      );
    }

    private static AdminCategoryCreateValidationInput create(
      AdminCategorySystemDefinition definition
    ) {
      return new AdminCategoryCreateValidationInput(
        definition,
        null,
        null,
        null,
        null,
        "Created",
        "created",
        true
      );
    }

    private static AdminCategoryReplaceValidationInput replace(
      AdminCategorySystemDefinition definition
    ) {
      return new AdminCategoryReplaceValidationInput(
        definition,
        SELECTED_ID,
        10L,
        "Replaced Boxes",
        "replaced-boxes",
        true
      );
    }

    private static AdminCategoryPatchValidationInput patch(
      AdminCategorySystemDefinition definition
    ) {
      return new AdminCategoryPatchValidationInput(
        definition,
        SELECTED_ID,
        null,
        false,
        "Patched Boxes",
        true,
        null,
        false,
        null,
        false
      );
    }

    private static AdminCategoryPlacementValidationInput placement(
      AdminCategorySystemDefinition definition,
      Operation operation
    ) {
      return new AdminCategoryPlacementValidationInput(
        definition,
        operation,
        operation == Operation.REORDER ? 10L : null,
        null,
        null,
        operation == Operation.REORDER ? 0 : null
      );
    }

    private static AdminCategorySystemRegistry registry(
      AdminCategorySystemRegistration registration
    ) {
      AdminCategorySystemRegistry registry = mock(AdminCategorySystemRegistry.class);
      when(registry.findRegistration(SYSTEM_KEY)).thenReturn(Optional.of(registration));
      when(registry.registrations()).thenReturn(List.of(registration));
      return registry;
    }

    private static PortSlot port(Operation operation) {
      return switch (operation) {
        case CAPABILITIES -> PortSlot.NONE;
        case LIST, GET -> PortSlot.CATEGORY_READ;
        case TREE, PARENT_OPTIONS, BREADCRUMB -> PortSlot.HIERARCHY_READ;
        case CREATE, REPLACE, PATCH, DELETE -> PortSlot.BASIC_MUTATION;
        case MOVE -> PortSlot.HIERARCHY_MUTATION;
        case REORDER -> PortSlot.ORDERING;
        case ACTIVATE, DEACTIVATE -> PortSlot.LIFECYCLE;
      };
    }

    private static String successResult(Operation operation) {
      return switch (operation) {
        case CAPABILITIES -> "200 AdminCategoryCapabilityResponse";
        case LIST -> "200 PageResponse<AdminCategoryListItemResponse>";
        case TREE -> "200 PageResponse<AdminCategoryTreeItemResponse>";
        case PARENT_OPTIONS -> "200 AdminCategoryParentOptionSliceResponse";
        case GET -> "200 AdminCategoryDetailResponse";
        case BREADCRUMB -> "200 root-to-selected breadcrumb array";
        case CREATE -> "201 AdminCategoryDetailResponse plus absolute Location";
        case REPLACE, PATCH, REORDER -> "200 AdminCategoryDetailResponse";
        case DELETE, MOVE, ACTIVATE, DEACTIVATE -> "204 empty body";
      };
    }

    private static String logOutcome(Operation operation) {
      return switch (operation) {
        case CREATE -> "category.create.succeeded";
        case REPLACE -> "category.update.succeeded";
        case PATCH -> "category.patch.succeeded";
        case DELETE -> "category.delete.succeeded";
        case MOVE -> "category.move.succeeded";
        case REORDER -> "category.reorder.succeeded";
        case ACTIVATE -> "category.activate.succeeded";
        case DEACTIVATE -> "category.deactivate.succeeded";
        default -> AdminCategoryContractAssertions.NO_LOG_OUTCOME;
      };
    }

    private static boolean isMutation(Operation operation) {
      return operation.ordinal() >= Operation.CREATE.ordinal();
    }

    private static SeedRecord seed(
      Long id,
      Long parentId,
      Long sortRank,
      String name,
      String slug,
      boolean active
    ) {
      return new SeedRecord(
        id.toString(),
        Map.of(
          "parentId",
          Objects.toString(parentId, "root"),
          "sortRank",
          sortRank.toString(),
          "name",
          name,
          "slug",
          slug,
          "active",
          Boolean.toString(active),
          "createdAt",
          "2026-07-01T00:00:00Z",
          "updatedAt",
          "2026-07-16T12:00:00Z"
        )
      );
    }

    private static Long nullableLong(String value) {
      return value == null || value.equals("root") ? null : Long.valueOf(value);
    }

    private static String required(Map<String, String> attributes, String key) {
      return Objects.requireNonNull(
        attributes.get(key),
        "missing descriptor seed attribute " + key
      );
    }

    private static OutputObservation emptyOutput() {
      return new OutputObservation(List.of(), List.of(), List.of());
    }

    private final class RecordingProvider
      implements
        AdminCategoryReadPort,
        AdminCategoryHierarchyReadPort,
        AdminCategoryBasicMutationPort,
        AdminCategoryHierarchyMutationPort,
        AdminCategoryOrderingPort,
        AdminCategoryLifecyclePort
    {

      private final DescriptorDrivenAdminCategoryJdbcProvider delegate;

      private RecordingProvider(DescriptorDrivenAdminCategoryJdbcProvider delegate) {
        this.delegate = delegate;
      }

      @Override
      public PageResult<AdminCategoryModels.CategoryNode> list(AdminCategoryListQuery query) {
        record("category-read.list");
        return delegate.list(query);
      }

      @Override
      public PageResult<AdminCategoryModels.CategoryNode> tree(AdminCategoryTreeQuery query) {
        record("hierarchy-read.tree");
        return delegate.tree(query);
      }

      @Override
      public AdminCategoryModels.CategoryNode getNode(Long id) {
        record("category-read.getNode");
        return delegate.getNode(id);
      }

      @Override
      public AdminCategoryModels.Category get(Long id) {
        providerForbiddenInteractions.add("provider.get");
        providerForbiddenInteractions.add("provider.fallback");
        return delegate.get(id);
      }

      @Override
      public List<AdminCategoryModels.BreadcrumbItem> breadcrumb(Long id) {
        record("hierarchy-read.breadcrumb");
        return delegate.breadcrumb(id);
      }

      @Override
      public AdminCategoryModels.ParentOptionSlice parentOptions(
        AdminCategoryParentOptionsQuery query
      ) {
        record("hierarchy-read.parentOptions");
        return delegate.parentOptions(query);
      }

      @Override
      public AdminCategoryModels.Category create(AdminCategoryCreateCommand command) {
        record("basic-mutation.create");
        return delegate.create(command);
      }

      @Override
      public AdminCategoryModels.Category replace(Long id, AdminCategoryUpdateCommand command) {
        record("basic-mutation.replace");
        return delegate.replace(id, command);
      }

      @Override
      public AdminCategoryModels.Category patch(Long id, AdminCategoryPatchCommand command) {
        record("basic-mutation.patch");
        return delegate.patch(id, command);
      }

      @Override
      public void move(Long id, AdminCategoryMoveCommand command) {
        record("hierarchy-mutation.move");
        delegate.move(id, command);
      }

      @Override
      public AdminCategoryModels.Category reorder(Long id, AdminCategoryReorderCommand command) {
        record("ordering.reorder");
        return delegate.reorder(id, command);
      }

      @Override
      public void activate(Long id) {
        record("lifecycle.activate");
        delegate.activate(id);
      }

      @Override
      public void deactivate(Long id) {
        record("lifecycle.deactivate");
        delegate.deactivate(id);
      }

      @Override
      public void delete(Long id) {
        record("basic-mutation.delete");
        delegate.delete(id);
      }

      private void record(String call) {
        calls.add(call);
        activeDuringProviderInvocation |=
          TransactionSynchronizationManager.isActualTransactionActive();
      }
    }

    private static final class AuthorizationState implements AuthorizationFixture {

      private boolean authenticated;
      private Set<String> authorities = Set.of();

      @Override
      public void unauthenticated() {
        authenticated = false;
        authorities = Set.of();
      }

      @Override
      public void authenticated(Set<String> selectedAuthorities) {
        authenticated = true;
        authorities = Set.copyOf(selectedAuthorities);
      }

      @SuppressWarnings("unused")
      private boolean authenticated() {
        return authenticated;
      }

      @SuppressWarnings("unused")
      private Set<String> authorities() {
        return authorities;
      }
    }

    private record InvocationResult(String resultId, StateSnapshot publicResult) {}

    private record DatabaseState(List<NodeRow> nodes, List<ClosureRow> closure) {
      private DatabaseState {
        nodes = List.copyOf(nodes);
        closure = List.copyOf(closure);
      }

      private static DatabaseState empty() {
        return new DatabaseState(List.of(), List.of());
      }
    }

    private record NodeRow(
      Long id,
      Long parentId,
      Long sortRank,
      String name,
      String slug,
      boolean active,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt
    ) {
      private String stableValue() {
        return String.join(
          "|",
          id.toString(),
          Objects.toString(parentId, "root"),
          sortRank.toString(),
          name,
          slug,
          Boolean.toString(active),
          createdAt.toString(),
          updatedAt.toString()
        );
      }
    }

    private record ClosureRow(Long ancestorId, Long descendantId, int depth) {
      private String stableValue() {
        return ancestorId + "|" + descendantId + "|" + depth;
      }
    }

    private static final class ControlledOuterFailure extends RuntimeException {}
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ContainersConfiguration {

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer<?> postgresContainer() {
      return new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"));
    }
  }
}
