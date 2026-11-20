package com.ferozfaiz.uniapi.catalogue.application.provider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.ReorderCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminListCatalogueCategoriesQuery;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryBreadcrumbItem;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryNode;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOption;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOptionSlice;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryTreeNode;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryRepository;
import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
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
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierCodec;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryIdentifierProfile;
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
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Applies the shared provider contract to the production catalogue adapter. */
final class CatalogueAdminCategoryProviderContractTest implements AdminCategoryProviderContract {

  @Override
  public AdminCategoryProviderContractProfile profile() {
    return CatalogueContractFixture.PROFILE;
  }

  @Override
  public AdminCategoryProviderContractFixture newFixture() {
    return new CatalogueContractFixture();
  }

  private static final class CatalogueContractFixture
    implements AdminCategoryProviderContractFixture
  {

    private static final String SYSTEM_KEY = "catalogue";
    private static final Long SELECTED_ID = 10L;
    private static final OffsetDateTime MUTATION_TIME = OffsetDateTime.parse(
      "2026-07-16T18:00:00Z"
    );
    private static final List<SeedRecord> SEEDS = List.of(
      seed(10L, null, 1_000_000_000L, "Storage", "storage", true, "2026-07-16T12:00:00Z"),
      seed(11L, 10L, 2_000_000_000L, "Boxes", "boxes", true, "2026-07-15T12:00:00Z"),
      seed(12L, null, 3_000_000_000L, "Supplies", "supplies", true, "2026-07-15T12:00:00Z")
    );
    private static final List<String> EXPECTED_ORDER = List.of("10", "11", "12");
    private static final Set<String> FORBIDDEN_INTERACTIONS = Set.of(
      "non-mapped-ports.0",
      "other-registrations.0",
      "legacy-and-storage-registries.0",
      "fallbacks.0",
      "provider.fallback"
    );
    private static final Map<String, String> PROVIDER_CALLS = Map.ofEntries(
      Map.entry("list", "category-read.list"),
      Map.entry("tree", "hierarchy-read.tree"),
      Map.entry("parentOptions", "hierarchy-read.parentOptions"),
      Map.entry("getNode", "category-read.getNode"),
      Map.entry("breadcrumb", "hierarchy-read.breadcrumb"),
      Map.entry("create", "basic-mutation.create"),
      Map.entry("replace", "basic-mutation.replace"),
      Map.entry("patch", "basic-mutation.patch"),
      Map.entry("delete", "basic-mutation.delete"),
      Map.entry("move", "hierarchy-mutation.move"),
      Map.entry("reorder", "ordering.reorder"),
      Map.entry("activate", "lifecycle.activate"),
      Map.entry("deactivate", "lifecycle.deactivate")
    );
    private static final AdminCategoryProviderContractProfile PROFILE = profileDefinition();

    private final Map<Long, CatalogueCategory> records = new LinkedHashMap<>();
    private final CatalogueCategoryApplicationService catalogueService = mock(
      CatalogueCategoryApplicationService.class
    );
    private final CatalogueCategoryRepository repository = mock(CatalogueCategoryRepository.class);
    private final CatalogueAdminCategoryProvider provider = spy(
      new CatalogueAdminCategoryProvider(catalogueService, repository)
    );
    private final AdminCategorySystemRegistration registration = registration(provider);
    private final AdminCategorySystemRegistry registry = registry(registration);
    private final AdminCategoryApplicationService target = new AdminCategoryApplicationService(
      registry
    );
    private final RecordingTransactionManager transactionManager =
      new RecordingTransactionManager();
    private final AdminCategoryApplicationService applicationService = transactional(target);
    private final TransactionTemplate transactions = new TransactionTemplate(transactionManager);
    private final AuthorizationState authorization = new AuthorizationState();

    private boolean activeDuringProviderInvocation;
    private OutputObservation output = emptyOutput();

    private CatalogueContractFixture() {
      stubCatalogueCollaborators();
      reset();
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
      authorization.authenticated(
        Set.of("admin:catalogue-categories:read", "admin:catalogue-categories:write")
      );
      clearObservations();
    }

    @Override
    public void seed(List<SeedRecord> seeds) {
      records.clear();
      List.copyOf(seeds)
        .stream()
        .map(CatalogueContractFixture::category)
        .forEach(category -> {
          if (records.putIfAbsent(category.id(), category) != null) {
            throw new IllegalArgumentException("catalogue seed identifiers must be unique");
          }
        });
      clearObservations();
    }

    @Override
    public OperationObservation invoke(Operation operation) {
      Objects.requireNonNull(operation, "operation must not be null");
      clearObservations();
      InvocationResult result = null;
      Optional<String> stableError = Optional.empty();
      String publicMessage = "";
      try (
        StructuredLogCapture capture = StructuredLogCapture.attach(
          AdminCategoryApplicationService.class
        )
      ) {
        try {
          result = invokeApplication(applicationService, operation);
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
                Integer.toString(failure.status().value())
              )
            )
          );
        } finally {
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

    @Override
    public OperationObservation invokeThenFailAfterWrite(Operation operation) {
      if (!isMutation(operation)) {
        throw new IllegalArgumentException("rollback applies only to catalogue mutations");
      }
      clearObservations();
      try (
        StructuredLogCapture capture = StructuredLogCapture.attach(
          AdminCategoryApplicationService.class
        )
      ) {
        try {
          transactions.executeWithoutResult(ignored -> {
            invokeApplication(target, operation);
            throw new ControlledOuterFailure();
          });
          throw new AssertionError("controlled outer failure was not raised");
        } catch (ControlledOuterFailure expected) {
          StateSnapshot rolledBack = snapshot();
          Optional<String> stableError = Optional.of("CONTROLLED_OUTER_FAILURE");
          InvocationResult result = new InvocationResult("rollback", rolledBack);
          output = output(result, stableError, expected.getMessage(), capture.events());
          return new OperationObservation("rollback", stableError, rolledBack, output);
        }
      }
    }

    @Override
    public StateSnapshot snapshot() {
      return snapshot("public-state");
    }

    @Override
    public InteractionLedger interactions() {
      List<String> calls = mockingDetails(provider)
        .getInvocations()
        .stream()
        .map(invocation -> PROVIDER_CALLS.get(invocation.getMethod().getName()))
        .filter(Objects::nonNull)
        .toList();
      List<String> unexpected = mockingDetails(provider)
        .getInvocations()
        .stream()
        .map(invocation -> invocation.getMethod().getName())
        .filter(name -> !PROVIDER_CALLS.containsKey(name) && !name.equals("toString"))
        .map(name -> "unexpected-provider." + name)
        .toList();
      return new InteractionLedger(calls, unexpected);
    }

    @Override
    public TransactionObservation transactionObservation() {
      return new TransactionObservation(
        transactionManager.commits,
        transactionManager.rollbacks,
        activeDuringProviderInvocation || transactionManager.activeDuringInvocation
      );
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
      return snapshot("catalogue-mock-state");
    }

    private InvocationResult invokeApplication(
      AdminCategoryApplicationService service,
      Operation operation
    ) {
      return switch (operation) {
        case CAPABILITIES -> capabilities(service);
        case LIST -> page(
          operation,
          service.list(
            SYSTEM_KEY,
            new AdminCategoryListValidationInput(
              registration.definition().orElseThrow(),
              0,
              100,
              null,
              null,
              null,
              null,
              List.of(),
              null
            )
          )
        );
        case TREE -> page(
          operation,
          service.tree(
            SYSTEM_KEY,
            new AdminCategoryTreeValidationInput(
              registration.definition().orElseThrow(),
              0,
              100,
              List.of(),
              null
            )
          )
        );
        case PARENT_OPTIONS -> parentOptions(
          service.parentOptions(
            SYSTEM_KEY,
            new AdminCategoryParentOptionsValidationInput(
              registration.definition().orElseThrow(),
              null,
              25,
              null,
              List.of(),
              null,
              "true"
            )
          )
        );
        case GET -> category(operation, service.getNode(SYSTEM_KEY, SELECTED_ID).category());
        case BREADCRUMB -> breadcrumb(service.breadcrumb(SYSTEM_KEY, SELECTED_ID));
        case CREATE -> category(
          operation,
          service.create(
            SYSTEM_KEY,
            new AdminCategoryCreateValidationInput(
              registration.definition().orElseThrow(),
              null,
              null,
              null,
              null,
              "Created",
              "created",
              true
            )
          )
        );
        case REPLACE -> category(
          operation,
          service.update(
            SYSTEM_KEY,
            SELECTED_ID,
            new AdminCategoryReplaceValidationInput(
              registration.definition().orElseThrow(),
              SELECTED_ID,
              null,
              "Replaced",
              "replaced",
              true
            )
          )
        );
        case PATCH -> category(
          operation,
          service.patch(
            SYSTEM_KEY,
            SELECTED_ID,
            new AdminCategoryPatchValidationInput(
              registration.definition().orElseThrow(),
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
          )
        );
        case DELETE -> {
          service.delete(SYSTEM_KEY, SELECTED_ID);
          yield status(operation);
        }
        case MOVE -> {
          service.move(SYSTEM_KEY, SELECTED_ID, placement(Operation.MOVE));
          yield status(operation);
        }
        case REORDER -> category(
          operation,
          service.reorder(SYSTEM_KEY, SELECTED_ID, placement(Operation.REORDER))
        );
        case ACTIVATE -> {
          service.activate(SYSTEM_KEY, SELECTED_ID);
          yield status(operation);
        }
        case DEACTIVATE -> {
          service.deactivate(SYSTEM_KEY, SELECTED_ID);
          yield status(operation);
        }
      };
    }

    private InvocationResult capabilities(AdminCategoryApplicationService service) {
      AdminCategorySystemDefinition definition = service.capabilities(SYSTEM_KEY);
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

    private AdminCategoryPlacementValidationInput placement(Operation operation) {
      return new AdminCategoryPlacementValidationInput(
        registration.definition().orElseThrow(),
        operation,
        null,
        null,
        null,
        null
      );
    }

    private void stubCatalogueCollaborators() {
      when(catalogueService.findAllNodes(any(AdminListCatalogueCategoriesQuery.class))).thenAnswer(
        invocation -> catalogueNodes(invocation.getArgument(0))
      );
      when(repository.findTree(any(PageCriteria.class), any(SortCriteria.class))).thenAnswer(
        invocation -> catalogueTree(invocation.getArgument(0), invocation.getArgument(1))
      );
      when(catalogueService.getNode(any(Long.class))).thenAnswer(invocation -> {
        observeTransaction();
        CatalogueCategory selected = requireCategory(invocation.getArgument(0));
        return new CatalogueCategoryNode(selected, depth(selected), hasChildren(selected.id()));
      });
      when(catalogueService.get(any(Long.class))).thenAnswer(invocation -> {
        observeTransaction();
        return requireCategory(invocation.getArgument(0));
      });
      when(catalogueService.findBreadcrumb(any(Long.class))).thenAnswer(invocation ->
        catalogueBreadcrumb(invocation.getArgument(0))
      );
      when(
        catalogueService.findParentOptions(any(AdminCatalogueCategoryParentOptionsQuery.class))
      ).thenAnswer(invocation -> catalogueParentOptions());
      when(catalogueService.create(any(UpsertCatalogueCategoryCommand.class))).thenAnswer(
        invocation -> create(invocation.getArgument(0))
      );
      when(catalogueService.update(any(UpsertCatalogueCategoryCommand.class))).thenAnswer(
        invocation -> update(invocation.getArgument(0))
      );
      doAnswer(invocation -> {
        move(invocation.getArgument(0), invocation.getArgument(1));
        return null;
      })
        .when(catalogueService)
        .move(any(Long.class), any(), any());
      when(
        catalogueService.reorder(any(Long.class), any(ReorderCatalogueCategoryCommand.class))
      ).thenAnswer(invocation -> reorder(invocation.getArgument(0), invocation.getArgument(1)));
      when(catalogueService.activate(any(Long.class))).thenAnswer(invocation ->
        lifecycle(invocation.getArgument(0), true)
      );
      when(catalogueService.deactivate(any(Long.class))).thenAnswer(invocation ->
        lifecycle(invocation.getArgument(0), false)
      );
      doAnswer(invocation -> {
        observeTransaction();
        requireCategory(invocation.getArgument(0));
        records.remove(invocation.<Long>getArgument(0));
        return null;
      })
        .when(catalogueService)
        .delete(any(Long.class));
    }

    private PageResult<CatalogueCategoryNode> catalogueNodes(
      AdminListCatalogueCategoriesQuery query
    ) {
      observeTransaction();
      List<CatalogueCategoryNode> nodes = records
        .values()
        .stream()
        .filter(category -> matches(category, query))
        .sorted(comparator(query.sort()))
        .map(category ->
          new CatalogueCategoryNode(category, depth(category), hasChildren(category.id()))
        )
        .toList();
      return page(nodes, query.page(), query.sort());
    }

    private PageResult<CatalogueCategoryTreeNode> catalogueTree(
      PageCriteria page,
      SortCriteria sort
    ) {
      observeTransaction();
      List<CatalogueCategoryTreeNode> nodes = records
        .values()
        .stream()
        .sorted(comparator(sort))
        .map(category ->
          new CatalogueCategoryTreeNode(category, depth(category), hasChildren(category.id()))
        )
        .toList();
      return page(nodes, page, sort);
    }

    private List<CatalogueCategoryBreadcrumbItem> catalogueBreadcrumb(Long id) {
      observeTransaction();
      List<CatalogueCategory> reversed = new ArrayList<>();
      CatalogueCategory selected = requireCategory(id);
      while (selected != null) {
        reversed.add(selected);
        selected = selected.parentId() == null ? null : requireCategory(selected.parentId());
      }
      java.util.Collections.reverse(reversed);
      List<CatalogueCategoryBreadcrumbItem> result = new ArrayList<>();
      for (int index = 0; index < reversed.size(); index++) {
        result.add(new CatalogueCategoryBreadcrumbItem(reversed.get(index), index));
      }
      return List.copyOf(result);
    }

    private CatalogueCategoryParentOptionSlice catalogueParentOptions() {
      observeTransaction();
      List<CatalogueCategoryParentOption> options = records
        .values()
        .stream()
        .sorted(Comparator.comparing(CatalogueCategory::name))
        .map(category ->
          CatalogueCategoryParentOption.from(
            category,
            depth(category),
            catalogueBreadcrumb(category.id())
          )
        )
        .toList();
      return new CatalogueCategoryParentOptionSlice(options, false, null);
    }

    private CatalogueCategory create(UpsertCatalogueCategoryCommand command) {
      observeTransaction();
      long id = records.keySet().stream().mapToLong(Long::longValue).max().orElse(0L) + 1L;
      CatalogueCategory created = new CatalogueCategory(
        id,
        command.parentId(),
        4_000_000_000L,
        command.name(),
        command.slug(),
        command.active(),
        MUTATION_TIME,
        MUTATION_TIME
      );
      records.put(id, created);
      return created;
    }

    private CatalogueCategory update(UpsertCatalogueCategoryCommand command) {
      observeTransaction();
      CatalogueCategory existing = requireCategory(command.id());
      CatalogueCategory updated = new CatalogueCategory(
        existing.id(),
        command.parentId(),
        existing.sortRank(),
        command.name(),
        command.slug(),
        command.active(),
        existing.createdAt(),
        MUTATION_TIME
      );
      records.put(updated.id(), updated);
      return updated;
    }

    private void move(Long id, Long parentId) {
      observeTransaction();
      CatalogueCategory existing = requireCategory(id);
      records.put(
        id,
        new CatalogueCategory(
          id,
          parentId,
          existing.sortRank(),
          existing.name(),
          existing.slug(),
          existing.active(),
          existing.createdAt(),
          MUTATION_TIME
        )
      );
    }

    private CatalogueCategory reorder(Long id, ReorderCatalogueCategoryCommand command) {
      observeTransaction();
      CatalogueCategory existing = requireCategory(id);
      CatalogueCategory reordered = new CatalogueCategory(
        id,
        command.parentId(),
        9_000_000_000L,
        existing.name(),
        existing.slug(),
        existing.active(),
        existing.createdAt(),
        MUTATION_TIME
      );
      records.put(id, reordered);
      return reordered;
    }

    private CatalogueCategory lifecycle(Long id, boolean active) {
      observeTransaction();
      CatalogueCategory existing = requireCategory(id);
      CatalogueCategory updated = new CatalogueCategory(
        id,
        existing.parentId(),
        existing.sortRank(),
        existing.name(),
        existing.slug(),
        active,
        existing.createdAt(),
        MUTATION_TIME
      );
      records.put(id, updated);
      return updated;
    }

    private CatalogueCategory requireCategory(Long id) {
      CatalogueCategory category = records.get(id);
      if (category == null) {
        throw CatalogueNotFoundException.category(id.toString());
      }
      return category;
    }

    private void observeTransaction() {
      activeDuringProviderInvocation |=
        TransactionSynchronizationManager.isActualTransactionActive();
    }

    private void clearObservations() {
      clearInvocations(provider, catalogueService, repository);
      transactionManager.resetObservations();
      activeDuringProviderInvocation = false;
      output = emptyOutput();
    }

    private StateSnapshot snapshot(String kind) {
      String rows = records
        .values()
        .stream()
        .sorted(Comparator.comparing(CatalogueCategory::id))
        .map(category ->
          String.join(
            "|",
            category.id().toString(),
            Objects.toString(category.parentId(), "root"),
            Objects.toString(category.sortRank(), "unranked"),
            category.name(),
            category.slug(),
            Boolean.toString(category.active()),
            category.createdAt().toString(),
            category.updatedAt().toString()
          )
        )
        .collect(java.util.stream.Collectors.joining(","));
      return new StateSnapshot(
        Map.of("kind", kind, "rowCount", Integer.toString(records.size()), "rows", rows)
      );
    }

    private int depth(CatalogueCategory category) {
      int depth = 0;
      Long parentId = category.parentId();
      while (parentId != null) {
        depth++;
        parentId = requireCategory(parentId).parentId();
      }
      return depth;
    }

    private boolean hasChildren(Long id) {
      return records
        .values()
        .stream()
        .anyMatch(category -> id.equals(category.parentId()));
    }

    private static boolean matches(
      CatalogueCategory category,
      AdminListCatalogueCategoriesQuery query
    ) {
      if (
        query.filters().active() != null &&
        category.active() != query.filters().active().booleanValue()
      ) {
        return false;
      }
      if (query.filters().slug() != null && !query.filters().slug().equals(category.slug())) {
        return false;
      }
      if (query.filters().q() != null) {
        String value = query.filters().q().toLowerCase(Locale.ROOT);
        if (
          !category.name().toLowerCase(Locale.ROOT).contains(value) &&
          !category.slug().toLowerCase(Locale.ROOT).contains(value)
        ) {
          return false;
        }
      }
      return switch (query.parentScope().mode()) {
        case ALL -> true;
        case ROOTS -> category.parentId() == null;
        case CHILDREN -> Objects.equals(category.parentId(), query.parentScope().parentId());
      };
    }

    private static Comparator<CatalogueCategory> comparator(SortCriteria sort) {
      Comparator<CatalogueCategory> result = null;
      for (SortOrder order : sort.orders()) {
        Comparator<CatalogueCategory> selected = comparator(order.property());
        if (order.direction() == SortDirection.DESC) {
          selected = selected.reversed();
        }
        result = result == null ? selected : result.thenComparing(selected);
      }
      return Objects.requireNonNullElseGet(result, () ->
        Comparator.comparing(CatalogueCategory::updatedAt)
          .reversed()
          .thenComparing(CatalogueCategory::id)
      );
    }

    private static Comparator<CatalogueCategory> comparator(String property) {
      return switch (property) {
        case "id" -> Comparator.comparing(CatalogueCategory::id);
        case "parentId" -> Comparator.comparing(
          CatalogueCategory::parentId,
          Comparator.nullsFirst(Comparator.naturalOrder())
        );
        case "name" -> Comparator.comparing(CatalogueCategory::name);
        case "slug" -> Comparator.comparing(CatalogueCategory::slug);
        case "active" -> Comparator.comparing(CatalogueCategory::active);
        case "createdAt" -> Comparator.comparing(CatalogueCategory::createdAt);
        case "updatedAt" -> Comparator.comparing(CatalogueCategory::updatedAt);
        case "sortRank" -> Comparator.comparing(
          CatalogueCategory::sortRank,
          Comparator.nullsLast(Comparator.naturalOrder())
        );
        default -> throw new IllegalArgumentException("unsupported catalogue sort property");
      };
    }

    private static <T> PageResult<T> page(
      List<T> values,
      PageCriteria criteria,
      SortCriteria sort
    ) {
      int from = Math.min(criteria.page() * criteria.size(), values.size());
      int to = Math.min(from + criteria.size(), values.size());
      int totalPages = values.isEmpty()
        ? 0
        : (int) Math.ceil((double) values.size() / criteria.size());
      return new PageResult<>(
        values.subList(from, to),
        criteria.page(),
        criteria.size(),
        values.size(),
        totalPages,
        criteria.page() == 0,
        totalPages == 0 || criteria.page() >= totalPages - 1,
        sort
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

    private static OutputObservation emptyOutput() {
      return new OutputObservation(List.of(), List.of(), List.of());
    }

    private static AdminCategoryProviderContractProfile profileDefinition() {
      AdminCategorySystemRegistration registration = registrationOnly();
      AdminCategorySystemDefinition definition = registration.definition().orElseThrow();
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
            new Applicability(true, "catalogue declares and implements " + operation.code())
          )
        );
        rollback.put(
          operation,
          new Applicability(
            isMutation(operation),
            isMutation(operation)
              ? "catalogue mutation participates in a transaction"
              : "catalogue read performs no mutation to roll back"
          )
        );
      }
      return new AdminCategoryProviderContractProfile(
        SYSTEM_KEY,
        definition,
        CatalogueContractFixture::registrationOnly,
        operations,
        ports,
        SEEDS,
        EXPECTED_ORDER,
        expectations,
        new StableErrorExpectation(
          "CATEGORY_NOT_FOUND",
          "category was not found",
          "catalogue GET maps the production catalogue not-found exception"
        ),
        rollback,
        new InteractionExpectation(Set.copyOf(PROVIDER_CALLS.values()), FORBIDDEN_INTERACTIONS),
        List.of(
          new QueryCountExpectation(
            "catalogue-mock-backed-adapter",
            1,
            25,
            0,
            new Applicability(
              false,
              "query counting is N/A for this deterministic Mockito adapter contract; catalogue JPA query suites provide SQL evidence"
            )
          )
        )
      );
    }

    private static AdminCategorySystemRegistration registrationOnly() {
      return registration(
        new CatalogueAdminCategoryProvider(
          mock(CatalogueCategoryApplicationService.class),
          mock(CatalogueCategoryRepository.class)
        )
      );
    }

    private static AdminCategorySystemRegistration registration(
      CatalogueAdminCategoryProvider provider
    ) {
      AdminCategoryDefinitionValidator validator = new AdminCategoryDefinitionValidator();
      return new CatalogueAdminCategoryRegistrationConfiguration().catalogueAdminCategorySystemRegistration(
        provider,
        new NumericIdentifierCodec(),
        validator,
        validator,
        validator,
        validator,
        validator,
        validator,
        validator
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

    private AdminCategoryApplicationService transactional(
      AdminCategoryApplicationService selectedTarget
    ) {
      ProxyFactory proxyFactory = new ProxyFactory(selectedTarget);
      proxyFactory.addAdvice(
        new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource())
      );
      return (AdminCategoryApplicationService) proxyFactory.getProxy();
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
      boolean active,
      String updatedAt
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
          updatedAt
        )
      );
    }

    private static CatalogueCategory category(SeedRecord seed) {
      Map<String, String> attributes = seed.attributes();
      String parentId = attributes.get("parentId");
      return new CatalogueCategory(
        Long.valueOf(seed.recordId()),
        parentId == null || parentId.equals("root") ? null : Long.valueOf(parentId),
        Long.valueOf(required(attributes, "sortRank")),
        required(attributes, "name"),
        required(attributes, "slug"),
        Boolean.parseBoolean(required(attributes, "active")),
        OffsetDateTime.parse(required(attributes, "createdAt")),
        OffsetDateTime.parse(required(attributes, "updatedAt"))
      );
    }

    private static String required(Map<String, String> values, String key) {
      return Objects.requireNonNull(values.get(key), "missing catalogue seed attribute " + key);
    }

    private final class RecordingTransactionManager extends AbstractPlatformTransactionManager {

      private Map<Long, CatalogueCategory> before = Map.of();
      private int commits;
      private int rollbacks;
      private boolean activeDuringInvocation;

      @Override
      protected Object doGetTransaction() {
        return new Object();
      }

      @Override
      protected void doBegin(Object transaction, TransactionDefinition definition) {
        before = Map.copyOf(records);
        activeDuringInvocation = true;
      }

      @Override
      protected void doCommit(DefaultTransactionStatus status) {
        commits++;
      }

      @Override
      protected void doRollback(DefaultTransactionStatus status) {
        records.clear();
        records.putAll(before);
        rollbacks++;
      }

      private void resetObservations() {
        before = Map.of();
        commits = 0;
        rollbacks = 0;
        activeDuringInvocation = false;
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

    private static final class NumericIdentifierCodec
      implements AdminCategoryIdentifierCodec<Long>
    {

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

    private static final class ControlledOuterFailure extends RuntimeException {

      private ControlledOuterFailure() {
        super("controlled outer failure");
      }
    }

    private record InvocationResult(String resultId, StateSnapshot publicResult) {}
  }
}
