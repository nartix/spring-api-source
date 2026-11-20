package com.ferozfaiz.uniapi.category.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Reusable deterministic statement-count and selected-definition bound contract.
 *
 * <p>Implementations supply fresh observations for one selected profile. Setup, teardown, and
 * post-observation fingerprint queries belong outside the supplied measurement functions.
 */
public interface AdminCategoryQueryCountContract {
  int COMPARISON_CAP = 25;

  List<QueryCountProfile> queryCountProfiles();

  @Test
  default void manifestsClassifyEveryPathAndBoundExactlyOnce() {
    List<QueryCountProfile> profiles = profiles();
    Set<String> profileIds = new HashSet<>();
    for (QueryCountProfile profile : profiles) {
      assertTrue(profileIds.add(profile.profileId()), "duplicate profile " + profile.profileId());
      assertEquals(Set.of(ReadPath.values()), profile.readPathApplicability().keySet());
      assertEquals(Set.of(BoundConcern.values()), profile.boundApplicability().keySet());
      assertEquals(
        applicableReadPaths(profile),
        profile
          .twoCardinalityCases()
          .stream()
          .map(TwoCardinalityCase::path)
          .collect(java.util.stream.Collectors.toUnmodifiableSet()),
        profile.profileId() + " two-cardinality ledger"
      );
      assertEquals(
        inapplicableReadPaths(profile),
        profile
          .unsupportedPathCases()
          .stream()
          .map(UnsupportedPathCase::path)
          .collect(java.util.stream.Collectors.toUnmodifiableSet()),
        profile.profileId() + " unsupported-path ledger"
      );
      assertEquals(
        applicableMaximumBounds(profile),
        profile
          .maximumBoundCases()
          .stream()
          .map(MaximumBoundCase::concern)
          .collect(java.util.stream.Collectors.toUnmodifiableSet()),
        profile.profileId() + " maximum-bound ledger"
      );
      profile
        .readPathApplicability()
        .values()
        .forEach(applicability -> assertFalse(applicability.evidence().isBlank()));
      profile
        .boundApplicability()
        .values()
        .forEach(applicability -> assertFalse(applicability.evidence().isBlank()));
    }
  }

  @TestFactory
  default Stream<DynamicTest> supportedPathsKeepExactStatementLedgersAtBothCardinalities() {
    return profiles()
      .stream()
      .flatMap(profile ->
        profile
          .twoCardinalityCases()
          .stream()
          .map(scenario ->
            DynamicTest.dynamicTest(profile.profileId() + "/" + scenario.path().id(), () -> {
              int small = 1;
              int larger = scenario.path().comparisonCardinality(profile.definition());

              StatementObservation smallObservation = scenario.measurement().measure(small);
              StatementObservation largerObservation = scenario.measurement().measure(larger);

              assertEquals(
                small,
                smallObservation.returnedCardinality(),
                "small result cardinality"
              );
              assertEquals(
                larger,
                largerObservation.returnedCardinality(),
                "larger result cardinality"
              );
              assertEquals(
                smallObservation.statementCount(),
                largerObservation.statementCount(),
                "statement count grew with result cardinality"
              );
              assertEquals(
                smallObservation.statementKinds(),
                largerObservation.statementKinds(),
                "statement-kind ledger changed with result cardinality"
              );
              assertEquals(
                smallObservation.normalizedStatementFingerprints(),
                largerObservation.normalizedStatementFingerprints(),
                "statement-shape ledger changed with result cardinality"
              );
              assertEquals(
                smallObservation.interactions(),
                largerObservation.interactions(),
                "interaction ledger changed with result cardinality"
              );
            })
          )
      );
  }

  @TestFactory
  default Stream<DynamicTest> selectedDefinitionMaximumBoundsStayBounded() {
    return profiles()
      .stream()
      .flatMap(profile ->
        profile
          .maximumBoundCases()
          .stream()
          .map(scenario ->
            DynamicTest.dynamicTest(profile.profileId() + "/" + scenario.concern().id(), () -> {
              long maximum = scenario.concern().selectedLimit(profile.definition());
              BoundaryObservation atMaximum = scenario.exercise().observe(maximum);
              assertBoundaryRequest(maximum, atMaximum);
              MaximumBoundaryExpectation maximumExpectation = scenario.maximumBoundaryExpectation();
              assertEquals(
                maximumExpectation.constructible(),
                atMaximum.succeeded(),
                maximumExpectation.evidence()
              );
              assertEquals(maximumExpectation.stableOutcome(), atMaximum.stableOutcome());
              assertTrue(
                atMaximum.statements().statementCount() <= maximumExpectation.maximumStatements(),
                "maximum-bound statement count exceeded its declared ceiling"
              );
              if (maximumExpectation.failureBeforeSql()) {
                assertEquals(0, atMaximum.statements().statementCount());
                assertTrue(
                  atMaximum.statements().interactions().isEmpty(),
                  "non-constructible maximum reached a runtime port"
                );
              }

              if (
                maximumExpectation.constructible() &&
                scenario.concern() != BoundConcern.COMPLETE_TREE &&
                scenario.oneOverExpectation().applicable()
              ) {
                assertStableOneOverFailure(profile, scenario, maximum);
              }
            })
          )
      );
  }

