package com.ferozfaiz.uniapi.admincategorycontractfixture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.Category;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.CategoryNode;
import com.ferozfaiz.uniapi.category.application.policy.AdminCategoryDefinitionValidator;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryCreateValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryParentOptionsValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPatchValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPlacementValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryReplaceValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryTreeValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.GeneralValidationPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBinding.QueryPolicyBinding;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryRuntimePolicyBindings;
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
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.Applicability;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.InteractionExpectation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.OperationExpectation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.PortSlot;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.SeedRecord;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.StableErrorExpectation;
import com.ferozfaiz.uniapi.category.support.log.StructuredLogCapture;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Test-only, deterministic in-memory implementation of the neutral flat provider contract. */
public final class NeutralFlatAdminCategoryContractFixture
  implements AdminCategoryProviderContractFixture
{

  private static final AdminCategorySystemDefinition DEFINITION =
    NeutralAdminCategoryDefinitions.flatDefinition();
  private static final String SYSTEM_KEY = DEFINITION.systemKey();
  private static final Long SELECTED_ID = 11L;
  private static final String UNSUPPORTED_RESULT = "409 CATEGORY_CAPABILITY_UNSUPPORTED";
  private static final String UNSUPPORTED_ERROR = "CATEGORY_CAPABILITY_UNSUPPORTED";
  private static final String UNSUPPORTED_LOG = "capability.unsupported";
  private static final Set<String> FORBIDDEN_INTERACTIONS = Set.of(
    "category-read.get",
    "hierarchy-read.tree",
    "hierarchy-read.parentOptions",
    "hierarchy-read.breadcrumb",
    "basic-mutation.create",
    "basic-mutation.replace",
    "basic-mutation.patch",
    "basic-mutation.delete",
    "hierarchy-mutation.move",
    "ordering.reorder",
    "lifecycle.activate",
    "lifecycle.deactivate",
    "hierarchy-policy.validate",
    "ordering-policy.validate",
    "repository.read",
    "repository.write",
    "cursor.decode",
    "sql.execute",
    "lock.acquire",
    "event.publish",
    "provider.fallback"
  );
  private static final List<SeedRecord> DEFAULT_SEEDS = List.of(
    seed(11L, "Current", "current", "2026-07-15T08:00:00Z"),
    seed(2L, "Earlier low", "earlier-low", "2026-07-14T08:00:00Z"),
    seed(7L, "Earlier high", "earlier-high", "2026-07-14T08:00:00Z")
  );
  private static final List<String> DEFAULT_ORDER = List.of("11", "2", "7");
  private static final AdminCategoryProviderContractProfile PROFILE = createProfile();

  private final Map<Long, Category> records = new LinkedHashMap<>();
  private final List<String> calls = new ArrayList<>();
  private final List<String> observedForbiddenInteractions = new ArrayList<>();
  private final FlatReadPort readPort = new FlatReadPort();
  private final AdminCategorySystemRegistration registration = registration(readPort);
  private final AdminCategorySystemRegistry registry = registry(registration);
  private final AdminCategoryApplicationService applicationService =
    new AdminCategoryApplicationService(registry);
  private final AuthorizationState authorization = new AuthorizationState();

  private TransactionObservation lastTransaction = new TransactionObservation(0, 0, false);
  private OutputObservation lastOutput = emptyOutput();

  public NeutralFlatAdminCategoryContractFixture() {}

  public static AdminCategoryProviderContractProfile contractProfile() {
    return PROFILE;
  }

  static AdminCategorySystemRegistration newRegistration() {
    return registration(new DetachedReadPort());
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
    records.clear();
    clearObservations();
    authorization.authenticated(Set.of());
  }

  @Override
  public void seed(List<SeedRecord> seeds) {
    Objects.requireNonNull(seeds, "records must not be null");
    records.clear();
    for (SeedRecord seed : seeds) {
      Category category = category(seed);
      if (records.putIfAbsent(category.id(), category) != null) {
        throw new IllegalArgumentException("seed identifiers must be unique");
      }
    }
    clearObservations();
  }

  @Override
  public OperationObservation invoke(Operation operation) {
    Objects.requireNonNull(operation, "operation must not be null");
    clearObservations();
    try (
      StructuredLogCapture logs = StructuredLogCapture.attach(AdminCategoryApplicationService.class)
    ) {
      try {
        OperationObservation observation = invokeSupportedOrRejected(operation);
        lastOutput = withLogs(observation.output(), logs);
        return new OperationObservation(
          observation.resultId(),
          observation.stableError(),
          observation.publicResult(),
          lastOutput
        );
      } catch (AdminCategoryException failure) {
        lastOutput = withLogs(errorOutput(failure), logs);
        return new OperationObservation(
          failure.code().equals(UNSUPPORTED_ERROR)
            ? UNSUPPORTED_RESULT
            : failure.status().value() + " " + failure.code(),
          Optional.of(failure.code()),
          errorSnapshot(failure),
          lastOutput
        );
      }
    }
  }

  @Override
  public OperationObservation invokeThenFailAfterWrite(Operation operation) {
    return invoke(operation);
  }

  @Override
  public StateSnapshot snapshot() {
    return stateSnapshot("public-records");
  }

  @Override
  public InteractionLedger interactions() {
    return new InteractionLedger(calls, observedForbiddenInteractions);
  }

  @Override
  public TransactionObservation transactionObservation() {
    return lastTransaction;
  }

  @Override
  public OutputObservation outputObservation() {
    return lastOutput;
  }

  @Override
  public Optional<QueryObservation> queryObservation() {
    return Optional.empty();
  }

  @Override
  public StateSnapshot providerState() {
    return stateSnapshot("in-memory-records");
  }

  private OperationObservation invokeSupportedOrRejected(Operation operation) {
    return switch (operation) {
      case CAPABILITIES -> capabilities();
      case LIST -> list();
      case TREE -> {
        applicationService.tree(
          SYSTEM_KEY,
          new AdminCategoryTreeValidationInput(DEFINITION, 0, 20, List.of(), null)
        );
        yield unreachable(operation);
      }
      case PARENT_OPTIONS -> {
        applicationService.parentOptions(
          SYSTEM_KEY,
          new AdminCategoryParentOptionsValidationInput(
            DEFINITION,
            null,
            20,
            null,
            List.of(),
            null,
            null
          )
        );
        yield unreachable(operation);
      }
      case GET -> get();
      case BREADCRUMB -> {
        applicationService.breadcrumb(SYSTEM_KEY, SELECTED_ID);
        yield unreachable(operation);
      }
      case CREATE -> {
        applicationService.create(
          SYSTEM_KEY,
          new AdminCategoryCreateValidationInput(
            DEFINITION,
            null,
            null,
            null,
            null,
            "Created",
            "created",
            true
          )
        );
        yield unreachable(operation);
      }
      case REPLACE -> {
        applicationService.update(
          SYSTEM_KEY,
          SELECTED_ID,
          new AdminCategoryReplaceValidationInput(
            DEFINITION,
            SELECTED_ID,
            null,
            "Replaced",
            "replaced",
            true
          )
        );
        yield unreachable(operation);
      }
      case PATCH -> {
        applicationService.patch(
          SYSTEM_KEY,
          SELECTED_ID,
          new AdminCategoryPatchValidationInput(
            DEFINITION,
            SELECTED_ID,
            null,
            false,
            "Patched",
            true,
            null,
            false,
            null,
            false
          )
        );
        yield unreachable(operation);
      }
      case DELETE -> {
        applicationService.delete(SYSTEM_KEY, SELECTED_ID);
        yield unreachable(operation);
      }
      case MOVE -> {
        applicationService.move(SYSTEM_KEY, SELECTED_ID, placement(Operation.MOVE));
        yield unreachable(operation);
      }
      case REORDER -> {
        applicationService.reorder(SYSTEM_KEY, SELECTED_ID, placement(Operation.REORDER));
        yield unreachable(operation);
      }
      case ACTIVATE -> {
        applicationService.activate(SYSTEM_KEY, SELECTED_ID);
        yield unreachable(operation);
      }
      case DEACTIVATE -> {
        applicationService.deactivate(SYSTEM_KEY, SELECTED_ID);
        yield unreachable(operation);
      }
    };
  }

  private OperationObservation capabilities() {
    AdminCategorySystemDefinition result = applicationService.capabilities(SYSTEM_KEY);
    String operations = result
      .operations()
      .supportedOperations()
      .stream()
      .sorted(Comparator.comparingInt(Enum::ordinal))
      .map(Operation::code)
      .reduce((left, right) -> left + "," + right)
      .orElse("none");
    StateSnapshot publicResult = new StateSnapshot(
      Map.of(
        "systemKey",
        result.systemKey(),
        "operations",
        operations,
        "fallbackOrder",
        result.ordering().fallbackOrder().isEmpty() ? "none" : "unexpected"
      )
    );
    OutputObservation output = new OutputObservation(
      List.of(result.systemKey(), operations, "fallback-order:none"),
      List.of(),
      List.of()
    );
    return success(Operation.CAPABILITIES, publicResult, output);
  }

  private OperationObservation list() {
    PageResult<CategoryNode> page = applicationService.list(
      SYSTEM_KEY,
      new AdminCategoryListValidationInput(
        DEFINITION,
        0,
        100,
        null,
        null,
        null,
        null,
        List.of(),
        null
      )
    );
    List<String> ids = page
      .content()
      .stream()
      .map(node -> node.category().id().toString())
      .toList();
    StateSnapshot publicResult = new StateSnapshot(
      Map.of(
        "ids",
        ids.toString(),
        "totalElements",
        Long.toString(page.totalElements()),
        "sort",
        page.sort().orders().toString()
      )
    );
    OutputObservation output = categoryOutput(
      page.content().stream().map(CategoryNode::category).toList()
    );
    return success(Operation.LIST, publicResult, output);
  }

  private OperationObservation get() {
    Category category = applicationService.getNode(SYSTEM_KEY, SELECTED_ID).category();
    StateSnapshot publicResult = new StateSnapshot(
      Map.of("id", category.id().toString(), "name", category.name(), "slug", category.slug())
    );
    return success(Operation.GET, publicResult, categoryOutput(List.of(category)));
  }

  private OperationObservation success(
    Operation operation,
    StateSnapshot result,
    OutputObservation output
  ) {
    return new OperationObservation(successResult(operation), Optional.empty(), result, output);
  }

  private static OperationObservation unreachable(Operation operation) {
    throw new IllegalStateException("undeclared operation unexpectedly succeeded: " + operation);
  }

  private static AdminCategoryPlacementValidationInput placement(Operation operation) {
    return new AdminCategoryPlacementValidationInput(DEFINITION, operation, null, null, null, null);
  }

  private StateSnapshot stateSnapshot(String kind) {
    List<Category> ordered = orderedRecords();
    String values = ordered
      .stream()
      .map(
        category -> category.id() + ":" + category.slug() + ":" + category.updatedAt().toInstant()
      )
      .toList()
      .toString();
    return new StateSnapshot(
      Map.of("kind", kind, "count", Integer.toString(ordered.size()), "records", values)
    );
  }

  private void clearObservations() {
    calls.clear();
    observedForbiddenInteractions.clear();
    lastTransaction = new TransactionObservation(0, 0, false);
    lastOutput = emptyOutput();
  }

  private void recordCall(String call) {
    calls.add(call);
    if (FORBIDDEN_INTERACTIONS.contains(call)) {
      observedForbiddenInteractions.add(call);
    }
  }

  private List<Category> orderedRecords() {
    return records.values().stream().sorted(ordinaryComparator()).toList();
  }

  private static Category category(SeedRecord seed) {
    Map<String, String> attributes = seed.attributes();
    Long id = parsePositiveLong(seed.recordId(), "recordId");
    String name = attributes.getOrDefault("name", "Category " + id);
    String slug = attributes.getOrDefault("slug", "category-" + id);
    boolean active = Boolean.parseBoolean(attributes.getOrDefault("active", "true"));
    OffsetDateTime updatedAt = OffsetDateTime.parse(
      attributes.getOrDefault("updatedAt", "2026-01-01T00:00:00Z")
    );
    OffsetDateTime createdAt = OffsetDateTime.parse(
      attributes.getOrDefault("createdAt", updatedAt.minusDays(1).toString())
    );
    return new Category(id, null, null, name, slug, active, createdAt, updatedAt);
  }

  private static Comparator<Category> ordinaryComparator() {
    return Comparator.comparing(Category::updatedAt).reversed().thenComparing(Category::id);
  }

  private static Comparator<Category> comparator(List<SortOrder> orders) {
    Comparator<Category> comparator = null;
    for (SortOrder order : orders) {
      Comparator<Category> selected = comparator(order.property());
      if (order.direction() == SortDirection.DESC) {
        selected = selected.reversed();
      }
      comparator = comparator == null ? selected : comparator.thenComparing(selected);
    }
    return Objects.requireNonNullElseGet(
      comparator,
      NeutralFlatAdminCategoryContractFixture::ordinaryComparator
    );
  }

  private static Comparator<Category> comparator(String property) {
    return switch (property) {
      case "id" -> Comparator.comparing(Category::id);
      case "name" -> Comparator.comparing(Category::name);
      case "slug" -> Comparator.comparing(Category::slug);
      case "active" -> Comparator.comparing(Category::active);
      case "createdAt" -> Comparator.comparing(Category::createdAt);
      case "updatedAt" -> Comparator.comparing(Category::updatedAt);
      default -> throw new IllegalArgumentException("unsupported in-memory sort property");
    };
  }

  private static boolean matches(Category category, AdminCategoryListQuery query) {
    if (
      query.filters().active() != null &&
      category.active() != query.filters().active().booleanValue()
    ) {
      return false;
    }
    if (query.filters().slug() != null && !category.slug().equals(query.filters().slug())) {
      return false;
    }
    if (query.filters().q() == null) {
      return true;
    }
    String needle = query.filters().q().toLowerCase(Locale.ROOT);
    return (
      category.name().toLowerCase(Locale.ROOT).contains(needle) ||
      category.slug().toLowerCase(Locale.ROOT).contains(needle)
    );
  }

  private static OutputObservation categoryOutput(List<Category> categories) {
    List<String> values = categories
      .stream()
      .flatMap(category ->
        java.util.stream.Stream.of(
          category.id().toString(),
          category.name(),
          category.slug(),
          category.createdAt().toString(),
          category.updatedAt().toString()
        )
      )
      .toList();
    return new OutputObservation(values, List.of(), List.of());
  }

  private static OutputObservation errorOutput(AdminCategoryException failure) {
    return new OutputObservation(
      List.of(failure.code(), Objects.requireNonNullElse(failure.getMessage(), "none")),
      List.of(),
      List.of()
    );
  }

  private static StateSnapshot errorSnapshot(AdminCategoryException failure) {
    return new StateSnapshot(
      Map.of(
        "code",
        failure.code(),
        "status",
        Integer.toString(failure.status().value()),
        "message",
        Objects.requireNonNullElse(failure.getMessage(), "none")
      )
    );
  }

  private static OutputObservation withLogs(OutputObservation output, StructuredLogCapture logs) {
    List<String> logValues = logs
      .events()
      .stream()
      .flatMap(event -> event.searchableSurfaces().stream())
      .toList();
    return new OutputObservation(output.publicValues(), logValues, output.attachedCauses());
  }

  private static OutputObservation emptyOutput() {
    return new OutputObservation(List.of(), List.of(), List.of());
  }

  private static SeedRecord seed(Long id, String name, String slug, String updatedAt) {
    return new SeedRecord(
      id.toString(),
      Map.of(
        "name",
        name,
        "slug",
        slug,
        "active",
        "true",
        "createdAt",
        "2026-07-01T08:00:00Z",
        "updatedAt",
        updatedAt
      )
    );
  }

  private static AdminCategoryProviderContractProfile createProfile() {
    Set<Operation> declared = Set.of(Operation.CAPABILITIES, Operation.LIST, Operation.GET);
    Map<Operation, PortSlot> ports = Map.of(
      Operation.CAPABILITIES,
      PortSlot.NONE,
      Operation.LIST,
      PortSlot.CATEGORY_READ,
      Operation.GET,
      PortSlot.CATEGORY_READ
    );
    Map<Operation, OperationExpectation> expectations = new EnumMap<>(Operation.class);
    for (Operation operation : Operation.values()) {
      boolean supported = declared.contains(operation);
      expectations.put(
        operation,
        new OperationExpectation(
          "V(" + operation + ")",
          supported ? successResult(operation) : UNSUPPORTED_RESULT,
          supported ? AdminCategoryContractAssertions.NO_STABLE_ERROR : UNSUPPORTED_ERROR,
          supported ? AdminCategoryContractAssertions.NO_LOG_OUTCOME : UNSUPPORTED_LOG,
          new Applicability(
            true,
            supported
              ? "neutral flat declares the operation"
              : "neutral flat must reject the undeclared operation before port work"
          )
        )
      );
    }
    Map<Operation, Applicability> rollback = new EnumMap<>(Operation.class);
    declared.forEach(operation ->
      rollback.put(
        operation,
        new Applicability(false, "neutral flat declares no mutation operation")
      )
    );
    return new AdminCategoryProviderContractProfile(
      "neutral-flat",
      DEFINITION,
      NeutralFlatAdminCategoryContractFixture::newRegistration,
      declared,
      ports,
      DEFAULT_SEEDS,
      DEFAULT_ORDER,
      expectations,
      new StableErrorExpectation(
        "CATEGORY_NOT_FOUND",
        "category was not found",
        "GET is declared and the in-memory read port maps an absent identifier"
      ),
      rollback,
      new InteractionExpectation(
        Set.of("category-read.list", "category-read.getNode"),
        FORBIDDEN_INTERACTIONS
      ),
      List.of()
    );
  }

  private static String successResult(Operation operation) {
    return switch (operation) {
      case CAPABILITIES -> "200 AdminCategoryCapabilityResponse";
      case LIST -> "200 PageResponse<AdminCategoryListItemResponse>";
      case GET -> "200 AdminCategoryDetailResponse";
      default -> throw new IllegalArgumentException("operation is not supported by neutral flat");
    };
  }

  private static AdminCategorySystemRegistration registration(AdminCategoryReadPort readPort) {
    AdminCategoryDefinitionValidator validator = new AdminCategoryDefinitionValidator();
    AdminCategoryPolicyReference general = reference(
      AdminCategoryPolicyObligation.GENERAL_VALIDATION
    );
    AdminCategoryPolicyReference query = reference(AdminCategoryPolicyObligation.QUERY);
    AdminCategoryRuntimePolicyBindings policies = new AdminCategoryRuntimePolicyBindings(
      List.of(
        new GeneralValidationPolicyBinding(general, validator),
        new QueryPolicyBinding(query, validator)
      )
    );
    AdminCategoryRuntimePorts ports = new AdminCategoryRuntimePorts(
      Optional.of(readPort),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty(),
      Optional.empty()
    );
    AdminCategoryRuntimeBinding binding = new AdminCategoryRuntimeBinding(
      new NumericCodec(),
      permissionMapping(),
      policies,
      ports
    );
    return AdminCategorySystemRegistration.of(DEFINITION, binding);
  }

  private static AdminCategoryPolicyReference reference(AdminCategoryPolicyObligation obligation) {
    return new AdminCategoryPolicyReference(
      obligation,
      new AdminCategoryLogicalPolicyId("neutral-contract", obligation.code())
    );
  }

  private static AdminCategoryPermissionMapping permissionMapping() {
    return new AdminCategoryPermissionMapping(
      DEFINITION.operations()
        .supportedOperations()
        .stream()
        .map(operation -> new Binding(operation, "admin:" + SYSTEM_KEY + "-categories:read"))
        .toList()
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

  private static Long parsePositiveLong(String value, String name) {
    try {
      long parsed = Long.parseLong(value);
      if (parsed <= 0) {
        throw new IllegalArgumentException(name + " must be a positive numeric identifier");
      }
      return parsed;
    } catch (NumberFormatException failure) {
      throw new IllegalArgumentException(name + " must be a positive numeric identifier", failure);
    }
  }

  private final class FlatReadPort implements AdminCategoryReadPort {

    @Override
    public PageResult<CategoryNode> list(AdminCategoryListQuery query) {
      recordCall("category-read.list");
      List<Category> matches = records
        .values()
        .stream()
        .filter(category -> matches(category, query))
        .sorted(comparator(query.sort().orders()))
        .toList();
      int from = Math.min(query.page().page() * query.page().size(), matches.size());
      int to = Math.min(from + query.page().size(), matches.size());
      List<CategoryNode> content = matches
        .subList(from, to)
        .stream()
        .map(category -> new CategoryNode(category, 0, false))
        .toList();
      int pages = matches.isEmpty()
        ? 0
        : (int) Math.ceil((double) matches.size() / query.page().size());
      return new PageResult<>(
        content,
        query.page().page(),
        query.page().size(),
        matches.size(),
        pages,
        query.page().page() == 0,
        query.page().page() >= Math.max(pages - 1, 0),
        query.sort()
      );
    }

    @Override
    public CategoryNode getNode(Long id) {
      recordCall("category-read.getNode");
      Category category = records.get(id);
      if (category == null) {
        throw AdminCategoryErrors.categoryNotFound(id);
      }
      return new CategoryNode(category, 0, false);
    }

    @Override
    public Category get(Long id) {
      recordCall("category-read.get");
      Category category = records.get(id);
      if (category == null) {
        throw AdminCategoryErrors.categoryNotFound(id);
      }
      return category;
    }
  }

  private static final class DetachedReadPort implements AdminCategoryReadPort {

    @Override
    public PageResult<CategoryNode> list(AdminCategoryListQuery query) {
      throw new UnsupportedOperationException("detached registration port is metadata-only");
    }

    @Override
    public CategoryNode getNode(Long id) {
      throw new UnsupportedOperationException("detached registration port is metadata-only");
    }

    @Override
    public Category get(Long id) {
      throw new UnsupportedOperationException("detached registration port is metadata-only");
    }
  }

  private static final class NumericCodec implements AdminCategoryIdentifierCodec<Long> {

    @Override
    public AdminCategoryIdentifierProfile identifierProfile() {
      return AdminCategoryIdentifierProfile.NUMERIC_LONG_V1;
    }

    @Override
    public Long parse(String value) {
      return parsePositiveLong(value, "identifier");
    }

    @Override
    public String serialize(Long value) {
      if (value == null || value <= 0) {
        throw new IllegalArgumentException("identifier must be positive");
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
      authorities = Set.copyOf(Objects.requireNonNull(selectedAuthorities, "authorities"));
    }

    @Override
    public String toString() {
      return (
        "AuthorizationState[authenticated=" +
        authenticated +
        ", authorityCount=" +
        authorities.size() +
        "]"
      );
    }
  }
}
