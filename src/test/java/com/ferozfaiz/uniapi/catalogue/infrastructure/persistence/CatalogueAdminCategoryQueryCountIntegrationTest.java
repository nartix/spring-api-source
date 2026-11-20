package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;

import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryParentOptionsCursor;
import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryParentOptionsValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryTreeValidationInput;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimeBinding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimePorts;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract;
import com.ferozfaiz.uniapi.category.support.sql.CountingDataSource;
import com.ferozfaiz.uniapi.category.support.sql.SqlStatementObservation;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Runs the reusable query-count contract through the real catalogue application/JPA/Postgres path. */
@Execution(ExecutionMode.SAME_THREAD)
@Import(CatalogueAdminCategoryQueryCountIntegrationTest.ObservationConfiguration.class)
final class CatalogueAdminCategoryQueryCountIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
  implements AdminCategoryQueryCountContract
{

  private static final String SYSTEM_KEY = "catalogue";
  private static final AtomicReference<CountingDataSource> COUNTING_DATA_SOURCE =
    new AtomicReference<>();
  private static final AtomicReference<List<Object>> OBSERVED_RUNTIME_PORTS =
    new AtomicReference<>();

  @Autowired
  private AdminCategoryApplicationService applicationService;

  @Autowired
  private AdminCategorySystemRegistry registry;

  private SqlStatementObservation lastSqlObservation;

  @Override
  public List<QueryCountProfile> queryCountProfiles() {
    return List.of(profile());
  }

  @Test
  void exactLedgersRemainAvailableWhenBindListArityIsNormalizedForEquality() {
    StatementObservation one = measureList(1);
    List<String> oneExact = lastSqlObservation().normalizedFingerprints();
    List<String> oneNormalized = one.normalizedStatementFingerprints();

    StatementObservation twentyFive = measureList(25);
    List<String> twentyFiveExact = lastSqlObservation().normalizedFingerprints();
    List<String> twentyFiveNormalized = twentyFive.normalizedStatementFingerprints();

    assertThat(oneExact).hasSize(one.statementCount()).isNotEqualTo(twentyFiveExact);
    assertThat(twentyFiveExact).hasSize(twentyFive.statementCount());
    assertThat(oneNormalized).isEqualTo(twentyFiveNormalized);
    assertThat(one.statementCount()).isEqualTo(twentyFive.statementCount());
    assertThat(lastSqlObservation().statements()).allSatisfy(statement -> {
      assertThat(statement.batchSize()).isOne();
      assertThat(statement.sqlShapeCount()).isOne();
    });
  }

  @Test
  void selectedAndOrdinaryResultsUseThreeSetOrientedStatementsWithoutATotalCount() {
    seedRoots(25);

    StatementObservation observation = measure(() ->
      applicationService.parentOptions(SYSTEM_KEY, parentOptions(25, null, List.of("25"), null))
    );

    assertThat(observation.returnedCardinality()).isEqualTo(25);
    assertThat(observation.statementCount()).isEqualTo(3);
    assertMeasuredSelectShapesWithoutTotalCount(observation);
  }

  @Test
  void cursorContinuationIsLimitUnboundAndStalePositionsReturnAStableTerminalSlice() {
    seedRoots(4);
    var first = applicationService.parentOptions(
      SYSTEM_KEY,
      parentOptions(1, null, List.of(), null)
    );
    assertThat(first.data()).hasSize(1);
    assertThat(first.nextCursor()).isNotBlank();

    StatementObservation continued = measure(() ->
      applicationService.parentOptions(
        SYSTEM_KEY,
        parentOptions(2, first.nextCursor(), List.of(), null)
      )
    );
    assertThat(continued.returnedCardinality()).isEqualTo(2);
    assertMeasuredSelectShapesWithoutTotalCount(continued);

    beginSetup();
    String stale = AdminCatalogueCategoryParentOptionsCursor.encode(
      AdminCatalogueCategoryParentOptionsCursor.Mode.DEFAULT,
      null,
      true,
      null,
      null,
      false,
      "zzzzzz",
      Long.MAX_VALUE
    );
    StatementObservation staleFirst = measure(() ->
      applicationService.parentOptions(SYSTEM_KEY, parentOptions(25, stale, List.of(), null))
    );
    StatementObservation staleSecond = measure(() ->
      applicationService.parentOptions(SYSTEM_KEY, parentOptions(25, stale, List.of(), null))
    );

    assertThat(staleFirst.returnedCardinality()).isZero();
    assertThat(staleSecond).isEqualTo(staleFirst);
    assertThat(staleFirst.statementCount()).isOne();
    assertMeasuredSelectShapesWithoutTotalCount(staleSecond);
  }

  private QueryCountProfile profile() {
    Map<ReadPath, Applicability> readPaths = new EnumMap<>(ReadPath.class);
    for (ReadPath path : ReadPath.values()) {
      readPaths.put(
        path,
        new Applicability(
          true,
          "catalogue declares " +
            path.operation().code() +
            " and executes the selected catalogue JPA/Postgres port"
        )
      );
    }

    Map<BoundConcern, Applicability> bounds = new EnumMap<>(BoundConcern.class);
    for (BoundConcern concern : BoundConcern.values()) {
      bounds.put(
        concern,
        new Applicability(
          true,
          "catalogue publishes and executes the selected " + concern.id() + " limit"
        )
      );
    }

    return new QueryCountProfile(
      "catalogue-jpa",
      definition(),
      readPaths,
      bounds,
      List.of(
        new TwoCardinalityCase(ReadPath.LIST, this::measureList),
        new TwoCardinalityCase(ReadPath.COMPLETE_TREE, this::measureTree),
        new TwoCardinalityCase(ReadPath.BREADCRUMB, this::measureBreadcrumb),
        new TwoCardinalityCase(ReadPath.PARENT_OPTIONS, this::measureParentOptions),
        new TwoCardinalityCase(ReadPath.SELECTED_HYDRATION, this::measureSelectedHydration)
      ),
      List.of(),
      List.of(
        maximum(
          BoundConcern.LIST_PAGE,
          4,
          "category.api.list.size.max",
          true,
          this::listPageBoundary
        ),
        maximum(
          BoundConcern.SEARCH_LENGTH,
          4,
          "category.api.list.search.size",
          true,
          this::searchLengthBoundary
        ),
        maximum(
          BoundConcern.SORT_SELECTIONS,
          4,
          "category.api.list.sort.max",
          true,
          this::sortSelectionBoundary
        ),
        maximum(
          BoundConcern.COMPLETE_TREE,
          2,
          "CATEGORY_TREE_TOO_LARGE",
          false,
          this::completeTreeBoundary
        ),
        maximum(
          BoundConcern.PARENT_OPTIONS,
          2,
          "category.api.parent-options.limit.max",
          true,
          this::parentOptionBoundary
        ),
        maximum(
          BoundConcern.INCLUDE_IDS,
          2,
          "category.api.parent-options.include-ids.max",
          true,
          this::includeIdBoundary
        ),
        maximum(
          BoundConcern.HIERARCHY_DEPTH,
          1,
          ArithmeticException.class.getName(),
          true,
          this::hierarchyDepthBoundary
        )
      ),
      new FilterSelectionBoundaryCase(
        5,
        4,
        this::filterSelectionBoundary,
        this::filterSelectionStructure
      ),
      new CapabilityRepetitionCase(this::measureCapabilities),
      List.of(new ZeroStatementCase("catalogue-capabilities-zero-sql", this::measureOneCapability)),
      new HierarchyAbsenceCase(
        "catalogue-hierarchy-enabled",
        new Applicability(
          false,
          "catalogue enables hierarchy and executes its hierarchy read port"
        ),
        List.of(),
        () -> new InteractionObservation(0, List.of())
      )
    );
  }

  private StatementObservation measureList(int cardinality) {
    seedRoots(cardinality);
    return measure(() ->
      applicationService.list(
        SYSTEM_KEY,
        listInput(cardinality, null, null, null, null, List.of("id,asc"))
      )
    );
  }

  private StatementObservation measureTree(int cardinality) {
    seedRoots(cardinality);
    return measure(() ->
      applicationService.tree(
        SYSTEM_KEY,
        new AdminCategoryTreeValidationInput(definition(), 0, cardinality, List.of("id,asc"), null)
      )
    );
  }

  private StatementObservation measureBreadcrumb(int cardinality) {
    seedChain(cardinality);
    return measure(() -> applicationService.breadcrumb(SYSTEM_KEY, (long) cardinality));
  }

  private StatementObservation measureParentOptions(int cardinality) {
    seedRoots(cardinality);
    StatementObservation observation = measure(() ->
      applicationService.parentOptions(
        SYSTEM_KEY,
        parentOptions(cardinality, null, List.of(), null)
      )
    );
    assertMeasuredSelectShapesWithoutTotalCount(observation);
    return observation;
  }

  private StatementObservation measureSelectedHydration(int cardinality) {
    seedRoots(cardinality);
    StatementObservation observation = measure(() ->
      applicationService.parentOptions(
        SYSTEM_KEY,
        parentOptions(cardinality, null, ids(cardinality), null)
      )
    );
    assertMeasuredSelectShapesWithoutTotalCount(observation);
    return observation;
  }

  private BoundaryObservation listPageBoundary(long requested) {
    seedRoots(1);
    return boundary(requested, () ->
      applicationService.list(
        SYSTEM_KEY,
        listInput(Math.toIntExact(requested), null, null, null, null, List.of("id,asc"))
      )
    );
  }

  private BoundaryObservation searchLengthBoundary(long requested) {
    seedRoots(1);
    return boundary(requested, () ->
      applicationService.list(
        SYSTEM_KEY,
        listInput(20, null, null, "q".repeat(Math.toIntExact(requested)), null, List.of("id,asc"))
      )
    );
  }

  private BoundaryObservation sortSelectionBoundary(long requested) {
    seedRoots(1);
    List<String> selections = List.of(
      "parentId,asc",
      "name,asc",
      "slug,asc",
      "active,asc",
      "createdAt,asc",
      "updatedAt,asc"
    ).subList(0, Math.toIntExact(requested));
    return boundary(requested, () -> {
      var result = applicationService.list(
        SYSTEM_KEY,
        listInput(20, null, null, null, null, selections)
      );
      if (requested == BoundConcern.SORT_SELECTIONS.selectedLimit(definition())) {
        assertThat(result.sort().orders())
          .hasSize(Math.toIntExact(requested) + 1)
          .extracting(com.ferozfaiz.uniapi.shared.pagination.SortOrder::property)
          .endsWith("id");
      }
      return result;
    });
  }

  private BoundaryObservation completeTreeBoundary(long requested) {
    seedRoots(Math.toIntExact(requested));
    return boundary(requested, () ->
      applicationService.tree(
        SYSTEM_KEY,
        new AdminCategoryTreeValidationInput(
          definition(),
          0,
          Math.toIntExact(BoundConcern.COMPLETE_TREE.selectedLimit(definition())),
          List.of("id,asc"),
          null
        )
      )
    );
  }

  private BoundaryObservation parentOptionBoundary(long requested) {
    seedRoots((int) Math.min(requested, 50));
    return boundary(requested, () ->
      applicationService.parentOptions(
        SYSTEM_KEY,
        parentOptions(Math.toIntExact(requested), null, List.of(), null)
      )
    );
  }

  private BoundaryObservation includeIdBoundary(long requested) {
    seedRoots((int) Math.min(requested, 25));
    return boundary(requested, () ->
      applicationService.parentOptions(
        SYSTEM_KEY,
        parentOptions(25, null, ids(Math.toIntExact(requested)), null)
      )
    );
  }

  private BoundaryObservation hierarchyDepthBoundary(long requested) {
    beginSetup();
    truncateCategories();
    try {
      int depth = Math.toIntExact(requested);
      insertCategories(2);
      jdbc.update("UPDATE catalogue_category SET parent_id = 1 WHERE id = 2");
      jdbc.batchUpdate(
        "INSERT INTO catalogue_category_closure (ancestor_id, descendant_id, depth) VALUES (?, ?, ?)",
        List.of(
          new Object[] { 1L, 1L, 0 },
          new Object[] { 2L, 2L, 0 },
          new Object[] { 1L, 2L, depth }
        )
      );
    } catch (RuntimeException failure) {
      counting().reset();
      return failedBoundary(requested, failure);
    }
    return boundary(requested, () -> applicationService.breadcrumb(SYSTEM_KEY, 2L));
  }

  private BoundaryObservation filterSelectionBoundary(long requested) {
    beginSetup();
    truncateCategories();
    insertCategories(2);
    jdbc.update("UPDATE catalogue_category SET parent_id = 1 WHERE id = 2");
    jdbc.update(
      "UPDATE catalogue_category SET name = 'Filter Child', slug = 'filter-child' WHERE id = 2"
    );
    insertChainClosure(2);
    return boundary(requested, () ->
      applicationService.list(
        SYSTEM_KEY,
        listInput(20, "1", true, "filter", "filter-child", List.of("id,asc"))
      )
    );
  }

  private FilterSelectionStructureObservation filterSelectionStructure() {
    List<String> recordComponents = Arrays.stream(
      AdminCategoryListValidationInput.class.getRecordComponents()
    )
      .map(RecordComponent::getName)
      .toList();
    List<String> slots = List.of("parentId", "active", "q", "slug");
    boolean collectionPresent = Arrays.stream(
      AdminCategoryListValidationInput.class.getRecordComponents()
    ).anyMatch(
      component ->
        (component.getName().equals("filters") || component.getName().equals("filterSelections")) &&
        java.util.Collection.class.isAssignableFrom(component.getType())
    );
    assertThat(definition().fieldsAndQueries().allowedFilters()).contains(
      AdminCategorySystemDefinition.LogicalField.PARENT,
      AdminCategorySystemDefinition.LogicalField.ACTIVE,
      AdminCategorySystemDefinition.LogicalField.SLUG
    );
    assertThat(definition().fieldsAndQueries().searchableFields()).isNotEmpty();
    return new FilterSelectionStructureObservation(
      slots,
      slots.stream().filter(recordComponents::contains).toList(),
      List.of(),
      collectionPresent
    );
  }

  private CapabilityRepetitionObservation measureCapabilities(int repetitions) {
    beginSetup();
    clearInvocations(registry);
    clearRuntimePortInvocations();
    counting().reset();
    List<AdminCategorySystemDefinition> results = new ArrayList<>(repetitions);
    for (int index = 0; index < repetitions; index++) {
      results.add(applicationService.capabilities(SYSTEM_KEY));
    }
    SqlStatementObservation sql = finishMeasurement();
    int registryLookups = Math.toIntExact(
      mockingDetails(registry)
        .getInvocations()
        .stream()
        .filter(invocation -> invocation.getMethod().getName().equals("findRegistration"))
        .count()
    );
    List<String> runtimePortInteractions = runtimePortInteractions();
    return new CapabilityRepetitionObservation(
      repetitions,
      results,
      registryLookups,
      statementObservation(sql, repetitions, runtimePortInteractions),
      runtimePortInteractions
    );
  }

  private StatementObservation measureOneCapability() {
    return measureCapabilities(1).statements();
  }

  private BoundaryObservation boundary(long requested, Supplier<?> invocation) {
    clearRuntimePortInvocations();
    counting().reset();
    try {
      Object result = invocation.get();
      return new BoundaryObservation(
        requested,
        true,
        "success",
        statementObservation(finishMeasurement(), cardinality(result), runtimePortInteractions())
      );
    } catch (RuntimeException failure) {
      return failedBoundary(requested, failure);
    }
  }

  private BoundaryObservation failedBoundary(long requested, RuntimeException failure) {
    return new BoundaryObservation(
      requested,
      false,
      stableOutcome(failure),
      statementObservation(finishMeasurement(), 0, runtimePortInteractions())
    );
  }

  private StatementObservation measure(Supplier<?> invocation) {
    clearRuntimePortInvocations();
    counting().reset();
    Object result = invocation.get();
    return statementObservation(
      finishMeasurement(),
      cardinality(result),
      runtimePortInteractions()
    );
  }

  private SqlStatementObservation finishMeasurement() {
    lastSqlObservation = counting().measurementObservation();
    counting().beginTeardown();
    return lastSqlObservation;
  }

  private SqlStatementObservation lastSqlObservation() {
    if (lastSqlObservation == null) {
      throw new IllegalStateException("SQL measurement has not completed");
    }
    return lastSqlObservation;
  }

  private static StatementObservation statementObservation(
    SqlStatementObservation sql,
    int returnedCardinality,
    List<String> interactions
  ) {
    List<String> exactFingerprints = sql.normalizedFingerprints();
    List<String> normalizedFingerprints = sql.cardinalityNormalizedFingerprints();
    assertThat(exactFingerprints).hasSize(sql.statementCount());
    assertThat(normalizedFingerprints).hasSize(sql.statementCount());
    return new StatementObservation(
      sql.statementCount(),
      returnedCardinality,
      sql.statementKinds().stream().map(Enum::name).toList(),
      normalizedFingerprints,
      interactions
    );
  }

  private void assertMeasuredSelectShapesWithoutTotalCount(StatementObservation observation) {
    assertThat(observation.statementKinds()).containsOnly("SELECT");
    assertThat(lastSqlObservation().containsTotalCountQuery()).isFalse();
    assertThat(lastSqlObservation().statements()).allSatisfy(statement -> {
      assertThat(statement.batchSize()).isOne();
      assertThat(statement.sqlShapeCount()).isOne();
    });
  }

  private void seedRoots(int count) {
    beginSetup();
    truncateCategories();
    insertCategories(count);
    jdbc.update(
      "INSERT INTO catalogue_category_closure (ancestor_id, descendant_id, depth) SELECT id, id, 0 FROM catalogue_category"
    );
  }

  private void seedChain(int count) {
    beginSetup();
    truncateCategories();
    insertCategories(count);
    jdbc.update("UPDATE catalogue_category SET parent_id = id - 1 WHERE id > 1");
    insertChainClosure(count);
  }

  private void beginSetup() {
    counting().beginSetup();
  }

  private void truncateCategories() {
    jdbc.execute(
      "TRUNCATE TABLE catalogue_category_closure, catalogue_category RESTART IDENTITY CASCADE"
    );
  }

  private void insertCategories(int count) {
    jdbc.update(
      """
      INSERT INTO catalogue_category (
        id, parent_id, sort_rank, name, slug, is_active, created_at, updated_at
      )
      SELECT
        series,
        NULL,
        series * 1000000000::BIGINT,
        'Category ' || lpad(series::TEXT, 5, '0'),
        'category-' || lpad(series::TEXT, 5, '0'),
        TRUE,
        TIMESTAMPTZ '2026-01-01T00:00:00Z',
        TIMESTAMPTZ '2026-01-01T00:00:00Z'
      FROM generate_series(1, CAST(? AS INTEGER)) AS series
      """,
      count
    );
  }

  private void insertChainClosure(int count) {
    jdbc.update(
      """
      INSERT INTO catalogue_category_closure (ancestor_id, descendant_id, depth)
      SELECT ancestor, descendant, descendant - ancestor
      FROM generate_series(1, CAST(? AS INTEGER)) AS descendant
      CROSS JOIN LATERAL generate_series(1, descendant) AS ancestor
      """,
      count
    );
  }

  private AdminCategoryListValidationInput listInput(
    int size,
    String parentId,
    Boolean active,
    String q,
    String slug,
    List<String> sort
  ) {
    return new AdminCategoryListValidationInput(
      definition(),
      0,
      size,
      parentId,
      active,
      q,
      slug,
      sort,
      null
    );
  }

  private AdminCategoryParentOptionsValidationInput parentOptions(
    int limit,
    String cursor,
    List<String> includeIds,
    String q
  ) {
    return new AdminCategoryParentOptionsValidationInput(
      definition(),
      q,
      limit,
      cursor,
      includeIds,
      null,
      "true"
    );
  }

  private AdminCategorySystemDefinition definition() {
    return registry.findRegistration(SYSTEM_KEY).orElseThrow().definition().orElseThrow();
  }

  private CountingDataSource counting() {
    return java.util.Objects.requireNonNull(
      COUNTING_DATA_SOURCE.get(),
      "counting datasource was not installed"
    );
  }

  private static void clearRuntimePortInvocations() {
    clearInvocations(observedRuntimePorts().toArray());
  }

  private static List<String> runtimePortInteractions() {
    return observedRuntimePorts()
      .stream()
      .flatMap(observer -> mockingDetails(observer).getInvocations().stream())
      .map(invocation -> invocation.getMethod().getName())
      .toList();
  }

  private static List<Object> observedRuntimePorts() {
    return java.util.Objects.requireNonNull(
      OBSERVED_RUNTIME_PORTS.get(),
      "runtime port observers were not installed"
    );
  }

  private static AdminCategorySystemRegistry observeRuntimePorts(
    AdminCategorySystemRegistry registry
  ) {
    AdminCategorySystemRegistration registration = registry
      .findRegistration(SYSTEM_KEY)
      .orElseThrow();
    AdminCategoryRuntimeBinding binding = registration.runtimeBinding().orElseThrow();
    List<Object> observers = new ArrayList<>();
    AdminCategoryRuntimePorts ports = new AdminCategoryRuntimePorts(
      observe(binding.ports().categoryRead(), observers),
      observe(binding.ports().hierarchyRead(), observers),
      observe(binding.ports().basicMutation(), observers),
      observe(binding.ports().hierarchyMutation(), observers),
      observe(binding.ports().ordering(), observers),
      observe(binding.ports().lifecycle(), observers)
    );
    AdminCategorySystemRegistration observedRegistration = AdminCategorySystemRegistration.of(
      registration.definition().orElseThrow(),
      new AdminCategoryRuntimeBinding(
        binding.identifierCodec(),
        binding.permissionMapping(),
        binding.policyBindings(),
        ports
      )
    );
    AdminCategorySystemRegistry observedRegistry = spy(registry);
    doAnswer(invocation ->
      SYSTEM_KEY.equals(invocation.<String>getArgument(0))
        ? Optional.of(observedRegistration)
        : invocation.callRealMethod()
    )
      .when(observedRegistry)
      .findRegistration(anyString());
    OBSERVED_RUNTIME_PORTS.set(List.copyOf(observers));
    return observedRegistry;
  }

  private static <T> Optional<T> observe(Optional<T> port, List<Object> observers) {
    return port.map(value -> {
      T observer = spy(value);
      observers.add(observer);
      return observer;
    });
  }

  private static MaximumBoundCase maximum(
    BoundConcern concern,
    int maximumStatements,
    String oneOverOutcome,
    boolean failureBeforeSql,
    BoundaryMeasurement measurement
  ) {
    return new MaximumBoundCase(
      concern,
      maximumStatements,
      new OneOverExpectation(
        true,
        oneOverOutcome,
        failureBeforeSql,
        failureBeforeSql ? 0 : maximumStatements,
        "the real catalogue boundary rejects the selected-definition one-over value stably"
      ),
      measurement
    );
  }

  private static String stableOutcome(RuntimeException failure) {
    if (failure instanceof AdminCategoryException category) {
      return category.code();
    }
    if (failure instanceof InvalidRequestException invalid) {
      return invalid.messageKey();
    }
    return failure.getClass().getName();
  }

  private static int cardinality(Object result) {
    if (result instanceof com.ferozfaiz.uniapi.shared.pagination.PageResult<?> page) {
      return page.content().size();
    }
    if (
      result instanceof
        com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.ParentOptionSlice slice
    ) {
      return slice.data().size();
    }
    if (result instanceof List<?> list) {
      return list.size();
    }
    return result == null ? 0 : 1;
  }

  private static List<String> ids(int count) {
    List<String> ids = new ArrayList<>(count);
    for (int index = 1; index <= count; index++) {
      ids.add(Integer.toString(index));
    }
    return List.copyOf(ids);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ObservationConfiguration {

    @Bean
    static BeanPostProcessor countingDataSourcePostProcessor() {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
          if (bean instanceof DataSource dataSource && !(bean instanceof CountingDataSource)) {
            CountingDataSource counting = new CountingDataSource(dataSource);
            COUNTING_DATA_SOURCE.set(counting);
            return counting;
          }
          if (bean instanceof AdminCategorySystemRegistry registry) {
            return observeRuntimePorts(registry);
          }
          return bean;
        }
      };
    }
  }
}
