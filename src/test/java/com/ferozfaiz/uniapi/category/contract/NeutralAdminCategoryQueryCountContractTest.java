package com.ferozfaiz.uniapi.category.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.admincategorycontractfixture.NeutralFlatAdminCategoryContractFixture;
import com.ferozfaiz.uniapi.admincategorycontractfixture.NeutralRestrictedAdminCategoryContractFixture;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.policy.AdminCategoryDefinitionValidator;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryParentOptionsValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryTreeValidationInput;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.LogicalField;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractFixture.OperationObservation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile.SeedRecord;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.Applicability;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.BoundConcern;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.BoundaryObservation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.CapabilityRepetitionCase;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.CapabilityRepetitionObservation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.FilterSelectionBoundaryCase;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.FilterSelectionStructureObservation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.HierarchyAbsenceCase;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.InteractionObservation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.MaximumBoundCase;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.MaximumBoundaryExpectation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.OneOverExpectation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.QueryCountProfile;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.ReadPath;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.StatementObservation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.TwoCardinalityCase;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.UnsupportedPathCase;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.UnsupportedPathObservation;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryQueryCountContract.ZeroStatementCase;
import com.ferozfaiz.uniapi.category.support.sql.SqlStatementObservation;
import com.ferozfaiz.uniapi.category.support.sql.SqlStatementObservation.StatementKind;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
final class NeutralAdminCategoryQueryCountContractTest implements AdminCategoryQueryCountContract {

  private static final String RESTRICTED_TABLE = "admin_category_contract_restricted_node";
  private static final List<String> HIERARCHY_INTERACTIONS = List.of(
    "hierarchy-read.tree",
    "hierarchy-read.parentOptions",
    "hierarchy-read.breadcrumb",
    "hierarchy-policy.validate",
    "ordering-policy.validate",
    "closure.read",
    "ranking.read",
    "parent-options.read"
  );

  private final NeutralQueryHarness flat = NeutralQueryHarness.flat();
  private final NeutralQueryHarness restricted = NeutralQueryHarness.restricted();

  @Override
  public List<QueryCountProfile> queryCountProfiles() {
    return List.of(neutralProfile(flat), neutralProfile(restricted));
  }

  @AfterAll
  void closeFixtures() {
    flat.close();
    restricted.close();
  }

  @Test
  void neutralDefinitionsDeclareNoHierarchyLimitOrHierarchyOperation() {
    for (QueryCountProfile profile : queryCountProfiles()) {
      assertThat(profile.definition().hierarchy().enabled()).isFalse();
      assertThat(profile.definition().hierarchy().supportedOperations()).isEmpty();
      assertThat(profile.definition().operationalLimits().treeSize()).isEmpty();
      assertThat(profile.definition().operationalLimits().parentOptionPageSize()).isEmpty();
      assertThat(profile.definition().operationalLimits().includeIdCount()).isEmpty();
      assertThat(profile.definition().operationalLimits().hierarchyDepth()).isEmpty();
    }
  }

  @Test
  void restrictedListLedgerComesFromTheRealPostgresTableWithoutAnOrderingPort() {
    StatementObservation observation = restricted.measureList(1);
    SqlStatementObservation sql = restricted.lastSqlObservation();

    assertThat(observation.returnedCardinality()).isOne();
    assertThat(observation.interactions()).containsExactly("category-read.list");
    assertThat(restricted.fixture().ports().ordering()).isEmpty();
    assertThat(sql.statementCount()).isEqualTo(2);
    assertThat(sql.statementKinds()).containsExactly(StatementKind.SELECT, StatementKind.SELECT);
    assertThat(sql.normalizedFingerprints()).containsExactly(
      "1ac1f15467d0b05d083c66f5e86b8b6a9680edeedcc874e68c65fe5bf54739ea",
      "5743823b8efb8f99296a170800d1411d60ecaa910b4a52380ab9be74dcec233e"
    );
    assertThat(sql.containsSqlFragment("select count(*) from " + RESTRICTED_TABLE)).isTrue();
    assertThat(
      sql.containsSqlFragment(
        "select id, name, slug, active, created_at, updated_at from " + RESTRICTED_TABLE
      )
    ).isTrue();
    assertThat(sql.statements()).allSatisfy(statement -> {
      assertThat(statement.sqlShapeCount()).isOne();
      assertThat(statement.batchSize()).isOne();
    });
  }

