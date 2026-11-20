package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;

import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.ParentOptionSlice;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryParentOptionsValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryTreeValidationInput;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentOptionsQuery;
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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Runs the reusable query-count contract through the real descriptor application/JDBC path. */
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
final class DescriptorAdminCategoryQueryCountIntegrationTest
  implements AdminCategoryQueryCountContract
{

  private static final String SYSTEM_KEY = DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A;
  private static final AtomicReference<CountingDataSource> COUNTING_DATA_SOURCE =
    new AtomicReference<>();
  private static final AtomicReference<AdminCategorySystemRegistry> OBSERVED_REGISTRY =
    new AtomicReference<>();
  private static final AtomicReference<List<Object>> OBSERVED_RUNTIME_PORTS =
    new AtomicReference<>();

  @Autowired
  private AdminCategoryApplicationService applicationService;

  @Autowired
  private DescriptorDrivenAdminCategoryJdbcProviderFactory factory;

  @Autowired
  private JdbcTemplate jdbc;

  @Override
  public List<QueryCountProfile> queryCountProfiles() {
    return List.of(profile());
  }

  @Test
  void exactLedgersRemainAvailableWhenSelectedBindArityIsNormalizedForEquality() {
    StatementObservation one = measureSelectedHydration(1);
    List<String> oneExact = lastSqlObservation().normalizedFingerprints();

    StatementObservation twentyFive = measureSelectedHydration(25);
    List<String> twentyFiveExact = lastSqlObservation().normalizedFingerprints();

    assertThat(oneExact).hasSize(one.statementCount()).isNotEqualTo(twentyFiveExact);
    assertThat(twentyFiveExact).hasSize(twentyFive.statementCount());
    assertThat(oneExact.getFirst()).isNotEqualTo(twentyFiveExact.getFirst());
    assertThat(one.normalizedStatementFingerprints().getFirst()).isEqualTo(
      twentyFive.normalizedStatementFingerprints().getFirst()
    );
  }

  @Test
  void selectedAndOrdinaryResultsUseThreeSetOrientedStatementsWithoutATotalCount() {
    seedRoots(25);

    StatementObservation observation = measure(() ->
      applicationService.parentOptions(SYSTEM_KEY, parentOptions(25, null, List.of("25"), null))
    );

    assertThat(observation.returnedCardinality()).isEqualTo(25);
    assertThat(observation.statementCount()).isEqualTo(3);
    assertThat(lastSqlObservation().containsTotalCountQuery()).isFalse();
    assertMeasuredSelectShapes(observation);
  }

  @Test
  void duplicateSelectedRowsReuseOneDistinctBreadcrumbHydrationBind() {
    seedRoots(1);
    AtomicReference<ParentOptionSlice> result = new AtomicReference<>();

    StatementObservation observation = measure(() -> {
      ParentOptionSlice slice = registration()
        .runtimeBinding()
        .orElseThrow()
        .ports()
        .hierarchyRead()
        .orElseThrow()
        .parentOptions(
          new AdminCategoryParentOptionsQuery(null, 2, null, List.of(1L, 1L), null, true)
        );
      result.set(slice);
      return slice;
    });

    assertThat(result.get().data())
      .extracting(option -> option.category().id())
      .containsExactly(1L, 1L);
    assertThat(observation.statementCount()).isEqualTo(2);
    assertThat(
      lastSqlObservation().containsSqlFragment("where closure.descendant_id in (?)")
    ).isTrue();
  }

  @Test
  void parentOptionCursorRemainsLimitUnboundAndStalePositionsRemainOrdinaryContinuations() {
    seedRoots(4);

    StatementObservation first = measure(() ->
      applicationService.parentOptions(SYSTEM_KEY, parentOptions(1, null, List.of(), null))
    );
    String cursor = applicationService
      .parentOptions(SYSTEM_KEY, parentOptions(1, null, List.of(), null))
      .nextCursor();
    assertThat(cursor).isNotBlank();

    StatementObservation changedLimit = measure(() ->
      applicationService.parentOptions(SYSTEM_KEY, parentOptions(2, cursor, List.of(), null))
    );
    assertThat(changedLimit.returnedCardinality()).isEqualTo(2);
    assertThat(first.statementCount()).isGreaterThan(0);
    assertThat(lastSqlObservation().containsTotalCountQuery()).isFalse();

    var context = new AdminCategoryParentOptionsCursorCodec.RequestContext(
      null,
      true,
      null,
      List.of()
    );
    String stale = new AdminCategoryParentOptionsCursorCodec().encode(
      SYSTEM_KEY,
      context,
      new AdminCategoryParentOptionsCursorCodec.Position(0, true, "category-00002", 9_999L)
    );
    StatementObservation staleFirst = measure(() ->
      applicationService.parentOptions(SYSTEM_KEY, parentOptions(4, stale, List.of(), null))
    );
    StatementObservation staleSecond = measure(() ->
      applicationService.parentOptions(SYSTEM_KEY, parentOptions(4, stale, List.of(), null))
    );
    assertThat(staleSecond).isEqualTo(staleFirst);
  }

  private QueryCountProfile profile() {
    AdminCategorySystemDefinition definition = definition();
    Map<ReadPath, Applicability> readPaths = new EnumMap<>(ReadPath.class);
    for (ReadPath path : ReadPath.values()) {
      readPaths.put(
        path,
        new Applicability(
          true,
          "descriptor-a declares " + path.operation().code() + " and executes its real JDBC port"
        )
      );
    }
    Map<BoundConcern, Applicability> bounds = new EnumMap<>(BoundConcern.class);
    for (BoundConcern concern : BoundConcern.values()) {
      bounds.put(
        concern,
        new Applicability(
          true,
          "descriptor-a publishes and executes the selected " + concern.id() + " limit"
        )
      );
    }

    return new QueryCountProfile(
      "descriptor-jdbc",
      definition,
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
          2,
          "category.api.list.size.max",
          true,
          this::listPageBoundary
        ),
        maximum(
          BoundConcern.SEARCH_LENGTH,
          2,
          "category.api.list.search.size",
          true,
          this::searchLengthBoundary
        ),
        maximum(
          BoundConcern.SORT_SELECTIONS,
          2,
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
          51,
          "category.api.parent-options.limit.max",
          true,
          this::parentOptionBoundary
        ),
        maximum(
          BoundConcern.INCLUDE_IDS,
          26,
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
        3,
        4,
        this::filterSelectionBoundary,
        this::filterSelectionStructure
      ),
      new CapabilityRepetitionCase(this::measureCapabilities),
      List.of(),
      new HierarchyAbsenceCase(
        "hierarchy-enabled",
        new Applicability(
          false,
          "descriptor-a enables hierarchy and executes the hierarchy read port"
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
        new AdminCategoryListValidationInput(
          definition(),
          0,
          cardinality,
          null,
          null,
          null,
          null,
          List.of("id,asc"),
          null
        )
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
    seedChain(cardinality, false);
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
    assertThat(lastSqlObservation().containsTotalCountQuery()).isFalse();
    assertMeasuredSelectShapes(observation);
    return observation;
  }

  private StatementObservation measureSelectedHydration(int cardinality) {
    seedRoots(cardinality);
    List<String> includeIds = ids(cardinality);
    StatementObservation observation = measure(() ->
      applicationService.parentOptions(
        SYSTEM_KEY,
        parentOptions(cardinality, null, includeIds, null)
      )
    );
    assertThat(lastSqlObservation().containsTotalCountQuery()).isFalse();
    assertMeasuredSelectShapes(observation);
    return observation;
  }

  private BoundaryObservation listPageBoundary(long requested) {
    seedRoots(1);
    return boundary(requested, () ->
      applicationService.list(
        SYSTEM_KEY,
        new AdminCategoryListValidationInput(
          definition(),
          0,
          Math.toIntExact(requested),
          null,
          null,
          null,
          null,
          List.of("id,asc"),
          null
        )
      )
    );
  }

  private BoundaryObservation searchLengthBoundary(long requested) {
    seedRoots(1);
    return boundary(requested, () ->
      applicationService.list(
        SYSTEM_KEY,
        new AdminCategoryListValidationInput(
          definition(),
          0,
          20,
          null,
          null,
          "a".repeat(Math.toIntExact(requested)),
          null,
          List.of("id,asc"),
          null
        )
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
      var page = applicationService.list(
        SYSTEM_KEY,
        new AdminCategoryListValidationInput(
          definition(),
          0,
          20,
          null,
          null,
          null,
          null,
          selections,
          null
        )
      );
      assertThat(page.sort().orders()).hasSize(Math.toIntExact(requested) + 1);
      assertThat(page.sort().orders().getLast().property()).isEqualTo("id");
      return page;
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
    recreateSchema();
    try {
      int depth = Math.toIntExact(requested);
      insertNodes(
        List.of(
          node(1L, null, "depth-root", "depth-root"),
          node(2L, 1L, "depth-leaf", "depth-leaf")
        )
      );
      insertClosures(
        List.of(
          new ClosureSeed(1L, 1L, 0),
          new ClosureSeed(2L, 2L, 0),
          new ClosureSeed(1L, 2L, depth)
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
    recreateSchema();
    insertNodes(
      List.of(
        node(1L, null, "filter-parent", "filter-parent"),
        node(2L, 1L, "filter-child", "filter-child")
      )
    );
    insertClosures(
      List.of(new ClosureSeed(1L, 1L, 0), new ClosureSeed(2L, 2L, 0), new ClosureSeed(1L, 2L, 1))
    );
    return boundary(requested, () ->
      applicationService.list(
        SYSTEM_KEY,
        new AdminCategoryListValidationInput(
          definition(),
          0,
          20,
          "1",
          true,
          "filter-child",
          "filter-child",
          List.of("id,asc"),
          null
        )
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
    boolean selectionCollectionPresent = Arrays.stream(
      AdminCategoryListValidationInput.class.getRecordComponents()
    ).anyMatch(
      component ->
        (component.getName().equals("filters") || component.getName().equals("filterSelections")) &&
        java.util.Collection.class.isAssignableFrom(component.getType())
    );
    return new FilterSelectionStructureObservation(
      slots,
      slots.stream().filter(recordComponents::contains).toList(),
      List.of(),
      selectionCollectionPresent
    );
  }

  private CapabilityRepetitionObservation measureCapabilities(int repetitions) {
    beginSetup();
    AdminCategorySystemRegistry registry = OBSERVED_REGISTRY.get();
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
    SqlStatementObservation observation = counting().measurementObservation();
    counting().beginTeardown();
    return observation;
  }

  private SqlStatementObservation lastSqlObservation() {
    return counting().measurementObservation();
  }

  private StatementObservation statementObservation(
    SqlStatementObservation sql,
    int returnedCardinality,
    List<String> interactions
  ) {
    List<String> exactFingerprints = sql.normalizedFingerprints();
    assertThat(exactFingerprints).hasSize(sql.statementCount());
    List<String> fingerprints = sql.cardinalityNormalizedFingerprints();
    assertThat(fingerprints).hasSize(sql.statementCount());
    return new StatementObservation(
      sql.statementCount(),
      returnedCardinality,
      sql.statementKinds().stream().map(Enum::name).toList(),
      fingerprints,
      interactions
    );
  }

  private void assertMeasuredSelectShapes(StatementObservation observation) {
    assertThat(observation.statementKinds()).containsOnly("SELECT");
    assertThat(lastSqlObservation().statements()).allSatisfy(statement -> {
      assertThat(statement.batchSize()).isEqualTo(1);
      assertThat(statement.sqlShapeCount()).isEqualTo(1);
    });
  }

  private void seedRoots(int count) {
    beginSetup();
    recreateSchema();
    List<NodeSeed> nodes = new ArrayList<>(count);
    List<ClosureSeed> closures = new ArrayList<>(count);
    for (int index = 1; index <= count; index++) {
      nodes.add(node(index, null, label(index), slug(index)));
      closures.add(new ClosureSeed(index, index, 0));
    }
    insertNodes(nodes);
    insertClosures(closures);
  }

  private void seedChain(int count, boolean maximumDepth) {
    beginSetup();
    recreateSchema();
    List<NodeSeed> nodes = new ArrayList<>(count);
    List<ClosureSeed> closures = new ArrayList<>((count * (count + 1)) / 2);
    for (int descendant = 1; descendant <= count; descendant++) {
      Long parentId = descendant == 1 ? null : (long) descendant - 1L;
      nodes.add(node(descendant, parentId, label(descendant), slug(descendant)));
      for (int ancestor = 1; ancestor <= descendant; ancestor++) {
        int depth = maximumDepth ? Integer.MAX_VALUE : descendant - ancestor;
        closures.add(new ClosureSeed(ancestor, descendant, depth));
      }
    }
    insertNodes(nodes);
    insertClosures(closures);
  }

  private void beginSetup() {
    counting().beginSetup();
  }

  private void recreateSchema() {
    DescriptorBackedAdminCategoryJdbcFixture.dropSchema(jdbc);
    DescriptorBackedAdminCategoryJdbcFixture.createSchema(jdbc);
  }

  private void insertNodes(List<NodeSeed> nodes) {
    jdbc.batchUpdate(
      """
      INSERT INTO test_category_a
        (id, parent_id, sort_rank, name, slug, active, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, TRUE, ?, ?)
      """,
      new BatchPreparedStatementSetter() {
        @Override
        public void setValues(PreparedStatement statement, int index) throws SQLException {
          NodeSeed node = nodes.get(index);
          statement.setLong(1, node.id());
          if (node.parentId() == null) {
            statement.setNull(2, java.sql.Types.BIGINT);
          } else {
            statement.setLong(2, node.parentId());
          }
          statement.setLong(3, node.id() * 1_000_000_000L);
          statement.setString(4, node.name());
          statement.setString(5, node.slug());
          statement.setObject(6, OffsetDateTime.parse("2026-01-01T00:00:00Z"));
          statement.setObject(7, OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        }

        @Override
        public int getBatchSize() {
          return nodes.size();
        }
      }
    );
  }

  private void insertClosures(List<ClosureSeed> closures) {
    jdbc.batchUpdate(
      """
      INSERT INTO test_category_a_closure (ancestor_id, descendant_id, depth)
      VALUES (?, ?, ?)
      """,
      new BatchPreparedStatementSetter() {
        @Override
        public void setValues(PreparedStatement statement, int index) throws SQLException {
          ClosureSeed closure = closures.get(index);
          statement.setLong(1, closure.ancestorId());
          statement.setLong(2, closure.descendantId());
          statement.setInt(3, closure.depth());
        }

        @Override
        public int getBatchSize() {
          return closures.size();
        }
      }
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
    return registration().definition().orElseThrow();
  }

  private AdminCategorySystemRegistration registration() {
    return factory
      .registrations()
      .stream()
      .filter(candidate -> candidate.systemKey().filter(SYSTEM_KEY::equals).isPresent())
      .findFirst()
      .orElseThrow();
  }

  private CountingDataSource counting() {
    return COUNTING_DATA_SOURCE.get();
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
    OBSERVED_REGISTRY.set(observedRegistry);
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
    boolean beforeSql,
    BoundaryMeasurement measurement
  ) {
    return new MaximumBoundCase(
      concern,
      maximumStatements,
      new OneOverExpectation(
        true,
        oneOverOutcome,
        beforeSql,
        beforeSql ? 0 : maximumStatements,
        "the real descriptor boundary rejects the selected-definition one-over value stably"
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

  private static NodeSeed node(long id, Long parentId, String name, String slug) {
    return new NodeSeed(id, parentId, name, slug);
  }

  private static String label(int index) {
    return "Category %05d".formatted(index);
  }

  private static String slug(int index) {
    return "category-%05d".formatted(index);
  }

  private record NodeSeed(long id, Long parentId, String name, String slug) {}

  private record ClosureSeed(long ancestorId, long descendantId, int depth) {}

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

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer<?> postgresContainer() {
      return new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"));
    }
  }
}