  @TestFactory
  default Stream<DynamicTest> filterSelectionMaximumUsesTheFrozenFourSlotRequestShape() {
    return profiles()
      .stream()
      .map(profile ->
        DynamicTest.dynamicTest(profile.profileId() + "/filter-selections-structural", () -> {
          FilterSelectionBoundaryCase scenario = profile.filterSelectionBoundaryCase();
          long selectedMaximum = BoundConcern.FILTER_SELECTIONS.selectedLimit(profile.definition());
          FilterSelectionStructureObservation structure = scenario
            .structuralOneOverEvidence()
            .get();

          assertEquals(
            List.of("parentId", "active", "q", "slug"),
            structure.selectionSlots(),
            "Feature 014 filter-selection slots drifted"
          );
          assertEquals(selectedMaximum, structure.selectionSlots().size());
          assertFalse(
            structure.selectionCollectionPresent(),
            "a fifth filter selection became representable"
          );
          assertEquals(
            Math.addExact(selectedMaximum, 1L),
            structure.selectionSlots().size() + 1L,
            "structural one-over evidence does not describe the fifth selection"
          );

          Set<String> available = new LinkedHashSet<>(structure.availableSelectionSlots());
          Set<String> unavailable = new LinkedHashSet<>(structure.unavailableSelectionSlots());
          assertTrue(Collections.disjoint(available, unavailable));
          Set<String> classified = new LinkedHashSet<>(available);
          classified.addAll(unavailable);
          assertEquals(new LinkedHashSet<>(structure.selectionSlots()), classified);
          assertEquals(scenario.largestValidSelections(), available.size());

          if (scenario.largestValidSelections() < selectedMaximum) {
            assertEquals(List.of("parentId"), structure.unavailableSelectionSlots());
            assertFalse(profile.definition().hierarchy().enabled());
          } else {
            assertTrue(structure.unavailableSelectionSlots().isEmpty());
          }

          BoundaryObservation atLargestValid = scenario
            .exerciseLargestValid()
            .observe(scenario.largestValidSelections());
          assertBoundaryRequest(scenario.largestValidSelections(), atLargestValid);
          assertTrue(atLargestValid.succeeded(), "largest valid filter selection set failed");
          assertEquals("success", atLargestValid.stableOutcome());
          assertTrue(
            atLargestValid.statements().statementCount() <=
              scenario.maximumStatementsAtLargestValid(),
            "largest valid filter selection set exceeded its statement ceiling"
          );
        })
      );
  }

  @TestFactory
  default Stream<DynamicTest> capabilitiesAndRegistryLookupStayConstantAtOneAndTwentyFiveCalls() {
    return profiles()
      .stream()
      .map(profile ->
        DynamicTest.dynamicTest(profile.profileId() + "/capabilities-1-vs-25", () -> {
          CapabilityRepetitionObservation once = profile
            .capabilityRepetitionCase()
            .measurement()
            .measure(1);
          CapabilityRepetitionObservation repeated = profile
            .capabilityRepetitionCase()
            .measurement()
            .measure(COMPARISON_CAP);

          assertCapabilityRepetition(profile, 1, once);
          assertCapabilityRepetition(profile, COMPARISON_CAP, repeated);
          assertEquals(once.results().getFirst(), repeated.results().getFirst());
        })
      );
  }

  @TestFactory
  default Stream<DynamicTest> completeTreeOneOverBoundFailureIsStableForEverySupportingProfile() {
    List<DynamicTest> tests = new ArrayList<>();
    for (QueryCountProfile profile : profiles()) {
      Applicability applicability = profile.boundApplicability().get(BoundConcern.COMPLETE_TREE);
      if (!applicability.applicable()) {
        tests.add(
          DynamicTest.dynamicTest(profile.profileId() + "/complete-tree-not-supported", () -> {
            assertFalse(
              profile.definition().operations().supportedOperations().contains(Operation.TREE)
            );
            assertFalse(applicability.evidence().isBlank());
          })
        );
        continue;
      }
      MaximumBoundCase scenario = profile
        .maximumBoundCases()
        .stream()
        .filter(candidate -> candidate.concern() == BoundConcern.COMPLETE_TREE)
        .findFirst()
        .orElseThrow();
      tests.add(
        DynamicTest.dynamicTest(profile.profileId() + "/complete-tree-one-over", () -> {
          assertTrue(scenario.oneOverExpectation().applicable(), applicability.evidence());
          assertStableOneOverFailure(
            profile,
            scenario,
            BoundConcern.COMPLETE_TREE.selectedLimit(profile.definition())
          );
        })
      );
    }
    return tests.stream();
  }