  @Test
  void neutralFilterSurfaceProvesParentIsTheUnavailableFourthStructuralSelection() {
    assertThat(
      Arrays.stream(AdminCategoryListValidationInput.class.getRecordComponents()).map(
        java.lang.reflect.RecordComponent::getName
      )
    ).containsExactly(
      "definition",
      "page",
      "size",
      "parentId",
      "active",
      "q",
      "slug",
      "sort",
      "direction"
    );

    AdminCategoryDefinitionValidator validator = new AdminCategoryDefinitionValidator();
    for (NeutralQueryHarness harness : List.of(flat, restricted)) {
      AdminCategorySystemDefinition definition = harness.definition();
      assertThat(definition.operationalLimits().filterSelections()).contains(4L);
      assertThat(definition.fieldsAndQueries().allowedFilters()).containsExactly(
        LogicalField.SLUG,
        LogicalField.ACTIVE
      );
      assertThat(definition.fieldsAndQueries().allowedFilters())
        .doesNotContain(LogicalField.PARENT)
        .hasSizeLessThan(
          Math.toIntExact(definition.operationalLimits().filterSelections().orElseThrow())
        );
      assertThat(definition.fieldsAndQueries().searchableFields()).containsExactly(
        LogicalField.NAME,
        LogicalField.SLUG
      );

      SeedRecord seed = harness.profile().seedRecords().getFirst();
      String slug = seed.attributes().get("slug");
      var largestConstructibleNeutralQuery = validator.validateList(
        listInput(definition, null, true, "a", slug, List.of())
      );
      assertThat(largestConstructibleNeutralQuery.filters().active()).isTrue();
      assertThat(largestConstructibleNeutralQuery.filters().q()).isEqualTo("a");
      assertThat(largestConstructibleNeutralQuery.filters().slug()).isEqualTo(slug);

      InvalidRequestException parentRejected = org.assertj.core.api.Assertions.catchThrowableOfType(
        () ->
          validator.validateList(
            new AdminCategoryListValidationInput(
              definition,
              0,
              20,
              "0",
              true,
              "a",
              slug,
              List.of(),
              null
            )
          ),
        InvalidRequestException.class
      );
      assertThat(parentRejected.messageKey()).isEqualTo("category.api.list.parent-id.format");
    }
  }

  @Test
  void fiveSortSelectionsRetainIdentifierAsTheFinalTiebreak() {
    AdminCategoryDefinitionValidator validator = new AdminCategoryDefinitionValidator();

    for (NeutralQueryHarness harness : List.of(flat, restricted)) {
      var query = validator.validateList(
        listInput(
          harness.definition(),
          null,
          null,
          null,
          null,
          NeutralQueryHarness.sortSelections(5)
        )
      );

      assertThat(query.sort().orders())
        .extracting(com.ferozfaiz.uniapi.shared.pagination.SortOrder::property)
        .containsExactly("name", "slug", "active", "createdAt", "updatedAt", "id");
    }
  }

