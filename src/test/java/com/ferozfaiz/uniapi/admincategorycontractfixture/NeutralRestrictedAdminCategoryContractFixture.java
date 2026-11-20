package com.ferozfaiz.uniapi.admincategorycontractfixture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryCreateCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryPatchCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryUpdateCommand;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.policy.AdminCategoryDefinitionValidator;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryCreateValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryParentOptionsValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPatchValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPlacementValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryReplaceValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryTreeValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.GeneralValidationPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.MutationValidationPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.QueryPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBindings;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryBasicMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierCodec;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierProfile;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryLogicalPolicyId;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping.Binding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyObligation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPolicyReference;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimeBinding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimePorts;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryContractAssertions;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractFixture;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractFixture.InteractionLedger;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractFixture.QueryObservation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractFixture.StateSnapshot;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractFixture.TransactionObservation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.Applicability;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.InteractionExpectation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.OperationExpectation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.PortSlot;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.QueryCountExpectation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.SeedRecord;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.StableErrorExpectation;
import com.ferozfaiz.uniapi.category.support.log.StructuredLogCapture;
import com.ferozfaiz.uniapi.category.support.sql.CountingDataSource;
import com.ferozfaiz.uniapi.category.support.sql.SqlStatementObservation;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Test-only PostgreSQL proof fixture for the restricted direct-port profile. */
public final class NeutralRestrictedAdminCategoryContractFixture
  implements AdminCategoryProviderContractFixture
{

  private static final String TABLE = "admin_category_contract_restricted_node";
  private static final String READ_PERMISSION = "admin:neutral-restricted-categories:read";
  private static final String WRITE_PERMISSION = "admin:neutral-restricted-categories:write";
  private static final String UNSUPPORTED_RESULT = "409 CATEGORY_CAPABILITY_UNSUPPORTED";
  private static final String UNSUPPORTED_ERROR = "CATEGORY_CAPABILITY_UNSUPPORTED";
  private static final String UNSUPPORTED_LOG = "capability.unsupported";
  private static final Set<String> FORBIDDEN_INTERACTIONS = Set.of(
    "category-read.getNode",
    "category-read.get",
    "hierarchy-read.tree",
    "hierarchy-read.parentOptions",
    "hierarchy-read.breadcrumb",
    "basic-mutation.replace",
    "basic-mutation.patch",
    "basic-mutation.delete",
    "hierarchy-mutation.move",
    "ordering.reorder",
    "lifecycle.activate",
    "lifecycle.deactivate",
    "hierarchy-policy.validate",
    "ordering-policy.validate",
    "cursor.decode",
    "lock.acquire",
    "event.publish",
    "provider.fallback"
  );
  private static final OffsetDateTime CREATED_TIMESTAMP = OffsetDateTime.parse(
    "2026-07-16T12:00:00Z"
  );
  private static final AtomicLong SCHEMA_SEQUENCE = new AtomicLong();
  private static final AdminCategoryProviderContractProfile PROFILE = createProfile();

  private final String schemaName;
  private final JdbcTemplate administrationJdbc;
  private final CountingDataSource countingDataSource;
  private final JdbcTemplate jdbc;
  private final RecordingTransactionManager transactionManager;
  private final TransactionTemplate transactions;
  private final RestrictedAdapter adapter;
  private final AdminCategoryRuntimePorts ports;
  private final AdminCategorySystemRegistration registration;
  private final AdminCategorySystemRegistry registry;
  private final AdminCategoryApplicationService applicationService;
  private final AuthorizationState authorization = new AuthorizationState();
  private final List<String> calls = new ArrayList<>();
  private final List<String> forbiddenInteractions = new ArrayList<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private OutputObservation output = emptyOutput();
  private Optional<SqlStatementObservation> measuredSql = Optional.empty();
  private long createdOrdinal;

  public NeutralRestrictedAdminCategoryContractFixture() {
    PostgreSQLContainer<?> postgres = PostgreSqlHolder.POSTGRES;
    schemaName = "admin_category_contract_restricted_" + SCHEMA_SEQUENCE.incrementAndGet();
    administrationJdbc = new JdbcTemplate(dataSource(postgres, postgres.getJdbcUrl()));
    administrationJdbc.execute("CREATE SCHEMA " + schemaName);
    try {
      String separator = postgres.getJdbcUrl().contains("?") ? "&" : "?";
      countingDataSource = new CountingDataSource(
        dataSource(postgres, postgres.getJdbcUrl() + separator + "currentSchema=" + schemaName)
      );
      jdbc = new JdbcTemplate(countingDataSource);
      jdbc.execute(
        """
        CREATE TABLE admin_category_contract_restricted_node (
          id BIGINT PRIMARY KEY,
          name VARCHAR(255) NOT NULL,
          slug VARCHAR(255) NOT NULL UNIQUE,
          active BOOLEAN NOT NULL,
          created_at TIMESTAMPTZ NOT NULL,
          updated_at TIMESTAMPTZ NOT NULL
        )
        """
      );
      transactionManager = new RecordingTransactionManager(jdbc.getDataSource());
      transactions = new TransactionTemplate(transactionManager);
      adapter = new RestrictedAdapter();
      ports = ports(adapter);
      registration = registration(adapter, ports);
      registry = registry(registration);
      applicationService = transactionalService(
        new AdminCategoryApplicationService(registry),
        transactionManager
      );
      reset();
    } catch (RuntimeException | Error failure) {
      administrationJdbc.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
      throw failure;
    }
  }

  public static AdminCategoryProviderContractProfile profile() {
    return PROFILE;
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
    return ports;
  }

  @Override
  public AuthorizationFixture authorization() {
    return authorization;
  }

  @Override
  public void reset() {
    requireOpen();
    countingDataSource.beginSetup();
    jdbc.execute("TRUNCATE TABLE " + TABLE);
    calls.clear();
    forbiddenInteractions.clear();
    transactionManager.resetObservations();
    adapter.resetObservations();
    authorization.authenticated(Set.of(READ_PERMISSION, WRITE_PERMISSION));
    output = emptyOutput();
    measuredSql = Optional.empty();
    createdOrdinal = 0L;
  }

  @Override
  public void seed(List<SeedRecord> records) {
    requireOpen();
    for (SeedRecord record : List.copyOf(records)) {
      Map<String, String> attributes = record.attributes();
      jdbc.update(
        """
        INSERT INTO admin_category_contract_restricted_node
          (id, name, slug, active, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        Long.valueOf(record.recordId()),
        requiredAttribute(attributes, "name"),
        requiredAttribute(attributes, "slug"),
        Boolean.valueOf(requiredAttribute(attributes, "active")),
        OffsetDateTime.parse(requiredAttribute(attributes, "createdAt")),
        OffsetDateTime.parse(requiredAttribute(attributes, "updatedAt"))
      );
    }
    adapter.resetObservations();
  }

  @Override
  public OperationObservation invoke(Operation operation) {
    requireOpen();
    beginObservation();
    InvocationResult result = null;
    Optional<String> stableError = Optional.empty();
    String publicMessage = "";
    try (
      StructuredLogCapture capture = StructuredLogCapture.attach(
        AdminCategoryApplicationService.class
      )
    ) {
      try {
        result = invokeApplication(operation);
      } catch (AdminCategoryException failure) {
        stableError = Optional.of(failure.code());
        publicMessage = failure.getMessage();
        result = new InvocationResult(
          UNSUPPORTED_RESULT,
          new StateSnapshot(Map.of("errorCode", failure.code()))
        );
      } finally {
        freezeQueryCountObservation();
        output = output(result, stableError, publicMessage, capture.events());
      }
    }
    return new OperationObservation(result.resultId(), stableError, result.publicResult(), output);
  }

  @Override
  public OperationObservation invokeThenFailAfterWrite(Operation operation) {
    requireOpen();
    if (operation != Operation.CREATE) {
      throw new IllegalArgumentException("restricted rollback applies only to create");
    }
    beginObservation();
    try (
      StructuredLogCapture capture = StructuredLogCapture.attach(
        AdminCategoryApplicationService.class
      )
    ) {
      try {
        transactions.executeWithoutResult(ignored -> {
          applicationService.create(
            NeutralAdminCategoryDefinitions.RESTRICTED_SYSTEM_KEY,
            create()
          );
          throw new ControlledOuterFailure();
        });
        throw new AssertionError("controlled outer failure was not raised");
      } catch (ControlledOuterFailure expected) {
        freezeQueryCountObservation();
        StateSnapshot rolledBack = snapshot();
        Optional<String> stableError = Optional.of("CONTROLLED_OUTER_FAILURE");
        output = output(
          new InvocationResult("rollback", rolledBack),
          stableError,
          "controlled outer failure",
          capture.events()
        );
        return new OperationObservation("rollback", stableError, rolledBack, output);
      }
    }
  }

  @Override
  public StateSnapshot snapshot() {
    requireOpen();
    List<String> rows = jdbc.query(
      """
      SELECT id, name, slug, active, created_at, updated_at
      FROM admin_category_contract_restricted_node
      ORDER BY id ASC
      """,
      (resultSet, rowNumber) ->
        String.join(
          "|",
          Long.toString(resultSet.getLong("id")),
          resultSet.getString("name"),
          resultSet.getString("slug"),
          Boolean.toString(resultSet.getBoolean("active")),
          resultSet.getObject("created_at", OffsetDateTime.class).toString(),
          resultSet.getObject("updated_at", OffsetDateTime.class).toString()
        )
    );
    return new StateSnapshot(
      Map.of("rowCount", Integer.toString(rows.size()), "rows", String.join(",", rows))
    );
  }

  @Override
  public InteractionLedger interactions() {
    return new InteractionLedger(List.copyOf(calls), List.copyOf(forbiddenInteractions));
  }

  @Override
  public TransactionObservation transactionObservation() {
    return new TransactionObservation(
      transactionManager.commits(),
      transactionManager.rollbacks(),
      adapter.activeDuringInvocation()
    );
  }

  @Override
  public OutputObservation outputObservation() {
    return output;
  }

  @Override
  public Optional<QueryObservation> queryObservation() {
    return measuredSql.flatMap(NeutralRestrictedAdminCategoryContractFixture::queryObservation);
  }

  /** Starts a one-operation measurement window for reusable query-count contract callers. */
  public void beginQueryCountMeasurement() {
    requireOpen();
    beginObservation();
  }

  /** Freezes and returns the real JDBC execution inventory before any assertion query runs. */
  public SqlStatementObservation freezeQueryCountObservation() {
    requireOpen();
    SqlStatementObservation observation = countingDataSource.measurementObservation();
    measuredSql = Optional.of(observation);
    return observation;
  }

  @Override
  public StateSnapshot providerState() {
    return snapshot();
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      administrationJdbc.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
    }
  }

  private InvocationResult invokeApplication(Operation operation) {
    String systemKey = NeutralAdminCategoryDefinitions.RESTRICTED_SYSTEM_KEY;
    return switch (operation) {
      case CAPABILITIES -> {
        AdminCategorySystemDefinition selected = applicationService.capabilities(systemKey);
        yield new InvocationResult(
          successResult(Operation.CAPABILITIES),
          new StateSnapshot(
            Map.of(
              "systemKey",
              selected.systemKey(),
              "operations",
              selected
                .operations()
                .supportedOperations()
                .stream()
                .sorted(java.util.Comparator.comparingInt(Enum::ordinal))
                .map(Operation::code)
                .collect(java.util.stream.Collectors.joining(","))
            )
          )
        );
      }
      case LIST -> {
        PageResult<AdminCategoryModels.CategoryNode> page = applicationService.list(
          systemKey,
          list()
        );
        yield new InvocationResult(successResult(Operation.LIST), pageSnapshot(page));
      }
      case CREATE -> {
        AdminCategoryModels.Category category = applicationService.create(systemKey, create());
        yield new InvocationResult(successResult(Operation.CREATE), categorySnapshot(category));
      }
      case TREE -> {
        applicationService.tree(systemKey, tree());
        yield unreachable(operation);
      }
      case PARENT_OPTIONS -> {
        applicationService.parentOptions(systemKey, parentOptions());
        yield unreachable(operation);
      }
      case GET -> {
        applicationService.getNode(systemKey, 41L);
        yield unreachable(operation);
      }
      case BREADCRUMB -> {
        applicationService.breadcrumb(systemKey, 41L);
        yield unreachable(operation);
      }
      case REPLACE -> {
        applicationService.update(systemKey, 41L, replace());
        yield unreachable(operation);
      }
      case PATCH -> {
        applicationService.patch(systemKey, 41L, patch());
        yield unreachable(operation);
      }
      case DELETE -> {
        applicationService.delete(systemKey, 41L);
        yield unreachable(operation);
      }
      case MOVE -> {
        applicationService.move(systemKey, 41L, placement(Operation.MOVE));
        yield unreachable(operation);
      }
      case REORDER -> {
        applicationService.reorder(systemKey, 41L, placement(Operation.REORDER));
        yield unreachable(operation);
      }
      case ACTIVATE -> {
        applicationService.activate(systemKey, 41L);
        yield unreachable(operation);
      }
      case DEACTIVATE -> {
        applicationService.deactivate(systemKey, 41L);
        yield unreachable(operation);
      }
    };
  }

  private AdminCategoryCreateValidationInput create() {
    createdOrdinal++;
    return new AdminCategoryCreateValidationInput(
      NeutralAdminCategoryDefinitions.restrictedDefinition(),
      null,
      null,
      null,
      null,
      "Created " + createdOrdinal,
      "created-" + createdOrdinal,
      true
    );
  }

  private static AdminCategoryListValidationInput list() {
    return new AdminCategoryListValidationInput(
      NeutralAdminCategoryDefinitions.restrictedDefinition(),
      null,
      null,
      null,
      null,
      null,
      null,
      List.of(),
      null
    );
  }

  private static AdminCategoryTreeValidationInput tree() {
    return new AdminCategoryTreeValidationInput(
      NeutralAdminCategoryDefinitions.restrictedDefinition(),
      null,
      null,
      List.of(),
      null
    );
  }

  private static AdminCategoryParentOptionsValidationInput parentOptions() {
    return new AdminCategoryParentOptionsValidationInput(
      NeutralAdminCategoryDefinitions.restrictedDefinition(),
      null,
      null,
      null,
      List.of(),
      null,
      null
    );
  }

  private static AdminCategoryReplaceValidationInput replace() {
    return new AdminCategoryReplaceValidationInput(
      NeutralAdminCategoryDefinitions.restrictedDefinition(),
      41L,
      null,
      "Replaced",
      "replaced",
      true
    );
  }

  private static AdminCategoryPatchValidationInput patch() {
    return new AdminCategoryPatchValidationInput(
      NeutralAdminCategoryDefinitions.restrictedDefinition(),
      41L,
      null,
      false,
      "Patched",
      true,
      null,
      false,
      null,
      false
    );
  }

  private static AdminCategoryPlacementValidationInput placement(Operation operation) {
    return new AdminCategoryPlacementValidationInput(
      NeutralAdminCategoryDefinitions.restrictedDefinition(),
      operation,
      null,
      null,
      null,
      null
    );
  }

  private void beginObservation() {
    calls.clear();
    forbiddenInteractions.clear();
    transactionManager.resetObservations();
    adapter.resetObservations();
    countingDataSource.reset();
    measuredSql = Optional.empty();
    output = emptyOutput();
  }

  private void requireOpen() {
    if (closed.get()) {
      throw new IllegalStateException("restricted fixture is closed");
    }
  }

  private static AdminCategoryProviderContractProfile createProfile() {
    AdminCategorySystemDefinition definition =
      NeutralAdminCategoryDefinitions.restrictedDefinition();
    Set<Operation> operations = Set.of(Operation.CAPABILITIES, Operation.LIST, Operation.CREATE);
    Map<Operation, PortSlot> operationPorts = new EnumMap<>(Operation.class);
    operationPorts.put(Operation.CAPABILITIES, PortSlot.NONE);
    operationPorts.put(Operation.LIST, PortSlot.CATEGORY_READ);
    operationPorts.put(Operation.CREATE, PortSlot.BASIC_MUTATION);
    Map<Operation, OperationExpectation> expectations = new EnumMap<>(Operation.class);
    for (Operation operation : Operation.values()) {
      boolean supported = operations.contains(operation);
      expectations.put(
        operation,
        new OperationExpectation(
          "V(" + operation + ")",
          supported ? successResult(operation) : UNSUPPORTED_RESULT,
          supported ? AdminCategoryContractAssertions.NO_STABLE_ERROR : UNSUPPORTED_ERROR,
          supported
            ? operation == Operation.CREATE
              ? "category.create.succeeded"
              : AdminCategoryContractAssertions.NO_LOG_OUTCOME
            : UNSUPPORTED_LOG,
          new Applicability(
            true,
            supported
              ? "neutral restricted declares the operation"
              : "neutral restricted rejects the undeclared operation before provider work"
          )
        )
      );
    }
    Map<Operation, Applicability> rollback = new EnumMap<>(Operation.class);
    rollback.put(
      Operation.CAPABILITIES,
      new Applicability(false, "CAPABILITIES performs no mutation")
    );
    rollback.put(Operation.LIST, new Applicability(false, "LIST is read-only"));
    rollback.put(Operation.CREATE, new Applicability(true, "CREATE is transaction-capable"));
    return new AdminCategoryProviderContractProfile(
      "neutral-restricted",
      definition,
      () -> {
        RegistrationOnlyAdapter adapter = new RegistrationOnlyAdapter();
        return registration(adapter, ports(adapter));
      },
      operations,
      operationPorts,
      seedRecords(),
      List.of("42", "43", "41"),
      expectations,
      new StableErrorExpectation(
        "CATEGORY_CAPABILITY_UNSUPPORTED",
        "operation is not supported for category system",
        "GET is deliberately undeclared for the restricted profile"
      ),
      rollback,
      new InteractionExpectation(
        Set.of("category-read.list", "basic-mutation.create"),
        FORBIDDEN_INTERACTIONS
      ),
      List.of(
        new QueryCountExpectation(
          "restricted-list",
          1,
          25,
          2,
          new Applicability(true, "restricted LIST uses bounded PostgreSQL statements")
        )
      )
    );
  }

  private static String successResult(Operation operation) {
    return switch (operation) {
      case CAPABILITIES -> "200 AdminCategoryCapabilityResponse";
      case LIST -> "200 PageResponse<AdminCategoryListItemResponse>";
      case CREATE -> "201 AdminCategoryDetailResponse plus absolute Location";
      default -> throw new IllegalArgumentException(
        "operation is not supported by neutral restricted"
      );
    };
  }

  private static List<SeedRecord> seedRecords() {
    return List.of(
      seed("41", "Earlier", "earlier", "2026-07-16T10:00:00Z"),
      seed("42", "Tie first", "tie-first", "2026-07-16T12:00:00Z"),
      seed("43", "Tie second", "tie-second", "2026-07-16T12:00:00Z")
    );
  }

  private static SeedRecord seed(String id, String name, String slug, String updatedAt) {
    return new SeedRecord(
      id,
      Map.of(
        "name",
        name,
        "slug",
        slug,
        "active",
        "true",
        "createdAt",
        "2026-07-15T00:00:00Z",
        "updatedAt",
        updatedAt
      )
    );
  }

  private static AdminCategoryRuntimePorts ports(RestrictedPortIdentity adapter) {
    return new AdminCategoryRuntimePorts(
      Optional.of(adapter),
      Optional.empty(),
      Optional.of(adapter),
      Optional.empty(),
      Optional.empty(),
      Optional.empty()
    );
  }

  private static AdminCategorySystemRegistration registration(
    RestrictedPortIdentity adapter,
    AdminCategoryRuntimePorts runtimePorts
  ) {
    AdminCategorySystemDefinition definition =
      NeutralAdminCategoryDefinitions.restrictedDefinition();
    AdminCategoryDefinitionValidator validator = new AdminCategoryDefinitionValidator();
    AdminCategoryPolicyReference general = reference(
      AdminCategoryPolicyObligation.GENERAL_VALIDATION,
      "general-validation"
    );
    AdminCategoryPolicyReference query = reference(AdminCategoryPolicyObligation.QUERY, "query");
    AdminCategoryPolicyReference mutation = reference(
      AdminCategoryPolicyObligation.MUTATION_VALIDATION,
      "mutation-validation"
    );
    AdminCategoryRuntimePolicyBindings policies = new AdminCategoryRuntimePolicyBindings(
      List.of(
        new GeneralValidationPolicyBinding(general, validator),
        new QueryPolicyBinding(query, validator),
        new MutationValidationPolicyBinding(mutation, validator)
      )
    );
    AdminCategoryPermissionMapping permissions = new AdminCategoryPermissionMapping(
      List.of(
        new Binding(Operation.CAPABILITIES, READ_PERMISSION),
        new Binding(Operation.LIST, READ_PERMISSION),
        new Binding(Operation.CREATE, WRITE_PERMISSION)
      )
    );
    return AdminCategorySystemRegistration.of(
      definition,
      new AdminCategoryRuntimeBinding(
        new NumericIdentifierCodec(),
        permissions,
        policies,
        runtimePorts
      )
    );
  }

  private static AdminCategoryPolicyReference reference(
    AdminCategoryPolicyObligation obligation,
    String policyKey
  ) {
    return new AdminCategoryPolicyReference(
      obligation,
      new AdminCategoryLogicalPolicyId("neutral-contract", policyKey)
    );
  }

  private static AdminCategorySystemRegistry registry(
    AdminCategorySystemRegistration registration
  ) {
    AdminCategorySystemRegistry registry = mock(AdminCategorySystemRegistry.class);
    when(
      registry.findRegistration(NeutralAdminCategoryDefinitions.RESTRICTED_SYSTEM_KEY)
    ).thenReturn(Optional.of(registration));
    when(registry.registrations()).thenReturn(List.of(registration));
    return registry;
  }

  private static AdminCategoryApplicationService transactionalService(
    AdminCategoryApplicationService target,
    DataSourceTransactionManager transactionManager
  ) {
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.addAdvice(
      new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource())
    );
    return (AdminCategoryApplicationService) proxyFactory.getProxy();
  }

  private static DriverManagerDataSource dataSource(PostgreSQLContainer<?> postgres, String url) {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName(postgres.getDriverClassName());
    dataSource.setUrl(url);
    dataSource.setUsername(postgres.getUsername());
    dataSource.setPassword(postgres.getPassword());
    return dataSource;
  }

  private static StateSnapshot pageSnapshot(PageResult<AdminCategoryModels.CategoryNode> page) {
    return new StateSnapshot(
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
    );
  }

  private static StateSnapshot categorySnapshot(AdminCategoryModels.Category category) {
    return new StateSnapshot(
      Map.of(
        "id",
        category.id().toString(),
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

  private static InvocationResult unreachable(Operation operation) {
    throw new AssertionError("undeclared operation reached provider path: " + operation.code());
  }

  private static String requiredAttribute(Map<String, String> attributes, String name) {
    String value = attributes.get(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("restricted seed is missing " + name);
    }
    return value;
  }

  private static OutputObservation output(
    InvocationResult result,
    Optional<String> stableError,
    String publicMessage,
    List<StructuredLogCapture.CapturedEvent> events
  ) {
    List<String> publicValues = new ArrayList<>();
    publicValues.add(result.resultId());
    publicValues.addAll(result.publicResult().facts().values());
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
      .flatMap(throwable -> java.util.stream.Stream.of(throwable.className(), throwable.message()))
      .filter(value -> value != null && !value.isBlank())
      .toList();
    return new OutputObservation(publicValues, logValues, causes);
  }

  private static OutputObservation emptyOutput() {
    return new OutputObservation(List.of(), List.of(), List.of());
  }

  private interface RestrictedPortIdentity
    extends AdminCategoryReadPort, AdminCategoryBasicMutationPort {}

  private final class RestrictedAdapter implements RestrictedPortIdentity {

    private boolean activeDuringInvocation;

    @Override
    public PageResult<AdminCategoryModels.CategoryNode> list(AdminCategoryListQuery query) {
      calls.add("category-read.list");
      activeDuringInvocation = TransactionSynchronizationManager.isActualTransactionActive();
      List<String> conditions = new ArrayList<>();
      List<Object> filterArguments = new ArrayList<>();
      if (query.filters().active() != null) {
        conditions.add("active = ?");
        filterArguments.add(query.filters().active());
      }
      if (query.filters().q() != null) {
        conditions.add("(LOWER(name) LIKE LOWER(?) OR LOWER(slug) LIKE LOWER(?))");
        String pattern = "%" + query.filters().q() + "%";
        filterArguments.add(pattern);
        filterArguments.add(pattern);
      }
      if (query.filters().slug() != null) {
        conditions.add("slug = ?");
        filterArguments.add(query.filters().slug());
      }
      String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

      Long total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + TABLE + where,
        Long.class,
        filterArguments.toArray()
      );
      List<Object> dataArguments = new ArrayList<>(filterArguments);
      dataArguments.add(query.page().size());
      dataArguments.add((long) query.page().page() * query.page().size());
      List<AdminCategoryModels.CategoryNode> content = jdbc.query(
        "SELECT id, name, slug, active, created_at, updated_at FROM " +
          TABLE +
          where +
          " ORDER BY " +
          orderBy(query) +
          " LIMIT ? OFFSET ?",
        (resultSet, rowNumber) ->
          new AdminCategoryModels.CategoryNode(category(resultSet), 0, false),
        dataArguments.toArray()
      );
      long totalElements = total == null ? 0L : total;
      int totalPages = (int) Math.ceil((double) totalElements / query.page().size());
      int page = query.page().page();
      return new PageResult<>(
        content,
        page,
        query.page().size(),
        totalElements,
        totalPages,
        page == 0,
        totalPages == 0 || page >= totalPages - 1,
        query.sort()
      );
    }

    @Override
    public AdminCategoryModels.CategoryNode getNode(Long id) {
      return forbidden("category-read.getNode");
    }

    @Override
    public AdminCategoryModels.Category get(Long id) {
      return forbidden("category-read.get");
    }

    @Override
    public AdminCategoryModels.Category create(AdminCategoryCreateCommand command) {
      calls.add("basic-mutation.create");
      activeDuringInvocation = TransactionSynchronizationManager.isActualTransactionActive();
      OffsetDateTime timestamp = CREATED_TIMESTAMP.plusSeconds(createdOrdinal);
      return jdbc.queryForObject(
        """
        INSERT INTO admin_category_contract_restricted_node
          (id, name, slug, active, created_at, updated_at)
        SELECT COALESCE(MAX(id), 0) + 1, ?, ?, ?, ?, ?
        FROM admin_category_contract_restricted_node
        RETURNING id, name, slug, active, created_at, updated_at
        """,
        (resultSet, rowNumber) -> category(resultSet),
        command.name(),
        command.slug(),
        command.active(),
        timestamp,
        timestamp
      );
    }

    @Override
    public AdminCategoryModels.Category replace(Long id, AdminCategoryUpdateCommand command) {
      return forbidden("basic-mutation.replace");
    }

    @Override
    public AdminCategoryModels.Category patch(Long id, AdminCategoryPatchCommand command) {
      return forbidden("basic-mutation.patch");
    }

    @Override
    public void delete(Long id) {
      forbidden("basic-mutation.delete");
    }

    private <T> T forbidden(String interaction) {
      calls.add(interaction);
      forbiddenInteractions.add(interaction);
      throw new AssertionError("undeclared sibling method was reached");
    }

    private void resetObservations() {
      activeDuringInvocation = false;
    }

    private boolean activeDuringInvocation() {
      return activeDuringInvocation;
    }
  }

  private static Optional<QueryObservation> queryObservation(SqlStatementObservation observation) {
    if (observation.statementCount() == 0) {
      return Optional.empty();
    }
    return Optional.of(
      new QueryObservation(
        observation.statementCount(),
        observation.statementKinds().stream().map(Enum::name).toList(),
        observation.normalizedFingerprints()
      )
    );
  }

  private static AdminCategoryModels.Category category(java.sql.ResultSet resultSet)
    throws java.sql.SQLException {
    return new AdminCategoryModels.Category(
      resultSet.getLong("id"),
      null,
      null,
      resultSet.getString("name"),
      resultSet.getString("slug"),
      resultSet.getBoolean("active"),
      resultSet.getObject("created_at", OffsetDateTime.class),
      resultSet.getObject("updated_at", OffsetDateTime.class)
    );
  }

  private static String orderBy(AdminCategoryListQuery query) {
    return query
      .sort()
      .orders()
      .stream()
      .map(order -> column(order.property()) + " " + order.direction().name())
      .collect(java.util.stream.Collectors.joining(", "));
  }

  private static String column(String property) {
    return switch (property) {
      case "id" -> "id";
      case "name" -> "name";
      case "slug" -> "slug";
      case "active" -> "active";
      case "createdAt" -> "created_at";
      case "updatedAt" -> "updated_at";
      default -> throw new IllegalArgumentException("unsupported restricted sort property");
    };
  }

  private static final class RegistrationOnlyAdapter implements RestrictedPortIdentity {

    @Override
    public PageResult<AdminCategoryModels.CategoryNode> list(AdminCategoryListQuery query) {
      throw new UnsupportedOperationException("registration-only adapter");
    }

    @Override
    public AdminCategoryModels.Category get(Long id) {
      throw new UnsupportedOperationException("registration-only adapter");
    }

    @Override
    public AdminCategoryModels.Category create(AdminCategoryCreateCommand command) {
      throw new UnsupportedOperationException("registration-only adapter");
    }

    @Override
    public AdminCategoryModels.Category replace(Long id, AdminCategoryUpdateCommand command) {
      throw new UnsupportedOperationException("registration-only adapter");
    }

    @Override
    public AdminCategoryModels.Category patch(Long id, AdminCategoryPatchCommand command) {
      throw new UnsupportedOperationException("registration-only adapter");
    }

    @Override
    public void delete(Long id) {
      throw new UnsupportedOperationException("registration-only adapter");
    }
  }

  private static final class NumericIdentifierCodec implements AdminCategoryIdentifierCodec<Long> {

    @Override
    public AdminCategoryIdentifierProfile identifierProfile() {
      return AdminCategoryIdentifierProfile.NUMERIC_LONG_V1;
    }

    @Override
    public Long parse(String value) {
      if (value == null || !value.matches("[1-9][0-9]*")) {
        throw new IllegalArgumentException("identifier is not canonical numeric text");
      }
      return Long.valueOf(value);
    }

    @Override
    public String serialize(Long value) {
      if (value == null || value <= 0) {
        throw new IllegalArgumentException("identifier is not positive");
      }
      return value.toString();
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

  private static final class RecordingTransactionManager extends DataSourceTransactionManager {

    private int commits;
    private int rollbacks;

    private RecordingTransactionManager(javax.sql.DataSource dataSource) {
      super(dataSource);
    }

    @Override
    protected void doCommit(
      org.springframework.transaction.support.DefaultTransactionStatus status
    ) {
      super.doCommit(status);
      commits++;
    }

    @Override
    protected void doRollback(
      org.springframework.transaction.support.DefaultTransactionStatus status
    ) {
      super.doRollback(status);
      rollbacks++;
    }

    private void resetObservations() {
      commits = 0;
      rollbacks = 0;
    }

    private int commits() {
      return commits;
    }

    private int rollbacks() {
      return rollbacks;
    }
  }

  private static final class ControlledOuterFailure extends RuntimeException {

    private ControlledOuterFailure() {
      super("controlled outer failure");
    }
  }

  private record InvocationResult(String resultId, StateSnapshot publicResult) {}

  private static final class PostgreSqlHolder {

    private static final PostgreSQLContainer<?> POSTGRES = start();

    private PostgreSqlHolder() {}

    private static PostgreSQLContainer<?> start() {
      PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest");
      postgres.start();
      return postgres;
    }
  }
}

final class NeutralRestrictedAdminCategoryContractFixtureTest {

  @org.junit.jupiter.api.Test
  void listAndCreateUseTheRealApplicationServiceAndPostgresTransactions() {
    try (var fixture = new NeutralRestrictedAdminCategoryContractFixture()) {
      fixture.reset();
      fixture.seed(NeutralRestrictedAdminCategoryContractFixture.profile().seedRecords());

      var list = fixture.invoke(Operation.LIST);

      org.assertj.core.api.Assertions.assertThat(list.resultId()).isEqualTo(
        "200 PageResponse<AdminCategoryListItemResponse>"
      );
      org.assertj.core.api.Assertions.assertThat(list.publicResult().facts().get("ids")).isEqualTo(
        "42,43,41"
      );
      org.assertj.core.api.Assertions.assertThat(fixture.interactions().calls()).containsExactly(
        "category-read.list"
      );
      org.assertj.core.api.Assertions.assertThat(fixture.queryObservation())
        .get()
        .extracting(QueryObservation::statementCount)
        .isEqualTo(2);
      org.assertj.core.api.Assertions.assertThat(fixture.transactionObservation()).isEqualTo(
        new TransactionObservation(1, 0, true)
      );

      var create = fixture.invoke(Operation.CREATE);

      org.assertj.core.api.Assertions.assertThat(create.resultId()).isEqualTo(
        "201 AdminCategoryDetailResponse plus absolute Location"
      );
      org.assertj.core.api.Assertions.assertThat(fixture.interactions().calls()).containsExactly(
        "basic-mutation.create"
      );
      org.assertj.core.api.Assertions.assertThat(
        fixture.snapshot().facts().get("rowCount")
      ).isEqualTo("4");
      org.assertj.core.api.Assertions.assertThat(fixture.transactionObservation()).isEqualTo(
        new TransactionObservation(1, 0, true)
      );
    }
  }

  @org.junit.jupiter.api.Test
  void controlledOuterFailureRollsCreateBackWithoutFallback() {
    try (var fixture = new NeutralRestrictedAdminCategoryContractFixture()) {
      fixture.reset();
      fixture.seed(NeutralRestrictedAdminCategoryContractFixture.profile().seedRecords());
      StateSnapshot before = fixture.snapshot();
      StateSnapshot visibleListBefore = fixture.invoke(Operation.LIST).publicResult();

      var observation = fixture.invokeThenFailAfterWrite(Operation.CREATE);
      StateSnapshot rolledBackState = fixture.snapshot();
      StateSnapshot rolledBackProviderState = fixture.providerState();
      InteractionLedger rollbackInteractions = fixture.interactions();
      TransactionObservation rollbackTransaction = fixture.transactionObservation();
      Optional<QueryObservation> rollbackQueries = fixture.queryObservation();
      StateSnapshot visibleListAfter = fixture.invoke(Operation.LIST).publicResult();

      org.assertj.core.api.Assertions.assertThat(observation.stableError()).contains(
        "CONTROLLED_OUTER_FAILURE"
      );
      org.assertj.core.api.Assertions.assertThat(rolledBackState).isEqualTo(before);
      org.assertj.core.api.Assertions.assertThat(rolledBackProviderState).isEqualTo(before);
      org.assertj.core.api.Assertions.assertThat(visibleListAfter).isEqualTo(visibleListBefore);
      org.assertj.core.api.Assertions.assertThat(rollbackInteractions.calls()).containsExactly(
        "basic-mutation.create"
      );
      org.assertj.core.api.Assertions.assertThat(
        rollbackInteractions.forbiddenInteractions()
      ).isEmpty();
      org.assertj.core.api.Assertions.assertThat(rollbackTransaction).isEqualTo(
        new TransactionObservation(0, 1, true)
      );
      org.assertj.core.api.Assertions.assertThat(rollbackQueries)
        .get()
        .extracting(QueryObservation::statementCount)
        .isEqualTo(1);
    }
  }

  @org.junit.jupiter.api.Test
  void undeclaredPresentPortSiblingsAreRejectedBeforeTheirMethods() {
    try (var fixture = new NeutralRestrictedAdminCategoryContractFixture()) {
      for (Operation operation : List.of(
        Operation.GET,
        Operation.REPLACE,
        Operation.PATCH,
        Operation.DELETE
      )) {
        fixture.reset();
        fixture.seed(NeutralRestrictedAdminCategoryContractFixture.profile().seedRecords());
        StateSnapshot before = fixture.snapshot();

        var observation = fixture.invoke(operation);

        org.assertj.core.api.Assertions.assertThat(observation.resultId()).isEqualTo(
          "409 CATEGORY_CAPABILITY_UNSUPPORTED"
        );
        org.assertj.core.api.Assertions.assertThat(observation.stableError()).contains(
          "CATEGORY_CAPABILITY_UNSUPPORTED"
        );
        org.assertj.core.api.Assertions.assertThat(fixture.interactions().calls())
          .as(operation.code())
          .isEmpty();
        org.assertj.core.api.Assertions.assertThat(fixture.interactions().forbiddenInteractions())
          .as(operation.code())
          .isEmpty();
        org.assertj.core.api.Assertions.assertThat(fixture.queryObservation())
          .as(operation.code())
          .isEmpty();
        org.assertj.core.api.Assertions.assertThat(fixture.snapshot()).isEqualTo(before);
      }
    }
  }
}