  @TestFactory
  default Stream<DynamicTest> declaredZeroStatementCasesHaveNoHiddenStatementLedger() {
    return profiles()
      .stream()
      .flatMap(profile ->
        profile
          .zeroStatementCases()
          .stream()
          .map(scenario ->
            DynamicTest.dynamicTest(profile.profileId() + "/" + scenario.caseId(), () -> {
              StatementObservation observation = scenario.observation().get();
              assertEquals(0, observation.statementCount());
              assertTrue(observation.statementKinds().isEmpty());
              assertTrue(observation.normalizedStatementFingerprints().isEmpty());
            })
          )
      );
  }

  @TestFactory
  default Stream<DynamicTest> zeroHierarchyProfilesNeverTouchHierarchyCollaborators() {
    return profiles()
      .stream()
      .map(profile ->
        DynamicTest.dynamicTest(
          profile.profileId() + "/" + profile.hierarchyAbsenceCase().caseId(),
          () -> {
            HierarchyAbsenceCase scenario = profile.hierarchyAbsenceCase();
            if (!scenario.applicability().applicable()) {
              assertTrue(
                profile.definition().hierarchy().enabled(),
                scenario.applicability().evidence()
              );
              return;
            }
            assertFalse(profile.definition().hierarchy().enabled());
            InteractionObservation observation = scenario.observation().get();
            assertTrue(
              Collections.disjoint(
                new HashSet<>(observation.interactions()),
                new HashSet<>(scenario.forbiddenInteractions())
              ),
              "disabled hierarchy touched a hierarchy, closure, ranking, or parent-option collaborator"
            );
          }
        )
      );
  }

  @TestFactory
  default Stream<DynamicTest> undeclaredReadPathsFailAtTheCapabilityGateBeforeSqlOrPorts() {
    return profiles()
      .stream()
      .flatMap(profile ->
        profile
          .unsupportedPathCases()
          .stream()
          .map(scenario ->
            DynamicTest.dynamicTest(
              profile.profileId() + "/" + scenario.path().id() + "-unsupported",
              () -> {
                Operation exactMissingOperation = scenario.path().operation();
                assertEquals(exactMissingOperation, scenario.missingOperation());
                assertFalse(
                  profile
                    .definition()
                    .operations()
                    .supportedOperations()
                    .contains(exactMissingOperation),
                  "unsupported-path evidence names a declared operation"
                );

                UnsupportedPathObservation observation = scenario.observation().get();

                assertEquals(exactMissingOperation, observation.missingOperation());
                assertEquals(409, observation.status());
                assertEquals("CATEGORY_CAPABILITY_UNSUPPORTED", observation.stableErrorCode());
                assertEquals(0, observation.statements().statementCount());
                assertTrue(observation.statements().statementKinds().isEmpty());
                assertTrue(observation.statements().normalizedStatementFingerprints().isEmpty());
                assertTrue(
                  observation.runtimePortInteractions().isEmpty(),
                  "capability rejection reached a runtime port"
                );
              }
            )
          )
      );
  }

  private static void assertStableOneOverFailure(
    QueryCountProfile profile,
    MaximumBoundCase scenario,
    long maximum
  ) throws Exception {
    OneOverExpectation expectation = scenario.oneOverExpectation();
    assertTrue(expectation.applicable(), expectation.evidence());
    long oneOver = Math.addExact(maximum, 1L);
    BoundaryObservation first = scenario.exercise().observe(oneOver);
    BoundaryObservation second = scenario.exercise().observe(oneOver);
    assertBoundaryRequest(oneOver, first);
    assertBoundaryRequest(oneOver, second);
    assertFalse(first.succeeded(), profile.profileId() + " one-over bound unexpectedly succeeded");
    assertFalse(
      second.succeeded(),
      profile.profileId() + " repeated one-over unexpectedly succeeded"
    );
    assertEquals(expectation.stableOutcome(), first.stableOutcome());
    assertEquals(
      first,
      second,
      "one-over-bound failure was not stable across repeated observations"
    );
    assertTrue(
      first.statements().statementCount() <= expectation.maximumStatements(),
      "one-over-bound failure exceeded its statement ceiling"
    );
    if (expectation.failureBeforeSql()) {
      assertEquals(0, first.statements().statementCount(), expectation.evidence());
    }
  }

  private static void assertBoundaryRequest(long requested, BoundaryObservation observation) {
    assertNotNull(observation, "boundary observation");
    assertEquals(requested, observation.requestedValue(), "boundary request drifted");
  }

  private static void assertCapabilityRepetition(
    QueryCountProfile profile,
    int repetitions,
    CapabilityRepetitionObservation observation
  ) {
    assertNotNull(observation, "capability repetition observation");
    assertEquals(repetitions, observation.repetitions());
    assertEquals(repetitions, observation.results().size());
    assertEquals(
      Set.of(profile.definition()),
      new HashSet<>(observation.results()),
      "capability projection changed across immutable registry lookups"
    );
    assertEquals(repetitions, observation.registryLookups());
    assertEquals(0, observation.statements().statementCount());
    assertEquals(repetitions, observation.statements().returnedCardinality());
    assertTrue(observation.statements().statementKinds().isEmpty());
    assertTrue(observation.statements().normalizedStatementFingerprints().isEmpty());
    assertTrue(
      observation.statements().interactions().isEmpty(),
      "capability projection touched a runtime collaborator"
    );
    assertTrue(
      observation.runtimePortInteractions().isEmpty(),
      "capability projection reached a runtime port"
    );
  }