  @Test
  void constructibleMaximumRequiresExecutableOneOverEvidence() {
    assertThatThrownBy(() ->
      new MaximumBoundCase(
        BoundConcern.LIST_PAGE,
        0,
        new MaximumBoundaryExpectation(true, "success", false, 0, "constructible maximum"),
        new OneOverExpectation(false, "missing", true, 0, "one-over evidence was omitted"),
        requested ->
          new BoundaryObservation(
            requested,
            true,
            "success",
            new StatementObservation(0, 0, List.of(), List.of(), List.of())
          )
      )
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("constructible")
      .hasMessageContaining("one-over");
  }

  @Test
  void reusableProfileStructurallyRequiresFilterAndCapabilityBoundaryEvidence() {
    assertThat(
      Arrays.stream(QueryCountProfile.class.getRecordComponents()).map(
        java.lang.reflect.RecordComponent::getName
      )
    ).contains("filterSelectionBoundaryCase", "capabilityRepetitionCase");
  }

  @Test
  void caughtAdminCategoryFailureAdapterPropagatesActualStatusAndCode() {
    AdminCategoryException failure = new AdminCategoryException(
      "STATUS_REGRESSION",
      org.springframework.http.HttpStatus.GONE,
      "category.test.status-regression",
      "status regression"
    );

    CaughtAdminCategoryFailure observation = caughtFailure(failure);

    assertThat(observation.status()).isEqualTo(410);
    assertThat(observation.code()).isEqualTo("STATUS_REGRESSION");
  }

  @Test
  void applicabilityAndStatementLedgersRejectUnexplainedOrInexactEvidence() {
    assertThatThrownBy(() -> new Applicability(false, "N/A"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("executable evidence");
    assertThatThrownBy(() ->
      new StatementObservation(1, 1, List.of(), List.of(), List.of("category-read.list"))
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("exact statement ledger");
  }

  private static QueryCountProfile neutralProfile(NeutralQueryHarness harness) {
    Map<ReadPath, Applicability> readPaths = new EnumMap<>(ReadPath.class);
    readPaths.put(ReadPath.LIST, applicable("the selected neutral definition declares LIST"));
    readPaths.put(
      ReadPath.COMPLETE_TREE,
      inapplicable("the selected definition disables hierarchy and does not declare TREE")
    );
    readPaths.put(
      ReadPath.BREADCRUMB,
      inapplicable("the selected definition disables hierarchy and does not declare BREADCRUMB")
    );
    readPaths.put(
      ReadPath.PARENT_OPTIONS,
      inapplicable("the selected definition disables hierarchy and parent options")
    );
    readPaths.put(
      ReadPath.SELECTED_HYDRATION,
      inapplicable("the selected definition does not declare PARENT_OPTIONS or include IDs")
    );

    Map<BoundConcern, Applicability> bounds = new EnumMap<>(BoundConcern.class);
    bounds.put(BoundConcern.LIST_PAGE, applicable("the selected definition declares page size"));
    bounds.put(
      BoundConcern.SEARCH_LENGTH,
      applicable("the selected definition declares search length")
    );
    bounds.put(
      BoundConcern.FILTER_SELECTIONS,
      applicable(
        "the selected definition declares four, but the frozen request exposes only two allowed filters and no filter collection"
      )
    );
    bounds.put(
      BoundConcern.SORT_SELECTIONS,
      applicable("the selected definition declares sort selections")
    );
    bounds.put(
      BoundConcern.COMPLETE_TREE,
      inapplicable("the selected definition disables hierarchy and has no tree-size bound")
    );
    bounds.put(
      BoundConcern.PARENT_OPTIONS,
      inapplicable("the selected definition has no parent-option bound")
    );
    bounds.put(
      BoundConcern.INCLUDE_IDS,
      inapplicable("the selected definition has no include-ID bound")
    );
    bounds.put(
      BoundConcern.HIERARCHY_DEPTH,
      inapplicable("the selected definition has no hierarchy-depth bound")
    );

    List<UnsupportedPathCase> unsupportedPaths = Arrays.stream(ReadPath.values())
      .filter(path -> !readPaths.get(path).applicable())
      .map(path -> new UnsupportedPathCase(path, path.operation(), () -> harness.unsupported(path)))
      .toList();

    List<MaximumBoundCase> maximumBounds = List.of(
      maximum(BoundConcern.LIST_PAGE, harness),
      maximum(BoundConcern.SEARCH_LENGTH, harness),
      maximum(BoundConcern.SORT_SELECTIONS, harness)
    );
    List<ZeroStatementCase> zeroStatementCases = harness.isRestricted()
      ? List.of(
          new ZeroStatementCase("restricted-capabilities", harness::capabilities),
          new ZeroStatementCase("restricted-unsupported-get", harness::unsupportedGet)
        )
      : List.of(
          new ZeroStatementCase("flat-list-cardinality-1", () -> harness.measureList(1)),
          new ZeroStatementCase("flat-list-cardinality-25", () -> harness.measureList(25)),
          new ZeroStatementCase("flat-get-existing", () -> harness.flatGet(true)),
          new ZeroStatementCase("flat-get-missing", () -> harness.flatGet(false))
        );

    return new QueryCountProfile(
      harness.profile().profileId(),
      harness.definition(),
      readPaths,
      bounds,
      List.of(new TwoCardinalityCase(ReadPath.LIST, harness::measureList)),
      unsupportedPaths,
      maximumBounds,
      new FilterSelectionBoundaryCase(
        harness.maximumListStatements(),
        3,
        requested -> harness.measureBoundary(BoundConcern.FILTER_SELECTIONS, requested),
        harness::filterSelectionStructure
      ),
      new CapabilityRepetitionCase(harness::measureCapabilities),
      zeroStatementCases,
      new HierarchyAbsenceCase(
        "neutral-hierarchy-collaborators-remain-absent",
        applicable("the selected definition disables hierarchy"),
        HIERARCHY_INTERACTIONS,
        harness::observeHierarchyAbsence
      )
    );
  }

  private static MaximumBoundCase maximum(BoundConcern concern, NeutralQueryHarness harness) {
    return new MaximumBoundCase(
      concern,
      harness.maximumListStatements(),
      new OneOverExpectation(
        true,
        stableOneOverOutcome(concern),
        true,
        0,
        "the real selected query policy rejects one-over-bound input before provider work"
      ),
      requested -> harness.measureBoundary(concern, requested)
    );
  }

  private static String stableOneOverOutcome(BoundConcern concern) {
    return switch (concern) {
      case LIST_PAGE -> "category.api.list.size.max";
      case SEARCH_LENGTH -> "category.api.list.search.size";
      case SORT_SELECTIONS -> "category.api.list.sort.max";
      default -> throw new IllegalArgumentException(
        "no constructible neutral one-over for " + concern
      );
    };
  }

  private static Applicability applicable(String evidence) {
    return new Applicability(true, evidence);
  }

  private static Applicability inapplicable(String evidence) {
    return new Applicability(false, evidence);
  }

  private static AdminCategoryListValidationInput listInput(
    AdminCategorySystemDefinition definition,
    Integer size,
    Boolean active,
    String q,
    String slug,
    List<String> sort
  ) {
    return new AdminCategoryListValidationInput(
      definition,
      0,
      size,
      null,
      active,
      q,
      slug,
      sort,
      null
    );
  }

  private static CaughtAdminCategoryFailure caughtFailure(AdminCategoryException failure) {
    return new CaughtAdminCategoryFailure(failure.status().value(), failure.code());
  }

  private record CaughtAdminCategoryFailure(int status, String code) {}

  private static final class NeutralQueryHarness implements AutoCloseable {

    private final AdminCategoryProviderContractFixture fixture;
    private final AdminCategoryProviderContractProfile profile;
    private final NeutralRestrictedAdminCategoryContractFixture restrictedFixture;
    private SqlStatementObservation lastSqlObservation;

    private NeutralQueryHarness(
      AdminCategoryProviderContractFixture fixture,
      AdminCategoryProviderContractProfile profile,
      NeutralRestrictedAdminCategoryContractFixture restrictedFixture
    ) {
      this.fixture = fixture;
      this.profile = profile;
      this.restrictedFixture = restrictedFixture;
    }

    private static NeutralQueryHarness flat() {
      return new NeutralQueryHarness(
        new NeutralFlatAdminCategoryContractFixture(),
        NeutralFlatAdminCategoryContractFixture.contractProfile(),
        null
      );
    }

    private static NeutralQueryHarness restricted() {
      NeutralRestrictedAdminCategoryContractFixture fixture =
        new NeutralRestrictedAdminCategoryContractFixture();
      return new NeutralQueryHarness(
        fixture,
        NeutralRestrictedAdminCategoryContractFixture.profile(),
        fixture
      );
    }

    private AdminCategoryProviderContractFixture fixture() {
      return fixture;
    }

    private AdminCategoryProviderContractProfile profile() {
      return profile;
    }

    private AdminCategorySystemDefinition definition() {
      return profile.definition();
    }

    private boolean isRestricted() {
      return restrictedFixture != null;
    }

    private int maximumListStatements() {
      return isRestricted() ? 2 : 0;
    }

    private StatementObservation measureList(int cardinality) {
      resetAndSeed(cardinality);
      beginMeasurement();
      com.ferozfaiz.uniapi.shared.pagination.PageResult<
        com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels.CategoryNode
      > result;
      try {
        result = fixture
          .applicationService()
          .list(
            definition().systemKey(),
            listInput(definition(), 1_000, null, null, null, List.of())
          );
      } finally {
        finishMeasurement();
      }
      return statementObservation(result.content().size(), fixture.interactions().calls());
    }

    private BoundaryObservation measureBoundary(BoundConcern concern, long requested) {
      resetAndSeed(1);
      beginMeasurement();
      RuntimeException failure = null;
      int resultSize = 0;
      try {
        var page = fixture
          .applicationService()
          .list(definition().systemKey(), boundaryInput(concern, requested));
        resultSize = page.content().size();
      } catch (RuntimeException caught) {
        failure = caught;
      } finally {
        finishMeasurement();
      }
      StatementObservation statements = statementObservation(
        resultSize,
        fixture.interactions().calls()
      );
      return new BoundaryObservation(
        requested,
        failure == null,
        failure == null ? "success" : stableOutcome(failure),
        statements
      );
    }

    private AdminCategoryListValidationInput boundaryInput(BoundConcern concern, long requested) {
      return switch (concern) {
        case LIST_PAGE -> listInput(
          definition(),
          Math.toIntExact(requested),
          null,
          null,
          null,
          List.of()
        );
        case SEARCH_LENGTH -> listInput(
          definition(),
          1_000,
          null,
          "a".repeat(Math.toIntExact(requested)),
          null,
          List.of()
        );
        case FILTER_SELECTIONS -> {
          if (requested != 3) {
            throw new IllegalArgumentException(
              "neutral filter execution is proven at the largest valid selection count 3"
            );
          }
          SeedRecord seed = profile.seedRecords().getFirst();
          yield listInput(
            definition(),
            1_000,
            true,
            seed.attributes().get("name").substring(0, 1).toLowerCase(java.util.Locale.ROOT),
            seed.attributes().get("slug"),
            List.of()
          );
        }
        case SORT_SELECTIONS -> listInput(
          definition(),
          1_000,
          null,
          null,
          null,
          sortSelections(Math.toIntExact(requested))
        );
        default -> throw new IllegalArgumentException("neutral profile has no " + concern);
      };
    }

    private UnsupportedPathObservation unsupported(ReadPath path) {
      resetAndSeed(1);
      Operation operation = path.operation();
      AdminCategoryException failure;
      beginMeasurement();
      try {
        invokeUnsupported(path);
        throw new AssertionError("undeclared read path unexpectedly succeeded: " + path);
      } catch (AdminCategoryException caught) {
        failure = caught;
      } finally {
        finishMeasurement();
      }
      CaughtAdminCategoryFailure actualFailure = caughtFailure(failure);
      return new UnsupportedPathObservation(
        operation,
        actualFailure.status(),
        actualFailure.code(),
        statementObservation(0, fixture.interactions().calls()),
        fixture.interactions().calls()
      );
    }

    private void invokeUnsupported(ReadPath path) {
      switch (path) {
        case COMPLETE_TREE -> fixture
          .applicationService()
          .tree(
            definition().systemKey(),
            new AdminCategoryTreeValidationInput(definition(), 0, 20, List.of(), null)
          );
        case BREADCRUMB -> fixture
          .applicationService()
          .breadcrumb(
            definition().systemKey(),
            Long.valueOf(profile.seedRecords().getFirst().recordId())
          );
        case PARENT_OPTIONS -> invokeUnsupportedParentOptions(List.of());
        case SELECTED_HYDRATION -> invokeUnsupportedParentOptions(
          List.of(profile.seedRecords().getFirst().recordId())
        );
        case LIST -> throw new IllegalArgumentException("LIST is declared by neutral profiles");
      }
    }

    private void invokeUnsupportedParentOptions(List<String> includeIds) {
      fixture
        .applicationService()
        .parentOptions(
          definition().systemKey(),
          new AdminCategoryParentOptionsValidationInput(
            definition(),
            null,
            20,
            null,
            includeIds,
            null,
            null
          )
        );
    }

    private StatementObservation flatGet(boolean existing) {
      if (isRestricted()) {
        throw new IllegalStateException("restricted profile does not declare GET");
      }
      resetAndSeed(existing ? 1 : 0);
      Long id = Long.valueOf(profile.seedRecords().getFirst().recordId());
      int returned = 0;
      try {
        fixture.applicationService().getNode(definition().systemKey(), id);
        returned = 1;
      } catch (AdminCategoryException failure) {
        assertThat(existing).isFalse();
        assertThat(failure.code()).isEqualTo("CATEGORY_NOT_FOUND");
      }
      return statementObservation(returned, fixture.interactions().calls());
    }

    private StatementObservation capabilities() {
      fixture.reset();
      OperationObservation result = fixture.invoke(Operation.CAPABILITIES);
      if (isRestricted()) {
        lastSqlObservation = restrictedFixture.freezeQueryCountObservation();
      }
      assertThat(result.stableError()).isEmpty();
      assertThat(result.publicResult().facts().get("systemKey")).isEqualTo(
        definition().systemKey()
      );
      return statementObservation(1, fixture.interactions().calls());
    }

    private CapabilityRepetitionObservation measureCapabilities(int repetitions) {
      if (repetitions < 1) {
        throw new IllegalArgumentException("repetitions must be positive");
      }
      fixture.reset();
      org.mockito.Mockito.clearInvocations(fixture.registry());
      beginMeasurement();
      List<AdminCategorySystemDefinition> results = new ArrayList<>(repetitions);
      try {
        for (int index = 0; index < repetitions; index++) {
          results.add(fixture.applicationService().capabilities(definition().systemKey()));
        }
      } finally {
        finishMeasurement();
      }
      int registryLookups = Math.toIntExact(
        org.mockito.Mockito.mockingDetails(fixture.registry())
          .getInvocations()
          .stream()
          .filter(invocation -> invocation.getMethod().getName().equals("findRegistration"))
          .count()
      );
      List<String> interactions = fixture.interactions().calls();
      return new CapabilityRepetitionObservation(
        repetitions,
        results,
        registryLookups,
        statementObservation(repetitions, interactions),
        interactions
      );
    }

    private FilterSelectionStructureObservation filterSelectionStructure() {
      List<String> selectionSlots = List.of("parentId", "active", "q", "slug");
      List<String> recordComponents = Arrays.stream(
        AdminCategoryListValidationInput.class.getRecordComponents()
      )
        .map(java.lang.reflect.RecordComponent::getName)
        .toList();
      assertThat(recordComponents).containsAll(selectionSlots);

      boolean selectionCollectionPresent = Arrays.stream(
        AdminCategoryListValidationInput.class.getRecordComponents()
      ).anyMatch(
        component ->
          (component.getName().equals("filters") ||
            component.getName().equals("filterSelections")) &&
          java.util.Collection.class.isAssignableFrom(component.getType())
      );

      List<String> availableSelectionSlots = new ArrayList<>();
      if (
        definition().hierarchy().enabled() &&
        definition().fieldsAndQueries().allowedFilters().contains(LogicalField.PARENT)
      ) {
        availableSelectionSlots.add("parentId");
      }
      if (definition().fieldsAndQueries().allowedFilters().contains(LogicalField.ACTIVE)) {
        availableSelectionSlots.add("active");
      }
      if (!definition().fieldsAndQueries().searchableFields().isEmpty()) {
        availableSelectionSlots.add("q");
      }
      if (definition().fieldsAndQueries().allowedFilters().contains(LogicalField.SLUG)) {
        availableSelectionSlots.add("slug");
      }
      List<String> unavailableSelectionSlots = selectionSlots
        .stream()
        .filter(slot -> !availableSelectionSlots.contains(slot))
        .toList();
      return new FilterSelectionStructureObservation(
        selectionSlots,
        availableSelectionSlots,
        unavailableSelectionSlots,
        selectionCollectionPresent
      );
    }

    private StatementObservation unsupportedGet() {
      resetAndSeed(1);
      AdminCategoryException failure;
      beginMeasurement();
      try {
        fixture
          .applicationService()
          .getNode(
            definition().systemKey(),
            Long.valueOf(profile.seedRecords().getFirst().recordId())
          );
        throw new AssertionError("restricted GET unexpectedly succeeded");
      } catch (AdminCategoryException caught) {
        failure = caught;
      } finally {
        finishMeasurement();
      }
      CaughtAdminCategoryFailure actualFailure = caughtFailure(failure);
      assertThat(actualFailure.status()).isEqualTo(409);
      assertThat(actualFailure.code()).isEqualTo("CATEGORY_CAPABILITY_UNSUPPORTED");
      return statementObservation(0, fixture.interactions().calls());
    }

    private InteractionObservation observeHierarchyAbsence() {
      StatementObservation list = measureList(1);
      List<String> interactions = new ArrayList<>(list.interactions());
      int statements = list.statementCount();
      if (!isRestricted()) {
        StatementObservation get = flatGet(true);
        interactions.addAll(get.interactions());
        statements += get.statementCount();
      }
      return new InteractionObservation(statements, interactions);
    }

    private void resetAndSeed(int cardinality) {
      fixture.reset();
      fixture.seedCardinality(cardinality, profile.seedRecords());
    }

    private void beginMeasurement() {
      if (isRestricted()) {
        restrictedFixture.beginQueryCountMeasurement();
      }
    }

    private void finishMeasurement() {
      if (isRestricted()) {
        lastSqlObservation = restrictedFixture.freezeQueryCountObservation();
      }
    }

    private StatementObservation statementObservation(int returned, List<String> interactions) {
      if (!isRestricted()) {
        return new StatementObservation(0, returned, List.of(), List.of(), interactions);
      }
      SqlStatementObservation sql = lastSqlObservation();
      return new StatementObservation(
        sql.statementCount(),
        returned,
        sql.statementKinds().stream().map(Enum::name).toList(),
        sql.normalizedFingerprints(),
        interactions
      );
    }

    private SqlStatementObservation lastSqlObservation() {
      return Optional.ofNullable(lastSqlObservation).orElseThrow(() ->
        new IllegalStateException("restricted SQL observation was not frozen")
      );
    }

    private static List<String> sortSelections(int requested) {
      List<String> all = List.of(
        "name,asc",
        "slug,asc",
        "active,asc",
        "createdAt,asc",
        "updatedAt,asc",
        "id,asc"
      );
      if (requested < 0 || requested > all.size()) {
        throw new IllegalArgumentException("unsupported neutral sort cardinality " + requested);
      }
      return all.subList(0, requested);
    }

    private static String stableOutcome(RuntimeException failure) {
      if (failure instanceof InvalidRequestException invalid) {
        return invalid.messageKey();
      }
      if (failure instanceof AdminCategoryException category) {
        return category.code();
      }
      return failure.getClass().getName();
    }

    @Override
    public void close() {
      fixture.close();
    }
  }
}