  private static Set<ReadPath> applicableReadPaths(QueryCountProfile profile) {
    return profile
      .readPathApplicability()
      .entrySet()
      .stream()
      .filter(entry -> entry.getValue().applicable())
      .map(Map.Entry::getKey)
      .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static Set<BoundConcern> applicableMaximumBounds(QueryCountProfile profile) {
    return profile
      .boundApplicability()
      .entrySet()
      .stream()
      .filter(
        entry -> entry.getValue().applicable() && entry.getKey() != BoundConcern.FILTER_SELECTIONS
      )
      .map(Map.Entry::getKey)
      .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static Set<ReadPath> inapplicableReadPaths(QueryCountProfile profile) {
    return profile
      .readPathApplicability()
      .entrySet()
      .stream()
      .filter(entry -> !entry.getValue().applicable())
      .map(Map.Entry::getKey)
      .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private List<QueryCountProfile> profiles() {
    List<QueryCountProfile> profiles = List.copyOf(
      Objects.requireNonNull(queryCountProfiles(), "queryCountProfiles")
    );
    assertFalse(profiles.isEmpty(), "query-count contract requires at least one profile");
    profiles.forEach(profile -> assertNotNull(profile, "query-count profile"));
    return profiles;
  }

  enum ReadPath {
    LIST("list"),
    COMPLETE_TREE("complete-tree"),
    BREADCRUMB("breadcrumb"),
    PARENT_OPTIONS("parent-options"),
    SELECTED_HYDRATION("selected-hydration");

    private final String id;

    ReadPath(String id) {
      this.id = id;
    }

    public String id() {
      return id;
    }

    public Operation operation() {
      return switch (this) {
        case LIST -> Operation.LIST;
        case COMPLETE_TREE -> Operation.TREE;
        case BREADCRUMB -> Operation.BREADCRUMB;
        case PARENT_OPTIONS, SELECTED_HYDRATION -> Operation.PARENT_OPTIONS;
      };
    }

    public boolean selectedDefinitionSupports(AdminCategorySystemDefinition definition) {
      Set<Operation> operations = definition.operations().supportedOperations();
      return switch (this) {
        case LIST -> operations.contains(Operation.LIST) &&
          definition.operationalLimits().pageSize().isPresent();
        case COMPLETE_TREE -> operations.contains(Operation.TREE) &&
          definition.operationalLimits().treeSize().isPresent();
        case BREADCRUMB -> operations.contains(Operation.BREADCRUMB) &&
          definition.operationalLimits().hierarchyDepth().isPresent();
        case PARENT_OPTIONS -> operations.contains(Operation.PARENT_OPTIONS) &&
          definition.operationalLimits().parentOptionPageSize().isPresent();
        case SELECTED_HYDRATION -> operations.contains(Operation.PARENT_OPTIONS) &&
          definition.operationalLimits().includeIdCount().isPresent();
      };
    }

    public int comparisonCardinality(AdminCategorySystemDefinition definition) {
      long selectedLimit = switch (this) {
        case LIST -> BoundConcern.LIST_PAGE.selectedLimit(definition);
        case COMPLETE_TREE -> BoundConcern.COMPLETE_TREE.selectedLimit(definition);
        case BREADCRUMB -> BoundConcern.HIERARCHY_DEPTH.selectedLimit(definition);
        case PARENT_OPTIONS -> BoundConcern.PARENT_OPTIONS.selectedLimit(definition);
        case SELECTED_HYDRATION -> BoundConcern.INCLUDE_IDS.selectedLimit(definition);
      };
      long cardinality =
        this == SELECTED_HYDRATION ? selectedLimit : Math.min(selectedLimit, COMPARISON_CAP);
      if (cardinality < 1 || cardinality > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("comparison cardinality is not constructible");
      }
      return Math.toIntExact(cardinality);
    }
  }

  enum BoundConcern {
    LIST_PAGE("list-page"),
    SEARCH_LENGTH("search-length"),
    FILTER_SELECTIONS("filter-selections"),
    SORT_SELECTIONS("sort-selections"),
    COMPLETE_TREE("complete-tree"),
    PARENT_OPTIONS("parent-options"),
    INCLUDE_IDS("include-ids"),
    HIERARCHY_DEPTH("hierarchy-depth");

    private final String id;

    BoundConcern(String id) {
      this.id = id;
    }

    public String id() {
      return id;
    }

    public boolean selectedDefinitionSupports(AdminCategorySystemDefinition definition) {
      return switch (this) {
        case LIST_PAGE -> definition.operationalLimits().pageSize().isPresent();
        case SEARCH_LENGTH -> definition.operationalLimits().searchInputLength().isPresent();
        case FILTER_SELECTIONS -> definition.operationalLimits().filterSelections().isPresent();
        case SORT_SELECTIONS -> definition.operationalLimits().sortSelections().isPresent();
        case COMPLETE_TREE -> definition.operationalLimits().treeSize().isPresent();
        case PARENT_OPTIONS -> definition.operationalLimits().parentOptionPageSize().isPresent();
        case INCLUDE_IDS -> definition.operationalLimits().includeIdCount().isPresent();
        case HIERARCHY_DEPTH -> definition.operationalLimits().hierarchyDepth().isPresent();
      };
    }

    public long selectedLimit(AdminCategorySystemDefinition definition) {
      Objects.requireNonNull(definition, "definition");
      return (
        switch (this) {
          case LIST_PAGE -> definition.operationalLimits().pageSize();
          case SEARCH_LENGTH -> definition.operationalLimits().searchInputLength();
          case FILTER_SELECTIONS -> definition.operationalLimits().filterSelections();
          case SORT_SELECTIONS -> definition.operationalLimits().sortSelections();
          case COMPLETE_TREE -> definition.operationalLimits().treeSize();
          case PARENT_OPTIONS -> definition.operationalLimits().parentOptionPageSize();
          case INCLUDE_IDS -> definition.operationalLimits().includeIdCount();
          case HIERARCHY_DEPTH -> definition.operationalLimits().hierarchyDepth();
        }
      ).orElseThrow(() ->
        new IllegalStateException(id + " is absent from the selected definition")
      );
    }
  }

  record Applicability(boolean applicable, String evidence) {
    public Applicability {
      evidence = requireText(evidence, "applicability evidence");
      String normalized = evidence.replaceAll("[^A-Za-z]", "").toLowerCase(java.util.Locale.ROOT);
      if (normalized.equals("na") || normalized.equals("notapplicable")) {
        throw new IllegalArgumentException(
          "applicability must provide executable evidence instead of an unexplained N/A"
        );
      }
    }
  }

  record QueryCountProfile(
    String profileId,
    AdminCategorySystemDefinition definition,
    Map<ReadPath, Applicability> readPathApplicability,
    Map<BoundConcern, Applicability> boundApplicability,
    List<TwoCardinalityCase> twoCardinalityCases,
    List<UnsupportedPathCase> unsupportedPathCases,
    List<MaximumBoundCase> maximumBoundCases,
    FilterSelectionBoundaryCase filterSelectionBoundaryCase,
    CapabilityRepetitionCase capabilityRepetitionCase,
    List<ZeroStatementCase> zeroStatementCases,
    HierarchyAbsenceCase hierarchyAbsenceCase
  ) {
    public QueryCountProfile {
      profileId = requireText(profileId, "profileId");
      definition = Objects.requireNonNull(definition, "definition");
      readPathApplicability = immutableEnumMap(
        readPathApplicability,
        ReadPath.class,
        "readPathApplicability"
      );
      boundApplicability = immutableEnumMap(
        boundApplicability,
        BoundConcern.class,
        "boundApplicability"
      );
      twoCardinalityCases = immutableList(twoCardinalityCases, "twoCardinalityCases");
      unsupportedPathCases = immutableList(unsupportedPathCases, "unsupportedPathCases");
      maximumBoundCases = immutableList(maximumBoundCases, "maximumBoundCases");
      filterSelectionBoundaryCase = Objects.requireNonNull(
        filterSelectionBoundaryCase,
        "filterSelectionBoundaryCase"
      );
      capabilityRepetitionCase = Objects.requireNonNull(
        capabilityRepetitionCase,
        "capabilityRepetitionCase"
      );
      zeroStatementCases = immutableList(zeroStatementCases, "zeroStatementCases");
      hierarchyAbsenceCase = Objects.requireNonNull(hierarchyAbsenceCase, "hierarchyAbsenceCase");

      assertReadPathApplicabilityMatchesDefinition(profileId, definition, readPathApplicability);
      assertBoundApplicabilityMatchesDefinition(profileId, definition, boundApplicability);
      assertExactCaseCoverage(
        profileId,
        readPathApplicability,
        twoCardinalityCases.stream().map(TwoCardinalityCase::path).toList(),
        "two-cardinality"
      );
      assertExactUnsupportedPathCoverage(profileId, readPathApplicability, unsupportedPathCases);
      assertExactMaximumBoundCoverage(
        profileId,
        boundApplicability,
        maximumBoundCases.stream().map(MaximumBoundCase::concern).toList(),
        "maximum-bound"
      );
      assertUniqueText(
        zeroStatementCases.stream().map(ZeroStatementCase::caseId).toList(),
        profileId + " zero-statement case IDs"
      );

      boolean hierarchyAbsenceApplies = !definition.hierarchy().enabled();
      if (hierarchyAbsenceCase.applicability().applicable() != hierarchyAbsenceApplies) {
        throw new IllegalArgumentException(
          profileId + " hierarchy-absence applicability disagrees with the selected definition"
        );
      }
      if (
        boundApplicability.get(BoundConcern.COMPLETE_TREE).applicable() &&
        maximumBoundCases
          .stream()
          .filter(candidate -> candidate.concern() == BoundConcern.COMPLETE_TREE)
          .noneMatch(candidate -> candidate.oneOverExpectation().applicable())
      ) {
        throw new IllegalArgumentException(
          profileId + " supporting complete-tree profile lacks one-over-bound stable failure"
        );
      }
    }
  }

  record TwoCardinalityCase(ReadPath path, CardinalityMeasurement measurement) {
    public TwoCardinalityCase {
      path = Objects.requireNonNull(path, "path");
      measurement = Objects.requireNonNull(measurement, "measurement");
    }
  }

  record UnsupportedPathCase(
    ReadPath path,
    Operation missingOperation,
    Supplier<UnsupportedPathObservation> observation
  ) {
    public UnsupportedPathCase {
      path = Objects.requireNonNull(path, "path");
      missingOperation = Objects.requireNonNull(missingOperation, "missingOperation");
      if (missingOperation != path.operation()) {
        throw new IllegalArgumentException(
          path.id() + " must name exact missing operation " + path.operation()
        );
      }
      observation = Objects.requireNonNull(observation, "observation");
    }
  }

  record MaximumBoundCase(
    BoundConcern concern,
    int maximumStatementsAtBound,
    MaximumBoundaryExpectation maximumBoundaryExpectation,
    OneOverExpectation oneOverExpectation,
    BoundaryMeasurement exercise
  ) {
    public MaximumBoundCase(
      BoundConcern concern,
      int maximumStatementsAtBound,
      OneOverExpectation oneOverExpectation,
      BoundaryMeasurement exercise
    ) {
      this(
        concern,
        maximumStatementsAtBound,
        new MaximumBoundaryExpectation(
          true,
          "success",
          false,
          maximumStatementsAtBound,
          "the selected-definition maximum is constructible and executes the real path"
        ),
        oneOverExpectation,
        exercise
      );
    }

    public MaximumBoundCase {
      concern = Objects.requireNonNull(concern, "concern");
      if (maximumStatementsAtBound < 0) {
        throw new IllegalArgumentException("maximumStatementsAtBound must be non-negative");
      }
      maximumBoundaryExpectation = Objects.requireNonNull(
        maximumBoundaryExpectation,
        "maximumBoundaryExpectation"
      );
      oneOverExpectation = Objects.requireNonNull(oneOverExpectation, "oneOverExpectation");
      exercise = Objects.requireNonNull(exercise, "exercise");
      if (maximumBoundaryExpectation.maximumStatements() != maximumStatementsAtBound) {
        throw new IllegalArgumentException(concern + " maximum statement ceilings must agree");
      }
      if (!maximumBoundaryExpectation.constructible()) {
        throw new IllegalArgumentException(
          concern + " maximum must execute; filter selections use their structural case"
        );
      }
      if (!oneOverExpectation.applicable()) {
        throw new IllegalArgumentException(
          concern + " constructible maximum requires executable one-over evidence"
        );
      }
    }
  }

  record MaximumBoundaryExpectation(
    boolean constructible,
    String stableOutcome,
    boolean failureBeforeSql,
    int maximumStatements,
    String evidence
  ) {
    public MaximumBoundaryExpectation {
      stableOutcome = requireText(stableOutcome, "maximum stableOutcome");
      if (maximumStatements < 0) {
        throw new IllegalArgumentException("maximumStatements must be non-negative");
      }
      evidence = requireText(evidence, "maximum-bound evidence");
      if (!constructible && normalizedNotApplicable(stableOutcome)) {
        throw new IllegalArgumentException("non-constructible maximum outcome must be exact");
      }
    }
  }

  record FilterSelectionBoundaryCase(
    int maximumStatementsAtLargestValid,
    int largestValidSelections,
    BoundaryMeasurement exerciseLargestValid,
    Supplier<FilterSelectionStructureObservation> structuralOneOverEvidence
  ) {
    public FilterSelectionBoundaryCase {
      if (maximumStatementsAtLargestValid < 0) {
        throw new IllegalArgumentException("maximumStatementsAtLargestValid must be non-negative");
      }
      if (largestValidSelections < 1) {
        throw new IllegalArgumentException("largestValidSelections must be positive");
      }
      exerciseLargestValid = Objects.requireNonNull(exerciseLargestValid, "exerciseLargestValid");
      structuralOneOverEvidence = Objects.requireNonNull(
        structuralOneOverEvidence,
        "structuralOneOverEvidence"
      );
    }
  }

  record FilterSelectionStructureObservation(
    List<String> selectionSlots,
    List<String> availableSelectionSlots,
    List<String> unavailableSelectionSlots,
    boolean selectionCollectionPresent
  ) {
    public FilterSelectionStructureObservation {
      selectionSlots = immutableTextList(selectionSlots, "selectionSlots");
      availableSelectionSlots = immutableTextList(
        availableSelectionSlots,
        "availableSelectionSlots"
      );
      unavailableSelectionSlots = immutableTextList(
        unavailableSelectionSlots,
        "unavailableSelectionSlots"
      );
      assertUniqueText(selectionSlots, "selectionSlots");
      assertUniqueText(availableSelectionSlots, "availableSelectionSlots");
      assertUniqueText(unavailableSelectionSlots, "unavailableSelectionSlots");
    }
  }

  record CapabilityRepetitionCase(CapabilityRepetitionMeasurement measurement) {
    public CapabilityRepetitionCase {
      measurement = Objects.requireNonNull(measurement, "measurement");
    }
  }

  record CapabilityRepetitionObservation(
    int repetitions,
    List<AdminCategorySystemDefinition> results,
    int registryLookups,
    StatementObservation statements,
    List<String> runtimePortInteractions
  ) {
    public CapabilityRepetitionObservation {
      if (repetitions < 1) {
        throw new IllegalArgumentException("repetitions must be positive");
      }
      results = immutableList(results, "capability results");
      if (results.size() != repetitions) {
        throw new IllegalArgumentException(
          "capability results must contain one result per repetition"
        );
      }
      if (registryLookups < 0) {
        throw new IllegalArgumentException("registryLookups must be non-negative");
      }
      statements = Objects.requireNonNull(statements, "statements");
      runtimePortInteractions = immutableTextList(
        runtimePortInteractions,
        "runtimePortInteractions"
      );
    }
  }

  record OneOverExpectation(
    boolean applicable,
    String stableOutcome,
    boolean failureBeforeSql,
    int maximumStatements,
    String evidence
  ) {
    public OneOverExpectation {
      stableOutcome = requireText(stableOutcome, "stableOutcome");
      if (maximumStatements < 0) {
        throw new IllegalArgumentException("maximumStatements must be non-negative");
      }
      evidence = requireText(evidence, "one-over evidence");
      if (applicable && normalizedNotApplicable(stableOutcome)) {
        throw new IllegalArgumentException("applicable one-over outcome must be exact");
      }
    }
  }

  record ZeroStatementCase(String caseId, Supplier<StatementObservation> observation) {
    public ZeroStatementCase {
      caseId = requireText(caseId, "caseId");
      observation = Objects.requireNonNull(observation, "observation");
    }
  }

  record HierarchyAbsenceCase(
    String caseId,
    Applicability applicability,
    List<String> forbiddenInteractions,
    Supplier<InteractionObservation> observation
  ) {
    public HierarchyAbsenceCase {
      caseId = requireText(caseId, "caseId");
      applicability = Objects.requireNonNull(applicability, "applicability");
      forbiddenInteractions = immutableTextList(forbiddenInteractions, "forbiddenInteractions");
      if (applicability.applicable() && forbiddenInteractions.isEmpty()) {
        throw new IllegalArgumentException(
          "applicable hierarchy absence requires a forbidden-interaction ledger"
        );
      }
      observation = Objects.requireNonNull(observation, "observation");
    }
  }

  record StatementObservation(
    int statementCount,
    int returnedCardinality,
    List<String> statementKinds,
    List<String> normalizedStatementFingerprints,
    List<String> interactions
  ) {
    public StatementObservation {
      if (statementCount < 0 || returnedCardinality < 0) {
        throw new IllegalArgumentException(
          "statement and result cardinalities must be non-negative"
        );
      }
      statementKinds = immutableTextList(statementKinds, "statementKinds");
      normalizedStatementFingerprints = immutableTextList(
        normalizedStatementFingerprints,
        "normalizedStatementFingerprints"
      );
      interactions = immutableTextList(interactions, "interactions");
      if (
        statementKinds.size() != statementCount ||
        normalizedStatementFingerprints.size() != statementCount
      ) {
        throw new IllegalArgumentException(
          "exact statement ledger must contain one kind and fingerprint per statement"
        );
      }
    }
  }

  record BoundaryObservation(
    long requestedValue,
    boolean succeeded,
    String stableOutcome,
    StatementObservation statements
  ) {
    public BoundaryObservation {
      if (requestedValue < 0) {
        throw new IllegalArgumentException("requestedValue must be non-negative");
      }
      stableOutcome = requireText(stableOutcome, "stableOutcome");
      statements = Objects.requireNonNull(statements, "statements");
    }
  }

  record InteractionObservation(int statementCount, List<String> interactions) {
    public InteractionObservation {
      if (statementCount < 0) {
        throw new IllegalArgumentException("statementCount must be non-negative");
      }
      interactions = immutableTextList(interactions, "interactions");
    }
  }

  record UnsupportedPathObservation(
    Operation missingOperation,
    int status,
    String stableErrorCode,
    StatementObservation statements,
    List<String> runtimePortInteractions
  ) {
    public UnsupportedPathObservation {
      missingOperation = Objects.requireNonNull(missingOperation, "missingOperation");
      if (status < 100 || status > 599) {
        throw new IllegalArgumentException("status must be a valid HTTP-equivalent status");
      }
      stableErrorCode = requireText(stableErrorCode, "stableErrorCode");
      statements = Objects.requireNonNull(statements, "statements");
      runtimePortInteractions = immutableTextList(
        runtimePortInteractions,
        "runtimePortInteractions"
      );
    }
  }

  @FunctionalInterface
  interface CardinalityMeasurement {
    StatementObservation measure(int cardinality) throws Exception;
  }

  @FunctionalInterface
  interface BoundaryMeasurement {
    BoundaryObservation observe(long requestedValue) throws Exception;
  }

  @FunctionalInterface
  interface CapabilityRepetitionMeasurement {
    CapabilityRepetitionObservation measure(int repetitions) throws Exception;
  }

  private static void assertReadPathApplicabilityMatchesDefinition(
    String profileId,
    AdminCategorySystemDefinition definition,
    Map<ReadPath, Applicability> applicability
  ) {
    applicability.forEach((path, selected) -> {
      if (selected.applicable() != path.selectedDefinitionSupports(definition)) {
        throw new IllegalArgumentException(
          profileId + " " + path.id() + " applicability disagrees with the selected definition"
        );
      }
    });
  }

  private static void assertBoundApplicabilityMatchesDefinition(
    String profileId,
    AdminCategorySystemDefinition definition,
    Map<BoundConcern, Applicability> applicability
  ) {
    applicability.forEach((concern, selected) -> {
      if (selected.applicable() != concern.selectedDefinitionSupports(definition)) {
        throw new IllegalArgumentException(
          profileId + " " + concern.id() + " applicability disagrees with the selected definition"
        );
      }
    });
  }

  private static <E extends Enum<E>> void assertExactCaseCoverage(
    String profileId,
    Map<E, Applicability> applicability,
    List<E> cases,
    String ledgerName
  ) {
    Set<E> expected = new LinkedHashSet<>();
    applicability.forEach((key, value) -> {
      if (value.applicable()) {
        expected.add(key);
      }
    });
    Set<E> actual = new LinkedHashSet<>(cases);
    if (actual.size() != cases.size()) {
      throw new IllegalArgumentException(profileId + " has duplicate " + ledgerName + " cases");
    }
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException(
        profileId + " " + ledgerName + " cases must cover applicable entries exactly"
      );
    }
  }

  private static void assertExactUnsupportedPathCoverage(
    String profileId,
    Map<ReadPath, Applicability> applicability,
    List<UnsupportedPathCase> cases
  ) {
    Set<ReadPath> expected = new LinkedHashSet<>();
    applicability.forEach((path, selected) -> {
      if (!selected.applicable()) {
        expected.add(path);
      }
    });
    List<ReadPath> paths = cases.stream().map(UnsupportedPathCase::path).toList();
    Set<ReadPath> actual = new LinkedHashSet<>(paths);
    if (actual.size() != paths.size()) {
      throw new IllegalArgumentException(profileId + " has duplicate unsupported-path cases");
    }
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException(
        profileId + " unsupported-path cases must cover inapplicable entries exactly"
      );
    }
  }

  private static void assertExactMaximumBoundCoverage(
    String profileId,
    Map<BoundConcern, Applicability> applicability,
    List<BoundConcern> cases,
    String ledgerName
  ) {
    Set<BoundConcern> expected = applicability
      .entrySet()
      .stream()
      .filter(
        entry -> entry.getValue().applicable() && entry.getKey() != BoundConcern.FILTER_SELECTIONS
      )
      .map(Map.Entry::getKey)
      .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    Set<BoundConcern> actual = new LinkedHashSet<>(cases);
    if (actual.size() != cases.size()) {
      throw new IllegalArgumentException(profileId + " has duplicate " + ledgerName + " cases");
    }
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException(
        profileId + " " + ledgerName + " cases must cover non-filter applicable entries exactly"
      );
    }
  }

  private static <E extends Enum<E>> Map<E, Applicability> immutableEnumMap(
    Map<E, Applicability> values,
    Class<E> enumType,
    String name
  ) {
    Objects.requireNonNull(values, name);
    EnumMap<E, Applicability> copy = new EnumMap<>(enumType);
    values.forEach((key, value) ->
      copy.put(
        Objects.requireNonNull(key, name + " key"),
        Objects.requireNonNull(value, name + " value")
      )
    );
    Set<E> expected = Set.copyOf(Arrays.asList(enumType.getEnumConstants()));
    if (!copy.keySet().equals(expected)) {
      throw new IllegalArgumentException(name + " must classify every entry exactly once");
    }
    return Collections.unmodifiableMap(copy);
  }

  private static <T> List<T> immutableList(List<T> values, String name) {
    List<T> copy = List.copyOf(Objects.requireNonNull(values, name));
    copy.forEach(value -> Objects.requireNonNull(value, name + " entry"));
    return copy;
  }

  private static List<String> immutableTextList(List<String> values, String name) {
    return immutableList(values, name)
      .stream()
      .map(value -> requireText(value, name + " entry"))
      .toList();
  }

  private static void assertUniqueText(List<String> values, String name) {
    if (new HashSet<>(values).size() != values.size()) {
      throw new IllegalArgumentException(name + " must be unique");
    }
  }

  private static boolean normalizedNotApplicable(String value) {
    String normalized = value.replaceAll("[^A-Za-z]", "").toLowerCase(java.util.Locale.ROOT);
    return normalized.equals("na") || normalized.equals("notapplicable");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
