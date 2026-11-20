package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryCreateValidationInput;
import com.ferozfaiz.uniapi.category.support.log.StructuredLogCapture;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableOperations;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseRankOperations;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Executes the nine overlapping catalogue-management route pairs against PostgreSQL while keeping
 * the two public implementations independent.
 */
@AutoConfigureMockMvc
@RecordApplicationEvents
class CatalogueCategoryRouteParityJpaIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  private static final String PUBLIC_BASE = "/api/v1/catalogue/categories";
  private static final String GENERIC_BASE = "/api/v1/admin/category-systems/catalogue/categories";

  private static final Set<String> PATCH_COMPATIBILITY_CASES = Set.of(
    "unknown-member-ignored",
    "parentId-absent",
    "parentId-explicit-null",
    "parentId-present-value",
    "name-absent",
    "name-explicit-null",
    "name-present-value",
    "slug-absent",
    "slug-explicit-null",
    "slug-present-value",
    "active-absent",
    "active-explicit-null",
    "active-present-value",
    "recognized-value-coercion",
    "validation-order",
    "stable-fr-086-outcome"
  );

  @Autowired
  private MockMvc mvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoSpyBean
  private CatalogueCategoryApplicationService categoryService;

  @MockitoSpyBean
  private AdminCategoryApplicationService adminCategoryService;

  @MockitoSpyBean
  private CategorySystemRegistry categorySystemRegistry;

  @Autowired
  private ApplicationEvents applicationEvents;

  @MockitoSpyBean
  private CatalogueSlugWriteLock slugWriteLock;

  @MockitoSpyBean
  private ClosureTableOperations closureTableOperations;

  @MockitoSpyBean
  private SparseRankOperations rankOperations;

  private final List<TransactionSnapshot> catalogueCreateTransactions = new ArrayList<>();
  private final List<TransactionSnapshot> genericCreateTransactions = new ArrayList<>();
  private final List<TransactionSnapshot> genericCapabilityTransactions = new ArrayList<>();
  private final List<TransactionSnapshot> publicCapabilityTransactions = new ArrayList<>();

  @BeforeEach
  void installTransactionObservations() {
    clearTransactionObservations();
    doAnswer(invocation -> {
      catalogueCreateTransactions.add(transactionSnapshot());
      return invocation.callRealMethod();
    })
      .when(categoryService)
      .create(any(UpsertCatalogueCategoryCommand.class));
    doAnswer(invocation -> {
      genericCreateTransactions.add(transactionSnapshot());
      return invocation.callRealMethod();
    })
      .when(adminCategoryService)
      .create(eq("catalogue"), any(AdminCategoryCreateValidationInput.class));
    doAnswer(invocation -> {
      genericCapabilityTransactions.add(transactionSnapshot());
      return invocation.callRealMethod();
    })
      .when(adminCategoryService)
      .capabilities("catalogue");
    doAnswer(invocation -> {
      publicCapabilityTransactions.add(transactionSnapshot());
      return invocation.callRealMethod();
    })
      .when(categorySystemRegistry)
      .requireDescriptor("catalogue");
  }

  private void clearTransactionObservations() {
    catalogueCreateTransactions.clear();
    genericCreateTransactions.clear();
    genericCapabilityTransactions.clear();
    publicCapabilityTransactions.clear();
  }

  private static TransactionSnapshot transactionSnapshot() {
    return new TransactionSnapshot(
      TransactionSynchronizationManager.isActualTransactionActive(),
      TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
      TransactionSynchronizationManager.getCurrentTransactionName()
    );
  }

  @Test
  void ninePairLedgerClassifiesEveryFr039DimensionWithZeroUnclassifiedRows() throws Exception {
    Map<RoutePair, PairRuns> observations = observeAllRoutePairs();
    LedgerSupport support = observeLedgerSupport();
    List<LedgerEntry> ledger = ledger(observations, support);

    assertThat(ledger).hasSize(RoutePair.values().length * Dimension.values().length);
    assertThat(observations).containsOnlyKeys(RoutePair.values());
    assertThat(support.authorization()).containsOnlyKeys(RoutePair.values());
    assertThat(ledger)
      .extracting(LedgerEntry::pair)
      .containsExactlyInAnyOrderElementsOf(repeatedPairs());
    assertThat(ledger).allSatisfy(entry -> {
      assertThat(entry.classification()).isNotEqualTo(Classification.UNCLASSIFIED);
      assertThat(entry.reason()).isNotBlank();
      assertThat(entry.observation()).isNotBlank();
    });
    assertThat(ledgerSummary(ledger))
      .contains("pairs=9")
      .contains("deleteGuards=2")
      .contains("patchCases=16")
      .contains("unclassified=0");
    assertThat(PATCH_COMPATIBILITY_CASES).hasSize(16);
  }

  private Map<RoutePair, PairRuns> observeAllRoutePairs() throws Exception {
    Map<RoutePair, PairRuns> observations = new LinkedHashMap<>();
    observations.put(
      RoutePair.CAPABILITIES,
      runPair(
        () -> {},
        () -> get(PUBLIC_BASE + "/capabilities").with(publicRead()),
        () -> get(GENERIC_BASE + "/capabilities").with(genericRead())
      )
    );
    String createBody = categoryBody(null, "Storage", "storage", true);
    observations.put(
      RoutePair.CREATE,
      assertSuccessfulPair(
        RoutePair.CREATE,
        () -> {},
        () ->
          post(PUBLIC_BASE)
            .with(publicWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody),
        () ->
          post(GENERIC_BASE)
            .with(genericWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody),
        201,
        "category.created",
        "category.create.succeeded"
      )
    );
    Runnable single = () -> createCategory("Storage", "storage", null, true);
    String replaceBody = categoryBody(null, "Storage Updated", "storage-updated", false);
    observations.put(
      RoutePair.REPLACE,
      assertSuccessfulPair(
        RoutePair.REPLACE,
        single,
        () ->
          put(PUBLIC_BASE + "/1")
            .with(publicWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(replaceBody),
        () ->
          put(GENERIC_BASE + "/1")
            .with(genericWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(replaceBody),
        200,
        "category.updated",
        "category.update.succeeded"
      )
    );
    String patchBody = "{\"name\":17,\"active\":\"false\",\"unknown\":true}";
    observations.put(
      RoutePair.PATCH,
      assertSuccessfulPair(
        RoutePair.PATCH,
        single,
        () ->
          patch(PUBLIC_BASE + "/1")
            .with(publicWrite())
            .contentType("application/merge-patch+json")
            .content(patchBody),
        () ->
          patch(GENERIC_BASE + "/1")
            .with(genericWrite())
            .contentType("application/merge-patch+json")
            .content(patchBody),
        200,
        "category.updated",
        "category.patch.succeeded"
      )
    );
    observations.put(
      RoutePair.DELETE,
      assertSuccessfulPair(
        RoutePair.DELETE,
        single,
        () -> delete(PUBLIC_BASE + "/1").with(publicWrite()),
        () -> delete(GENERIC_BASE + "/1").with(genericWrite()),
        204,
        "category.deleted",
        "category.delete.succeeded"
      )
    );
    Runnable hierarchy = () -> {
      var firstParent = createCategory("Storage", "storage", null, true);
      createCategory("Archive", "archive", null, true);
      createCategory("SSDs", "ssds", firstParent.id(), true);
    };
    String moveBody = "{\"newParentId\":2}";
    observations.put(
      RoutePair.MOVE,
      assertSuccessfulPair(
        RoutePair.MOVE,
        hierarchy,
        () ->
          post(PUBLIC_BASE + "/3/move")
            .with(publicWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(moveBody),
        () ->
          post(GENERIC_BASE + "/3/move")
            .with(genericWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(moveBody),
        204,
        "category.moved",
        "category.move.succeeded"
      )
    );
    Runnable siblings = () -> {
      var parent = createCategory("Storage", "storage", null, true);
      createCategory("First", "first", parent.id(), true);
      createCategory("Second", "second", parent.id(), true);
    };
    String reorderBody = "{\"parentId\":1,\"afterId\":3}";
    observations.put(
      RoutePair.REORDER,
      assertSuccessfulPair(
        RoutePair.REORDER,
        siblings,
        () ->
          post(PUBLIC_BASE + "/2/reorder")
            .with(publicWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(reorderBody),
        () ->
          post(GENERIC_BASE + "/2/reorder")
            .with(genericWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(reorderBody),
        200,
        "category.reordered",
        "category.reorder.succeeded"
      )
    );
    observations.put(
      RoutePair.ACTIVATE,
      assertSuccessfulPair(
        RoutePair.ACTIVATE,
        () -> createCategory("Storage", "storage", null, false),
        () -> post(PUBLIC_BASE + "/1/activate").with(publicWrite()),
        () -> post(GENERIC_BASE + "/1/activate").with(genericWrite()),
        204,
        "category.activated",
        "category.activate.succeeded"
      )
    );
    observations.put(
      RoutePair.DEACTIVATE,
      assertSuccessfulPair(
        RoutePair.DEACTIVATE,
        single,
        () -> post(PUBLIC_BASE + "/1/deactivate").with(publicWrite()),
        () -> post(GENERIC_BASE + "/1/deactivate").with(genericWrite()),
        204,
        "category.deactivated",
        "category.deactivate.succeeded"
      )
    );
    return Map.copyOf(observations);
  }

  private LedgerSupport observeLedgerSupport() throws Exception {
    Map<RoutePair, AuthorizationProbe> authorization = new LinkedHashMap<>();
    for (RoutePair pair : RoutePair.values()) {
      authorization.put(
        pair,
        new AuthorizationProbe(
          status(routeRequest(pair, false)),
          status(routeRequest(pair, false).with(wrongPublicAuthority(pair))),
          status(routeRequest(pair, true).with(csrf())),
          status(routeRequest(pair, true).with(wrongGenericAuthority(pair)))
        )
      );
    }

    Map<RoutePair, PairRuns> missing = new LinkedHashMap<>();
    Map<RoutePair, PairRuns> missingParent = new LinkedHashMap<>();
    String missingParentBody = categoryBody(999L, "Storage", "storage", true);
    missingParent.put(
      RoutePair.CREATE,
      runPair(
        () -> {},
        () ->
          post(PUBLIC_BASE)
            .with(publicWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(missingParentBody),
        () ->
          post(GENERIC_BASE)
            .with(genericWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(missingParentBody)
      )
    );
    String replaceBody = categoryBody(null, "Storage", "storage", true);
    missing.put(
      RoutePair.REPLACE,
      runPair(
        () -> {},
        () ->
          put(PUBLIC_BASE + "/99")
            .with(publicWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(replaceBody),
        () ->
          put(GENERIC_BASE + "/99")
            .with(genericWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(replaceBody)
      )
    );
    missing.put(
      RoutePair.PATCH,
      runPair(
        () -> {},
        () ->
          patch(PUBLIC_BASE + "/99")
            .with(publicWrite())
            .contentType("application/merge-patch+json")
            .content("{}"),
        () ->
          patch(GENERIC_BASE + "/99")
            .with(genericWrite())
            .contentType("application/merge-patch+json")
            .content("{}")
      )
    );
    missing.put(
      RoutePair.DELETE,
      runPair(
        () -> {},
        () -> delete(PUBLIC_BASE + "/99").with(publicWrite()),
        () -> delete(GENERIC_BASE + "/99").with(genericWrite())
      )
    );
    missing.put(
      RoutePair.MOVE,
      runPair(
        () -> {},
        () ->
          post(PUBLIC_BASE + "/99/move")
            .with(publicWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newParentId\":null}"),
        () ->
          post(GENERIC_BASE + "/99/move")
            .with(genericWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newParentId\":null}")
      )
    );
    missing.put(
      RoutePair.REORDER,
      runPair(
        () -> {},
        () ->
          post(PUBLIC_BASE + "/99/reorder")
            .with(publicWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"),
        () ->
          post(GENERIC_BASE + "/99/reorder")
            .with(genericWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}")
      )
    );
    missing.put(
      RoutePair.ACTIVATE,
      runPair(
        () -> {},
        () -> post(PUBLIC_BASE + "/99/activate").with(publicWrite()),
        () -> post(GENERIC_BASE + "/99/activate").with(genericWrite())
      )
    );
    missing.put(
      RoutePair.DEACTIVATE,
      runPair(
        () -> {},
        () -> post(PUBLIC_BASE + "/99/deactivate").with(publicWrite()),
        () -> post(GENERIC_BASE + "/99/deactivate").with(genericWrite())
      )
    );

    Runnable categoryTarget = () -> createCategory("Storage", "storage", null, true);
    missingParent.put(
      RoutePair.REPLACE,
      runPair(
        categoryTarget,
        () ->
          put(PUBLIC_BASE + "/1")
            .with(publicWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(missingParentBody),
        () ->
          put(GENERIC_BASE + "/1")
            .with(genericWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(missingParentBody)
      )
    );
    missingParent.put(
      RoutePair.PATCH,
      runPair(
        categoryTarget,
        () ->
          patch(PUBLIC_BASE + "/1")
            .with(publicWrite())
            .contentType("application/merge-patch+json")
            .content("{\"parentId\":999}"),
        () ->
          patch(GENERIC_BASE + "/1")
            .with(genericWrite())
            .contentType("application/merge-patch+json")
            .content("{\"parentId\":999}")
      )
    );
    missingParent.put(
      RoutePair.MOVE,
      runPair(
        categoryTarget,
        () ->
          post(PUBLIC_BASE + "/1/move")
            .with(publicWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newParentId\":999}"),
        () ->
          post(GENERIC_BASE + "/1/move")
            .with(genericWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"newParentId\":999}")
      )
    );
    missingParent.put(
      RoutePair.REORDER,
      runPair(
        categoryTarget,
        () ->
          post(PUBLIC_BASE + "/1/reorder")
            .with(publicWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"parentId\":999}"),
        () ->
          post(GENERIC_BASE + "/1/reorder")
            .with(genericWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"parentId\":999}")
      )
    );

    Map<RoutePair, PairRuns> duplicateSlug = new LinkedHashMap<>();
    Runnable existingSlug = () -> createCategory("Storage", "storage", null, true);
    String duplicateBody = categoryBody(null, "Duplicate", "storage", true);
    duplicateSlug.put(
      RoutePair.CREATE,
      runPair(
        existingSlug,
        () ->
          post(PUBLIC_BASE)
            .with(publicWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(duplicateBody),
        () ->
          post(GENERIC_BASE)
            .with(genericWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(duplicateBody)
      )
    );
    Runnable replacementTarget = () -> {
      createCategory("Storage", "storage", null, true);
      createCategory("Other", "other", null, true);
    };
    duplicateSlug.put(
      RoutePair.REPLACE,
      runPair(
        replacementTarget,
        () ->
          put(PUBLIC_BASE + "/2")
            .with(publicWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(duplicateBody),
        () ->
          put(GENERIC_BASE + "/2")
            .with(genericWrite())
            .contentType(MediaType.APPLICATION_JSON)
            .content(duplicateBody)
      )
    );
    duplicateSlug.put(
      RoutePair.PATCH,
      runPair(
        replacementTarget,
        () ->
          patch(PUBLIC_BASE + "/2")
            .with(publicWrite())
            .contentType("application/merge-patch+json")
            .content("{\"slug\":\"storage\"}"),
        () ->
          patch(GENERIC_BASE + "/2")
            .with(genericWrite())
            .contentType("application/merge-patch+json")
            .content("{\"slug\":\"storage\"}")
      )
    );
    PairRuns patchValidationOrder = runPair(
      () -> {},
      () ->
        patch(PUBLIC_BASE + "/0")
          .with(publicWrite())
          .contentType("application/merge-patch+json")
          .content("{\"name\":null}"),
      () ->
        patch(GENERIC_BASE + "/0")
          .with(genericWrite())
          .contentType("application/merge-patch+json")
          .content("{\"name\":null}")
    );
    return new LedgerSupport(
      Map.copyOf(authorization),
      Map.copyOf(missing),
      Map.copyOf(missingParent),
      Map.copyOf(duplicateSlug),
      patchValidationOrder
    );
  }

  private int status(RequestBuilder request) throws Exception {
    return mvc.perform(request).andReturn().getResponse().getStatus();
  }

  private static RequestPostProcessor wrongPublicAuthority(RoutePair pair) {
    return pair == RoutePair.CAPABILITIES ? publicWrite() : publicRead();
  }

  private static RequestPostProcessor wrongGenericAuthority(RoutePair pair) {
    return pair == RoutePair.CAPABILITIES ? genericWrite() : genericRead();
  }

  private static MockHttpServletRequestBuilder routeRequest(RoutePair pair, boolean generic) {
    String base = generic ? GENERIC_BASE : PUBLIC_BASE;
    return switch (pair) {
      case CAPABILITIES -> get(base + "/capabilities");
      case CREATE -> post(base)
        .contentType(MediaType.APPLICATION_JSON)
        .content(categoryBody(null, "Storage", "storage", true));
      case REPLACE -> put(base + "/1")
        .contentType(MediaType.APPLICATION_JSON)
        .content(categoryBody(null, "Storage", "storage", true));
      case PATCH -> patch(base + "/1")
        .contentType("application/merge-patch+json")
        .content("{}");
      case DELETE -> delete(base + "/1");
      case MOVE -> post(base + "/1/move")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"newParentId\":null}");
      case REORDER -> post(base + "/1/reorder")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"afterId\":2}");
      case ACTIVATE -> post(base + "/1/activate");
      case DEACTIVATE -> post(base + "/1/deactivate");
    };
  }

  @Test
  void capabilitiesPairRetainsCommonRankingFactsAndFrozenRouteSpecificShapes() throws Exception {
    PairRun publicRun = run(() -> {}, () -> get(PUBLIC_BASE + "/capabilities").with(publicRead()));
    PairRun genericRun = run(
      () -> {},
      () -> get(GENERIC_BASE + "/capabilities").with(genericRead())
    );

    assertThat(publicRun.status()).isEqualTo(200);
    assertThat(genericRun.status()).isEqualTo(200);
    assertRequestId(publicRun);
    assertRequestId(genericRun);
    assertThat(publicRun.body().get("rankingEnabled")).isEqualTo(
      genericRun.body().get("rankingEnabled")
    );
    assertThat(publicRun.body().get("placementModes")).isEqualTo(
      genericRun.body().get("placementModes")
    );
    assertThat(publicRun.body().get("sortRankExposed")).isEqualTo(
      genericRun.body().get("sortRankExposed")
    );
    assertThat(publicRun.body().get("fallbackOrder")).isEqualTo(
      genericRun.body().get("fallbackOrder")
    );
    assertThat(publicRun.body().size()).isEqualTo(4);
    assertThat(genericRun.body().size()).isGreaterThan(4);
    assertThat(publicRun.categories()).isEmpty();
    assertThat(genericRun.categories()).isEmpty();
    assertThat(publicRun.catalogueLogTokens()).isEmpty();
    assertThat(genericRun.genericLogTokens()).isEmpty();
    assertThat(publicRun.catalogueLogs()).isEmpty();
    assertThat(publicRun.genericLogs()).isEmpty();
    assertThat(genericRun.catalogueLogs()).isEmpty();
    assertThat(genericRun.genericLogs()).isEmpty();
    assertThat(publicRun.slugLockCalls()).isEmpty();
    assertThat(genericRun.slugLockCalls()).isEmpty();
    assertThat(publicRun.closureCalls()).isEmpty();
    assertThat(genericRun.closureCalls()).isEmpty();
    assertThat(publicRun.rankCalls()).isEmpty();
    assertThat(genericRun.rankCalls()).isEmpty();
    assertThat(publicRun.categoryApplicationEvents()).isZero();
    assertThat(genericRun.categoryApplicationEvents()).isZero();
  }

  @Test
  void createPairPersistsEquivalentRowsWithFrozenLocationsAndLogInventories() throws Exception {
    String body = categoryBody(null, "Storage", "storage", true);

    assertSuccessfulPair(
      RoutePair.CREATE,
      () -> {},
      () ->
        post(PUBLIC_BASE).with(publicWrite()).contentType(MediaType.APPLICATION_JSON).content(body),
      () ->
        post(GENERIC_BASE)
          .with(genericWrite())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body),
      201,
      "category.created",
      "category.create.succeeded"
    );
  }

  @Test
  void replacePairPersistsEquivalentFullUpdatesAndStableRanking() throws Exception {
    Runnable setup = () -> createCategory("Storage", "storage", null, true);
    String body = categoryBody(null, "Storage Updated", "storage-updated", false);

    assertSuccessfulPair(
      RoutePair.REPLACE,
      setup,
      () ->
        put(PUBLIC_BASE + "/1")
          .with(publicWrite())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body),
      () ->
        put(GENERIC_BASE + "/1")
          .with(genericWrite())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body),
      200,
      "category.updated",
      "category.update.succeeded"
    );
  }

  @Test
  void patchPairIgnoresUnknownMembersAndRetainsRecognizedCoercionAndPresenceSemantics()
    throws Exception {
    Runnable setup = () -> createCategory("Storage", "storage", null, true);
    String body = """
      {
        "name": 17,
        "active": "false",
        "unknown": {"ignored": true}
      }
      """;

    PairRuns runs = assertSuccessfulPair(
      RoutePair.PATCH,
      setup,
      () ->
        patch(PUBLIC_BASE + "/1")
          .with(publicWrite())
          .contentType("application/merge-patch+json")
          .content(body),
      () ->
        patch(GENERIC_BASE + "/1")
          .with(genericWrite())
          .contentType("application/merge-patch+json")
          .content(body),
      200,
      "category.updated",
      "category.patch.succeeded"
    );

    assertThat(runs.publicRun().categories().getFirst().name()).isEqualTo("17");
    assertThat(runs.publicRun().categories().getFirst().slug()).isEqualTo("storage");
    assertThat(runs.publicRun().categories().getFirst().active()).isFalse();
    assertThat(PATCH_COMPATIBILITY_CASES).contains(
      "unknown-member-ignored",
      "name-present-value",
      "slug-absent",
      "active-present-value",
      "recognized-value-coercion",
      "stable-fr-086-outcome"
    );
  }

  @Test
  void patchCompatibilityMatrixExecutesAllSixteenFr086CasesOnBothRoutes() throws Exception {
    List<PatchCase> cases = List.of(
      new PatchCase("unknown-member-ignored", PatchSetup.ROOT, 1L, "{\"unknown\":17}", 200, 200),
      new PatchCase("parentId-absent", PatchSetup.ROOT, 1L, "{\"name\":\"Renamed\"}", 200, 200),
      new PatchCase(
        "parentId-explicit-null",
        PatchSetup.CHILD,
        2L,
        "{\"parentId\":null}",
        200,
        200
      ),
      new PatchCase("parentId-present-value", PatchSetup.ROOT, 1L, "{\"parentId\":2}", 200, 200),
      new PatchCase("name-absent", PatchSetup.ROOT, 1L, "{\"active\":false}", 200, 200),
      new PatchCase("name-explicit-null", PatchSetup.ROOT, 1L, "{\"name\":null}", 400, 400),
      new PatchCase("name-present-value", PatchSetup.ROOT, 1L, "{\"name\":\"Renamed\"}", 200, 200),
      new PatchCase("slug-absent", PatchSetup.ROOT, 1L, "{\"name\":\"Renamed\"}", 200, 200),
      new PatchCase("slug-explicit-null", PatchSetup.ROOT, 1L, "{\"slug\":null}", 400, 400),
      new PatchCase("slug-present-value", PatchSetup.ROOT, 1L, "{\"slug\":\"renamed\"}", 200, 200),
      new PatchCase("active-absent", PatchSetup.ROOT, 1L, "{\"name\":\"Renamed\"}", 200, 200),
      new PatchCase("active-explicit-null", PatchSetup.ROOT, 1L, "{\"active\":null}", 400, 400),
      new PatchCase("active-present-value", PatchSetup.ROOT, 1L, "{\"active\":false}", 200, 200),
      new PatchCase(
        "recognized-value-coercion",
        PatchSetup.ROOT,
        1L,
        "{\"parentId\":\"2\",\"name\":17,\"slug\":false,\"active\":\"true\"}",
        200,
        200
      ),
      new PatchCase("validation-order", PatchSetup.NONE, 0L, "{\"name\":null}", 404, 400),
      new PatchCase("stable-fr-086-outcome", PatchSetup.ROOT, 1L, "{\"name\":", 400, 400)
    );

    List<String> executed = new ArrayList<>();
    for (PatchCase patchCase : cases) {
      PairRuns runs = runPair(
        () -> setupPatchCase(patchCase.setup()),
        () -> patchRequest(PUBLIC_BASE, patchCase, publicWrite()),
        () -> patchRequest(GENERIC_BASE, patchCase, genericWrite())
      );
      assertPatchCase(patchCase, runs);
      executed.add(patchCase.name());
    }

    assertThat(executed).containsExactlyInAnyOrderElementsOf(PATCH_COMPATIBILITY_CASES);
  }

  private void setupPatchCase(PatchSetup setup) {
    switch (setup) {
      case NONE -> {
      }
      case ROOT -> {
        createCategory("Storage", "storage", null, true);
        createCategory("Archive", "archive", null, true);
      }
      case CHILD -> {
        var parent = createCategory("Archive", "archive", null, true);
        createCategory("Storage", "storage", parent.id(), true);
      }
    }
  }

  private static RequestBuilder patchRequest(
    String base,
    PatchCase patchCase,
    RequestPostProcessor authorization
  ) {
    return patch(base + "/" + patchCase.targetId())
      .with(authorization)
      .contentType("application/merge-patch+json")
      .content(patchCase.body());
  }

  private static void assertPatchCase(PatchCase patchCase, PairRuns runs) {
    assertThat(runs.publicRun().status())
      .as(patchCase.name() + " public")
      .isEqualTo(patchCase.publicStatus());
    assertThat(runs.genericRun().status())
      .as(patchCase.name() + " generic")
      .isEqualTo(patchCase.genericStatus());
    assertThat(runs.publicRun().categories()).isEqualTo(runs.genericRun().categories());
    assertThat(runs.publicRun().closures()).isEqualTo(runs.genericRun().closures());
    assertThat(runs.publicRun().categoryApplicationEvents()).isZero();
    assertThat(runs.genericRun().categoryApplicationEvents()).isZero();

    if (patchCase.publicStatus() == 200) {
      assertEquivalentCategoryBodies(runs.publicRun().body(), runs.genericRun().body());
      assertMutationObservation(runs.publicRun(), false);
      assertMutationObservation(runs.genericRun(), true);
      StoredCategory target = runs
        .publicRun()
        .categories()
        .stream()
        .filter(category -> category.id().equals(patchCase.targetId()))
        .findFirst()
        .orElseThrow();
      switch (patchCase.name()) {
        case "unknown-member-ignored" -> assertThat(target.name()).isEqualTo("Storage");
        case "parentId-absent" -> assertThat(target.parentId()).isNull();
        case "parentId-explicit-null" -> assertThat(target.parentId()).isNull();
        case "parentId-present-value" -> assertThat(target.parentId()).isEqualTo(2L);
        case "name-absent" -> assertThat(target.name()).isEqualTo("Storage");
        case "name-present-value" -> assertThat(target.name()).isEqualTo("Renamed");
        case "slug-absent" -> assertThat(target.slug()).isEqualTo("storage");
        case "slug-present-value" -> assertThat(target.slug()).isEqualTo("renamed");
        case "active-absent" -> assertThat(target.active()).isTrue();
        case "active-present-value" -> assertThat(target.active()).isFalse();
        case "recognized-value-coercion" -> {
          assertThat(target.parentId()).isEqualTo(2L);
          assertThat(target.name()).isEqualTo("17");
          assertThat(target.slug()).isEqualTo("false");
          assertThat(target.active()).isTrue();
        }
        default -> throw new AssertionError(
          "unclassified successful patch case: " + patchCase.name()
        );
      }
      return;
    }

    assertThat(runs.publicRun().catalogueLogTokens()).isEmpty();
    assertThat(runs.genericRun().catalogueLogTokens()).isEmpty();
    assertThat(runs.publicRun().genericLogTokens()).isEmpty();
    assertThat(runs.genericRun().genericLogTokens()).isEmpty();
    assertRolledBack(runs);
    switch (patchCase.name()) {
      case "name-explicit-null" -> assertPatchError(
        runs,
        patchCase.targetId(),
        400,
        "VALIDATION_FAILED",
        "category name is required",
        400,
        "VALIDATION_FAILED",
        "category name is required"
      );
      case "slug-explicit-null" -> assertPatchError(
        runs,
        patchCase.targetId(),
        400,
        "VALIDATION_FAILED",
        "category slug is required",
        400,
        "VALIDATION_FAILED",
        "slug may contain lowercase letters, numbers, and hyphens"
      );
      case "active-explicit-null" -> assertPatchError(
        runs,
        patchCase.targetId(),
        400,
        "VALIDATION_FAILED",
        "active cannot be null in a merge-patch request",
        400,
        "VALIDATION_FAILED",
        "active cannot be null in a merge-patch request"
      );
      case "validation-order" -> {
        assertPatchError(
          runs,
          patchCase.targetId(),
          404,
          "CATEGORY_NOT_FOUND",
          "category was not found",
          400,
          "VALIDATION_FAILED",
          "category.api.category.id.positive"
        );
      }
      case "stable-fr-086-outcome" -> assertPatchError(
        runs,
        patchCase.targetId(),
        400,
        "MALFORMED_REQUEST",
        "Request body is invalid",
        400,
        "MALFORMED_REQUEST",
        "Request body is invalid"
      );
      default -> throw new AssertionError("unclassified failing patch case: " + patchCase.name());
    }
  }

  private static void assertPatchError(
    PairRuns runs,
    Long id,
    int publicStatus,
    String publicCode,
    String publicMessage,
    int genericStatus,
    String genericCode,
    String genericMessage
  ) {
    assertExactError(
      runs.publicRun(),
      publicStatus,
      publicCode,
      publicMessage,
      routePath(PUBLIC_BASE, RoutePair.PATCH, id)
    );
    assertExactError(
      runs.genericRun(),
      genericStatus,
      genericCode,
      genericMessage,
      routePath(GENERIC_BASE, RoutePair.PATCH, id)
    );
  }

  @Test
  void deletePairRemovesTheSameLeafAndRetainsIndependentChildAndProductGuards() throws Exception {
    Runnable leafSetup = () -> createCategory("Storage", "storage", null, true);
    assertSuccessfulPair(
      RoutePair.DELETE,
      leafSetup,
      () -> delete(PUBLIC_BASE + "/1").with(publicWrite()),
      () -> delete(GENERIC_BASE + "/1").with(genericWrite()),
      204,
      "category.deleted",
      "category.delete.succeeded"
    );

    PairRuns childGuard = runPair(
      () -> {
        var parent = createCategory("Storage", "storage", null, true);
        createCategory("SSDs", "ssds", parent.id(), true);
      },
      () -> delete(PUBLIC_BASE + "/1").with(publicWrite()),
      () -> delete(GENERIC_BASE + "/1").with(genericWrite())
    );
    assertGuardRollback(childGuard);

    PairRuns productGuard = runPair(
      this::seedAssignedProductCategory,
      () -> delete(PUBLIC_BASE + "/1").with(publicWrite()),
      () -> delete(GENERIC_BASE + "/1").with(genericWrite())
    );
    assertGuardRollback(productGuard);
  }

  @Test
  void movePairPersistsEquivalentHierarchyAndFreezesRouteSpecificInvalidMoveErrors()
    throws Exception {
    Runnable setup = () -> {
      var firstParent = createCategory("Storage", "storage", null, true);
      createCategory("Archive", "archive", null, true);
      createCategory("SSDs", "ssds", firstParent.id(), true);
    };
    String body = "{\"newParentId\":2}";

    assertSuccessfulPair(
      RoutePair.MOVE,
      setup,
      () ->
        post(PUBLIC_BASE + "/3/move")
          .with(publicWrite())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body),
      () ->
        post(GENERIC_BASE + "/3/move")
          .with(genericWrite())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body),
      204,
      "category.moved",
      "category.move.succeeded"
    );

    PairRuns invalid = runPair(
      setup,
      () ->
        post(PUBLIC_BASE + "/1/move")
          .with(publicWrite())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"newParentId\":3}"),
      () ->
        post(GENERIC_BASE + "/1/move")
          .with(genericWrite())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"newParentId\":3}")
    );
    assertExactError(
      invalid.publicRun(),
      409,
      "CATEGORY_MOVE_REJECTED",
      "category move is invalid",
      routePath(PUBLIC_BASE, RoutePair.MOVE, 1L)
    );
    assertExactError(
      invalid.genericRun(),
      400,
      "INVALID_CATEGORY_MOVE",
      "category move is invalid",
      routePath(GENERIC_BASE, RoutePair.MOVE, 1L)
    );
    assertRolledBack(invalid);
  }

  @Test
  void reorderPairPersistsEquivalentSiblingOrderRanksAndHierarchy() throws Exception {
    Runnable setup = () -> {
      var parent = createCategory("Storage", "storage", null, true);
      createCategory("First", "first", parent.id(), true);
      createCategory("Second", "second", parent.id(), true);
    };
    String body = "{\"parentId\":1,\"afterId\":3}";

    assertSuccessfulPair(
      RoutePair.REORDER,
      setup,
      () ->
        post(PUBLIC_BASE + "/2/reorder")
          .with(publicWrite())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body),
      () ->
        post(GENERIC_BASE + "/2/reorder")
          .with(genericWrite())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body),
      200,
      "category.reordered",
      "category.reorder.succeeded"
    );
  }

  @Test
  void activatePairPersistsEquivalentLifecycleStateWithoutChangingRankOrParent() throws Exception {
    Runnable setup = () -> createCategory("Storage", "storage", null, false);

    PairRuns runs = assertSuccessfulPair(
      RoutePair.ACTIVATE,
      setup,
      () -> post(PUBLIC_BASE + "/1/activate").with(publicWrite()),
      () -> post(GENERIC_BASE + "/1/activate").with(genericWrite()),
      204,
      "category.activated",
      "category.activate.succeeded"
    );
    assertThat(runs.publicRun().categories().getFirst().active()).isTrue();
  }

  @Test
  void deactivatePairPersistsEquivalentLifecycleStateWithoutChangingRankOrParent()
    throws Exception {
    Runnable setup = () -> createCategory("Storage", "storage", null, true);

    PairRuns runs = assertSuccessfulPair(
      RoutePair.DEACTIVATE,
      setup,
      () -> post(PUBLIC_BASE + "/1/deactivate").with(publicWrite()),
      () -> post(GENERIC_BASE + "/1/deactivate").with(genericWrite()),
      204,
      "category.deactivated",
      "category.deactivate.succeeded"
    );
    assertThat(runs.publicRun().categories().getFirst().active()).isFalse();
  }

  private PairRuns assertSuccessfulPair(
    RoutePair pair,
    Runnable setup,
    Supplier<RequestBuilder> publicRequest,
    Supplier<RequestBuilder> genericRequest,
    int expectedStatus,
    String catalogueLogToken,
    String genericLogToken
  ) throws Exception {
    PairRuns runs = runPair(setup, publicRequest, genericRequest);

    assertThat(runs.publicRun().status())
      .as(pair + " public status")
      .isEqualTo(expectedStatus);
    assertThat(runs.genericRun().status())
      .as(pair + " generic status")
      .isEqualTo(expectedStatus);
    assertRequestId(runs.publicRun());
    assertRequestId(runs.genericRun());
    assertThat(runs.publicRun().categories()).isEqualTo(runs.genericRun().categories());
    assertThat(runs.publicRun().closures()).isEqualTo(runs.genericRun().closures());
    assertThat(runs.publicRun().catalogueLogTokens()).containsExactly(catalogueLogToken);
    assertThat(runs.genericRun().catalogueLogTokens()).containsExactly(catalogueLogToken);
    assertThat(runs.publicRun().genericLogTokens()).isEmpty();
    assertThat(runs.genericRun().genericLogTokens()).containsExactly(genericLogToken);
    assertMutationObservation(runs.publicRun(), false);
    assertMutationObservation(runs.genericRun(), true);
    assertThat(runs.publicRun().slugLockCalls()).isEqualTo(runs.genericRun().slugLockCalls());
    assertThat(runs.publicRun().closureCalls()).isEqualTo(runs.genericRun().closureCalls());
    assertThat(runs.publicRun().rankCalls()).isEqualTo(runs.genericRun().rankCalls());
    if (EnumSet.of(RoutePair.CREATE, RoutePair.REPLACE, RoutePair.PATCH).contains(pair)) {
      assertThat(runs.publicRun().slugLockCalls()).contains("lock");
    }
    if (EnumSet.of(RoutePair.CREATE, RoutePair.MOVE, RoutePair.REORDER).contains(pair)) {
      assertThat(runs.publicRun().rankCalls()).isNotEmpty();
    }
    if (EnumSet.of(RoutePair.CREATE, RoutePair.MOVE).contains(pair)) {
      assertThat(runs.publicRun().closureCalls()).isNotEmpty();
    }
    if (expectedStatus == 200 || expectedStatus == 201) {
      assertEquivalentCategoryBodies(runs.publicRun().body(), runs.genericRun().body());
    } else {
      assertThat(runs.publicRun().body()).isNull();
      assertThat(runs.genericRun().body()).isNull();
    }
    if (pair == RoutePair.CREATE) {
      assertThat(runs.publicRun().location()).endsWith(PUBLIC_BASE + "/1");
      assertThat(runs.genericRun().location()).endsWith(GENERIC_BASE + "/1");
    } else {
      assertThat(runs.publicRun().location()).isNull();
      assertThat(runs.genericRun().location()).isNull();
    }
    return runs;
  }

  private static void assertMutationObservation(PairRun run, boolean genericRoute) {
    assertThat(run.categoryApplicationEvents()).isZero();
    assertThat(run.catalogueLogs()).allSatisfy(log -> {
      assertThat(log.loggerName()).isEqualTo(CatalogueCategoryApplicationService.class.getName());
      assertThat(log.message()).isNotBlank();
      assertThat(log.transactionActive()).isTrue();
      assertThat(log.fields()).containsKey("catalogue.event");
    });
    if (genericRoute) {
      assertThat(run.genericLogs())
        .singleElement()
        .satisfies(log -> {
          assertThat(log.loggerName()).isEqualTo(AdminCategoryApplicationService.class.getName());
          assertThat(log.message()).isEqualTo("admin category mutation succeeded");
          assertThat(log.transactionActive()).isTrue();
          assertThat(log.fields()).containsKey("category.event");
        });
    } else {
      assertThat(run.genericLogs()).isEmpty();
    }
  }

  private PairRuns runPair(
    Runnable setup,
    Supplier<RequestBuilder> publicRequest,
    Supplier<RequestBuilder> genericRequest
  ) throws Exception {
    return new PairRuns(run(setup, publicRequest), run(setup, genericRequest));
  }

  private PairRun run(Runnable setup, Supplier<RequestBuilder> request) throws Exception {
    cleanCatalogueTables();
    setup.run();
    List<StoredCategory> beforeCategories = categorySnapshot();
    List<StoredClosure> beforeClosures = closureSnapshot();
    clearTransactionObservations();
    clearInvocations(slugWriteLock, closureTableOperations, rankOperations);
    applicationEvents.clear();
    try (
      StructuredLogCapture catalogueLogs = StructuredLogCapture.attach(
        CatalogueCategoryApplicationService.class
      );
      StructuredLogCapture genericLogs = StructuredLogCapture.attach(
        AdminCategoryApplicationService.class
      )
    ) {
      MvcResult result = mvc.perform(request.get()).andReturn();
      return new PairRun(
        result.getResponse().getStatus(),
        parseBody(result),
        result.getResponse().getHeader("Location"),
        result.getResponse().getHeader("X-Request-Id"),
        requestBytes(result),
        beforeCategories,
        beforeClosures,
        categorySnapshot(),
        closureSnapshot(),
        structuredFieldValues(catalogueLogs, "catalogue.event"),
        structuredFieldValues(genericLogs, "category.event"),
        logSnapshots(catalogueLogs),
        logSnapshots(genericLogs),
        invocationMethods(slugWriteLock),
        invocationMethods(closureTableOperations),
        invocationMethods(rankOperations),
        categoryApplicationEventCount(),
        List.copyOf(catalogueCreateTransactions),
        List.copyOf(genericCreateTransactions),
        List.copyOf(genericCapabilityTransactions),
        List.copyOf(publicCapabilityTransactions)
      );
    }
  }

  private long categoryApplicationEventCount() {
    return applicationEvents
      .stream()
      .filter(event -> {
        String type = event.getClass().getName();
        return (
          type.startsWith("com.ferozfaiz.uniapi.catalogue.") ||
          type.startsWith("com.ferozfaiz.uniapi.category.")
        );
      })
      .count();
  }

  private static List<ObservedLog> logSnapshots(StructuredLogCapture capture) {
    return capture
      .events()
      .stream()
      .map(event ->
        new ObservedLog(
          event.loggerName(),
          event.message(),
          event.transactionActive(),
          event
            .structuredFields()
            .stream()
            .collect(
              java.util.stream.Collectors.toMap(
                StructuredLogCapture.StructuredField::key,
                StructuredLogCapture.StructuredField::value,
                (first, second) -> first,
                LinkedHashMap::new
              )
            )
        )
      )
      .toList();
  }

  private static List<String> invocationMethods(Object spy) {
    return mockingDetails(spy)
      .getInvocations()
      .stream()
      .map(invocation -> invocation.getMethod().getName())
      .toList();
  }

  private JsonNode parseBody(MvcResult result) throws Exception {
    String body = result.getResponse().getContentAsString();
    return body.isBlank() ? null : objectMapper.readTree(body);
  }

  private static int requestBytes(MvcResult result) {
    byte[] content = result.getRequest().getContentAsByteArray();
    return content == null ? 0 : content.length;
  }

  private List<StoredCategory> categorySnapshot() {
    return jdbc.query(
      """
      SELECT id, parent_id, sort_rank, name, slug, is_active
      FROM catalogue_category
      ORDER BY id
      """,
      (row, rowNumber) ->
        new StoredCategory(
          row.getLong("id"),
          nullableLong(row, "parent_id"),
          row.getLong("sort_rank"),
          row.getString("name"),
          row.getString("slug"),
          row.getBoolean("is_active")
        )
    );
  }

  private List<StoredClosure> closureSnapshot() {
    return jdbc.query(
      """
      SELECT ancestor_id, descendant_id, depth
      FROM catalogue_category_closure
      ORDER BY ancestor_id, descendant_id, depth
      """,
      (row, rowNumber) ->
        new StoredClosure(
          row.getLong("ancestor_id"),
          row.getLong("descendant_id"),
          row.getInt("depth")
        )
    );
  }

  private static Long nullableLong(java.sql.ResultSet row, String column)
    throws java.sql.SQLException {
    long value = row.getLong(column);
    return row.wasNull() ? null : value;
  }

  private static List<String> structuredFieldValues(StructuredLogCapture capture, String key) {
    return capture
      .events()
      .stream()
      .flatMap(event -> event.structuredFields().stream())
      .filter(field -> field.key().equals(key))
      .map(StructuredLogCapture.StructuredField::value)
      .toList();
  }

  private void assertGuardRollback(PairRuns runs) {
    assertExactError(
      runs.publicRun(),
      409,
      "CATEGORY_DELETE_REJECTED",
      "category cannot be deleted because dependent catalogue data exists",
      routePath(PUBLIC_BASE, RoutePair.DELETE, 1L)
    );
    assertExactError(
      runs.genericRun(),
      409,
      "CATEGORY_DELETE_BLOCKED",
      "category cannot be deleted because dependent data exists",
      routePath(GENERIC_BASE, RoutePair.DELETE, 1L)
    );
    assertRolledBack(runs);
    assertThat(runs.publicRun().catalogueLogTokens()).isEmpty();
    assertThat(runs.genericRun().catalogueLogTokens()).isEmpty();
    assertThat(runs.publicRun().genericLogTokens()).isEmpty();
    assertThat(runs.genericRun().genericLogTokens()).isEmpty();
  }

  private static String errorCode(PairRun run) {
    return run.body().path("code").asText();
  }

  private static void assertEquivalentCategoryBodies(JsonNode publicBody, JsonNode genericBody) {
    for (String field : List.of("id", "parentId", "sortRank", "name", "slug", "active")) {
      assertThat(publicBody.get(field)).as(field).isEqualTo(genericBody.get(field));
    }
    assertThat(publicBody.path("id").isIntegralNumber()).isTrue();
    assertThat(genericBody.path("id").isIntegralNumber()).isTrue();
    if (!publicBody.path("sortRank").isNull()) {
      assertThat(publicBody.path("sortRank").isIntegralNumber()).isTrue();
      assertThat(genericBody.path("sortRank").isIntegralNumber()).isTrue();
    }
  }

  private static void assertRequestId(PairRun run) {
    assertThat(run.requestId()).isNotBlank();
  }

  private com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory createCategory(
    String name,
    String slug,
    Long parentId,
    boolean active
  ) {
    return categoryService.create(
      new UpsertCatalogueCategoryCommand(null, parentId, name, slug, active)
    );
  }

  private void seedAssignedProductCategory() {
    var category = createCategory("CPUs", "cpus", null, true);
    Long brand = insertBrand("AMD", "amd");
    Long manufacturer = insertManufacturer("AMD", "amd-manufacturer");
    Long product = insertProduct(
      "Ryzen 7 7800X3D",
      "ryzen-7-7800x3d",
      ProductFamily.CPU,
      brand,
      manufacturer,
      true
    );
    jdbc.update(
      "INSERT INTO catalogue_product_category (product_id, category_id) VALUES (?, ?)",
      product,
      category.id()
    );
  }

  private static String categoryBody(Long parentId, String name, String slug, boolean active) {
    String parent = parentId == null ? "null" : parentId.toString();
    return (
      """
      {
        "parentId": %s,
        "name": "%s",
        "slug": "%s",
        "active": %s
      }
      """
    ).formatted(parent, name, slug, active);
  }

  private static RequestPostProcessor publicRead() {
    return jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:read"));
  }

  private static RequestPostProcessor publicWrite() {
    return jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:write"));
  }

  private static RequestPostProcessor genericRead() {
    return jwt().authorities(new SimpleGrantedAuthority("admin:catalogue-categories:read"));
  }

  private static RequestPostProcessor genericWrite() {
    return jwt().authorities(new SimpleGrantedAuthority("admin:catalogue-categories:write"));
  }

  private static List<LedgerEntry> ledger(
    Map<RoutePair, PairRuns> observations,
    LedgerSupport support
  ) {
    List<LedgerEntry> entries = new ArrayList<>();
    for (RoutePair pair : RoutePair.values()) {
      for (Dimension dimension : Dimension.values()) {
        LedgerEntry classified = classify(pair, dimension);
        entries.add(
          new LedgerEntry(
            pair,
            dimension,
            classified.classification(),
            classified.reason(),
            observe(pair, dimension, observations.get(pair), support)
          )
        );
      }
    }
    return List.copyOf(entries);
  }

  private static String observe(
    RoutePair pair,
    Dimension dimension,
    PairRuns runs,
    LedgerSupport support
  ) {
    PairRun publicRun = runs.publicRun();
    PairRun genericRun = runs.genericRun();
    return switch (dimension) {
      case AUTHORIZATION -> {
        AuthorizationProbe probe = support.authorization().get(pair);
        assertThat(probe).isNotNull();
        assertThat(probe.publicUnauthenticated()).isEqualTo(401);
        assertThat(probe.publicForbidden()).isEqualTo(403);
        assertThat(probe.genericUnauthenticated()).isEqualTo(401);
        assertThat(probe.genericForbidden()).isEqualTo(403);
        yield "AUTH-" +
          pair +
          "=public 401/403; generic 401/403; accepted " +
          publicRun.status() +
          "/" +
          genericRun.status();
      }
      case TRANSPORT_DECODING -> {
        if (
          EnumSet.of(
            RoutePair.CAPABILITIES,
            RoutePair.DELETE,
            RoutePair.ACTIVATE,
            RoutePair.DEACTIVATE
          ).contains(pair)
        ) {
          assertThat(publicRun.requestBytes()).isZero();
          assertThat(genericRun.requestBytes()).isZero();
        } else {
          assertThat(publicRun.requestBytes()).isPositive();
          assertThat(genericRun.requestBytes()).isPositive();
        }
        yield "requestBytes=" + publicRun.requestBytes() + "/" + genericRun.requestBytes();
      }
      case VALIDATION_ORDER -> {
        if (pair == RoutePair.PATCH) {
          assertThat(support.patchValidationOrder().publicRun().status()).isEqualTo(404);
          assertThat(support.patchValidationOrder().genericRun().status()).isEqualTo(400);
          yield "PATCH-VALIDATION-ORDER=" + errorObservation(support.patchValidationOrder());
        }
        yield "successful request reached " + publicRun.status() + "/" + genericRun.status();
      }
      case STATUS -> "public=" + publicRun.status() + ", generic=" + genericRun.status();
      case BODY_SHAPE -> "publicFields=" +
        bodySize(publicRun.body()) +
        ", genericFields=" +
        bodySize(genericRun.body());
      case HEADERS -> "requestIds=" +
        !publicRun.requestId().isBlank() +
        "/" +
        !genericRun.requestId().isBlank();
      case LOCATION -> {
        if (pair != RoutePair.CREATE) {
          assertThat(publicRun.location()).isNull();
          assertThat(genericRun.location()).isNull();
        }
        yield "public=" + publicRun.location() + ", generic=" + genericRun.location();
      }
      case NUMERIC_IDENTIFIERS -> {
        if (pair == RoutePair.CAPABILITIES) {
          assertThat(publicRun.body().has("id")).isFalse();
          assertThat(genericRun.body().has("id")).isFalse();
        }
        yield "integralIds=" + integralId(publicRun.body()) + "/" + integralId(genericRun.body());
      }
      case STABLE_ERRORS -> {
        if (pair == RoutePair.CAPABILITIES) {
          yield "ERROR-CAPABILITIES=" +
            support.authorization().get(pair).publicForbidden() +
            "/" +
            support.authorization().get(pair).genericForbidden();
        }
        yield "ERROR-" + pair + "=" + assertMissingEvidence(pair, support);
      }
      case DUPLICATE_SLUG -> {
        PairRuns probe = support.duplicateSlug().get(pair);
        if (probe == null) {
          assertThat(publicRun.slugLockCalls()).isEmpty();
          assertThat(genericRun.slugLockCalls()).isEmpty();
          yield "N/A-DUPLICATE-" + pair + "=zero slug-lock calls";
        }
        assertDuplicateError(pair, probe);
        yield "DUPLICATE-" + pair + "=" + errorObservation(probe);
      }
      case MISSING_CATEGORY_OR_PARENT -> {
        if (pair == RoutePair.CAPABILITIES) {
          assertThat(publicRun.body().has("id")).isFalse();
          assertThat(genericRun.body().has("id")).isFalse();
          yield "N/A-MISSING-CAPABILITIES=no category identifier";
        }
        yield "MISSING-" + pair + "=" + assertMissingEvidence(pair, support);
      }
      case MOVE_OR_PLACEMENT -> {
        if (
          !EnumSet.of(
            RoutePair.CREATE,
            RoutePair.REPLACE,
            RoutePair.PATCH,
            RoutePair.MOVE,
            RoutePair.REORDER
          ).contains(pair)
        ) {
          assertThat(publicRun.closureCalls()).isEmpty();
          assertThat(publicRun.rankCalls()).isEmpty();
        }
        yield "rows=" +
          publicRun.categories().size() +
          ", closureCalls=" +
          publicRun.closureCalls();
      }
      case LIFECYCLE -> {
        if (!EnumSet.of(RoutePair.ACTIVATE, RoutePair.DEACTIVATE).contains(pair)) {
          assertThat(publicRun.catalogueLogTokens()).doesNotContain(
            "category.activated",
            "category.deactivated"
          );
          assertThat(genericRun.catalogueLogTokens()).doesNotContain(
            "category.activated",
            "category.deactivated"
          );
        }
        yield "activeStates=" +
          publicRun.categories().stream().map(StoredCategory::active).toList();
      }
      case TRANSACTION_OR_ROLLBACK -> transactionObservation(pair, runs, support);
      case LOCKING -> {
        if (
          !EnumSet.of(
            RoutePair.CREATE,
            RoutePair.REPLACE,
            RoutePair.PATCH,
            RoutePair.MOVE,
            RoutePair.REORDER
          ).contains(pair)
        ) {
          assertThat(publicRun.slugLockCalls()).isEmpty();
          assertThat(publicRun.closureCalls()).isEmpty();
          assertThat(publicRun.rankCalls()).isEmpty();
        }
        yield "slug=" + publicRun.slugLockCalls() + ", closure=" + publicRun.closureCalls();
      }
      case RANKING -> "rankCalls=" +
        publicRun.rankCalls() +
        ", ranks=" +
        publicRun.categories().stream().map(StoredCategory::sortRank).toList();
      case EVENTS -> "applicationEvents=" +
        publicRun.categoryApplicationEvents() +
        "/" +
        genericRun.categoryApplicationEvents();
      case STRUCTURED_LOGS -> "public=" + logNames(publicRun) + ", generic=" + logNames(genericRun);
      case PERSISTED_RESULT -> "rows=" +
        publicRun.categories().size() +
        "/" +
        genericRun.categories().size() +
        ", closures=" +
        publicRun.closures().size() +
        "/" +
        genericRun.closures().size();
    };
  }

  private static String assertMissingEvidence(RoutePair pair, LedgerSupport support) {
    List<String> observations = new ArrayList<>();
    PairRuns missingCategory = support.missing().get(pair);
    if (missingCategory != null) {
      assertExactError(
        missingCategory.publicRun(),
        404,
        "CATEGORY_NOT_FOUND",
        "category was not found",
        routePath(PUBLIC_BASE, pair, 99L)
      );
      assertExactError(
        missingCategory.genericRun(),
        404,
        "CATEGORY_NOT_FOUND",
        "category was not found",
        routePath(GENERIC_BASE, pair, 99L)
      );
      assertRolledBack(missingCategory);
      observations.add("category=" + errorObservation(missingCategory));
    }
    PairRuns missingParent = support.missingParent().get(pair);
    if (missingParent != null) {
      Long id = pair == RoutePair.CREATE ? null : 1L;
      assertExactError(
        missingParent.publicRun(),
        404,
        "CATEGORY_NOT_FOUND",
        "category was not found",
        routePath(PUBLIC_BASE, pair, id)
      );
      assertExactError(
        missingParent.genericRun(),
        404,
        "CATEGORY_PARENT_NOT_FOUND",
        "category parent was not found",
        routePath(GENERIC_BASE, pair, id)
      );
      assertRolledBack(missingParent);
      observations.add("parent=" + errorObservation(missingParent));
    }
    assertThat(observations).isNotEmpty();
    return String.join(",", observations);
  }

  private static String transactionObservation(
    RoutePair pair,
    PairRuns runs,
    LedgerSupport support
  ) {
    PairRun publicRun = runs.publicRun();
    PairRun genericRun = runs.genericRun();
    if (pair == RoutePair.CAPABILITIES) {
      assertThat(publicRun.genericCapabilityTransactions()).isEmpty();
      assertThat(publicRun.publicCapabilityTransactions())
        .singleElement()
        .satisfies(snapshot -> {
          assertThat(snapshot.active()).isFalse();
          assertThat(snapshot.readOnly()).isFalse();
          assertThat(snapshot.name()).isNull();
        });
      assertThat(genericRun.genericCapabilityTransactions())
        .singleElement()
        .satisfies(snapshot -> {
          assertThat(snapshot.active()).isTrue();
          assertThat(snapshot.readOnly()).isTrue();
          assertThat(snapshot.name()).contains("AdminCategoryApplicationService.capabilities");
        });
      assertThat(genericRun.publicCapabilityTransactions()).isEmpty();
      return (
        "publicDirect=" +
        publicRun.publicCapabilityTransactions().get(0) +
        ", genericReadOnly=" +
        genericRun.genericCapabilityTransactions().get(0)
      );
    }

    assertThat(publicRun.catalogueLogs()).allSatisfy(log ->
      assertThat(log.transactionActive()).isTrue()
    );
    assertThat(genericRun.catalogueLogs()).allSatisfy(log ->
      assertThat(log.transactionActive()).isTrue()
    );
    assertThat(genericRun.genericLogs()).allSatisfy(log ->
      assertThat(log.transactionActive()).isTrue()
    );

    if (pair == RoutePair.CREATE) {
      assertThat(publicRun.genericCreateTransactions()).isEmpty();
      assertThat(publicRun.catalogueCreateTransactions()).hasSize(1);
      TransactionSnapshot publicCreate = publicRun.catalogueCreateTransactions().get(0);
      assertThat(publicCreate.active()).isTrue();
      assertThat(publicCreate.readOnly()).isFalse();
      assertThat(publicCreate.name()).contains("CatalogueCategoryApplicationService.create");

      assertThat(genericRun.genericCreateTransactions()).hasSize(1);
      assertThat(genericRun.catalogueCreateTransactions()).hasSize(1);
      TransactionSnapshot genericCreate = genericRun.genericCreateTransactions().get(0);
      TransactionSnapshot joinedCatalogueCreate = genericRun.catalogueCreateTransactions().get(0);
      assertThat(genericCreate.active()).isTrue();
      assertThat(genericCreate.readOnly()).isFalse();
      assertThat(genericCreate.name()).contains("AdminCategoryApplicationService.create");
      assertThat(joinedCatalogueCreate.active()).isTrue();
      assertThat(joinedCatalogueCreate.readOnly()).isFalse();
      assertThat(joinedCatalogueCreate.name()).isEqualTo(genericCreate.name());
      assertThat(joinedCatalogueCreate.name()).isNotEqualTo(publicCreate.name());
    }

    PairRuns missingCategory = support.missing().get(pair);
    if (missingCategory != null) {
      assertRolledBack(missingCategory);
    }
    PairRuns missingParent = support.missingParent().get(pair);
    if (missingParent != null) {
      assertRolledBack(missingParent);
    }
    PairRuns duplicate = support.duplicateSlug().get(pair);
    if (duplicate != null) {
      assertRolledBack(duplicate);
    }
    return (
      "catalogueLogTransactions=" +
      publicRun.catalogueLogs().stream().map(ObservedLog::transactionActive).toList() +
      ", genericLogTransactions=" +
      genericRun.genericLogs().stream().map(ObservedLog::transactionActive).toList() +
      (pair == RoutePair.CREATE
        ? ", publicCreate=" +
            publicRun.catalogueCreateTransactions().get(0) +
            ", genericOuter=" +
            genericRun.genericCreateTransactions().get(0)
        : ", rollbackProbes=verified")
    );
  }

  private static void assertDuplicateError(RoutePair pair, PairRuns probe) {
    Long id = pair == RoutePair.CREATE ? null : 2L;
    assertExactError(
      probe.publicRun(),
      409,
      "DUPLICATE_CATEGORY_SLUG",
      "category slug already exists",
      routePath(PUBLIC_BASE, pair, id)
    );
    assertExactError(
      probe.genericRun(),
      409,
      "DUPLICATE_CATEGORY_SLUG",
      "category slug already exists",
      routePath(GENERIC_BASE, pair, id)
    );
    assertRolledBack(probe);
  }

  private static void assertExactError(
    PairRun run,
    int status,
    String code,
    String message,
    String path
  ) {
    assertThat(run.status()).isEqualTo(status);
    assertThat(errorCode(run)).isEqualTo(code);
    assertThat(run.body().path("message").asText()).isEqualTo(message);
    assertThat(run.body().path("path").asText()).isEqualTo(path);
    assertThat(run.body().path("timestamp").isTextual()).isTrue();
    assertThat(run.body().path("fieldErrors").isArray()).isTrue();
    assertThat(run.body().path("fieldErrors").size()).isZero();
  }

  private static void assertRolledBack(PairRuns probe) {
    assertThat(probe.publicRun().categories()).isEqualTo(probe.publicRun().beforeCategories());
    assertThat(probe.publicRun().closures()).isEqualTo(probe.publicRun().beforeClosures());
    assertThat(probe.genericRun().categories()).isEqualTo(probe.genericRun().beforeCategories());
    assertThat(probe.genericRun().closures()).isEqualTo(probe.genericRun().beforeClosures());
    assertThat(probe.publicRun().categories()).isEqualTo(probe.genericRun().categories());
    assertThat(probe.publicRun().closures()).isEqualTo(probe.genericRun().closures());
  }

  private static String routePath(String base, RoutePair pair, Long id) {
    return switch (pair) {
      case CAPABILITIES -> base + "/capabilities";
      case CREATE -> base;
      case REPLACE, PATCH, DELETE -> base + "/" + id;
      case MOVE -> base + "/" + id + "/move";
      case REORDER -> base + "/" + id + "/reorder";
      case ACTIVATE -> base + "/" + id + "/activate";
      case DEACTIVATE -> base + "/" + id + "/deactivate";
    };
  }

  private static String errorObservation(PairRuns probe) {
    return (
      probe.publicRun().status() +
      ":" +
      errorCode(probe.publicRun()) +
      "/" +
      probe.genericRun().status() +
      ":" +
      errorCode(probe.genericRun()) +
      "; rows=" +
      probe.publicRun().categories().size() +
      "/" +
      probe.genericRun().categories().size()
    );
  }

  private static int bodySize(JsonNode body) {
    return body == null ? 0 : body.size();
  }

  private static boolean integralId(JsonNode body) {
    return body != null && body.has("id") && body.path("id").isIntegralNumber();
  }

  private static List<String> logNames(PairRun run) {
    List<String> names = new ArrayList<>();
    run.catalogueLogs().forEach(log -> names.add(log.loggerName() + ":" + log.message()));
    run.genericLogs().forEach(log -> names.add(log.loggerName() + ":" + log.message()));
    return List.copyOf(names);
  }

  private static LedgerEntry classify(RoutePair pair, Dimension dimension) {
    if (dimension == Dimension.MISSING_CATEGORY_OR_PARENT && pair == RoutePair.CAPABILITIES) {
      return entry(
        pair,
        dimension,
        Classification.NOT_APPLICABLE,
        "capabilities has no category or parent identifier"
      );
    }
    if (dimension == Dimension.LOCATION && pair != RoutePair.CREATE) {
      return entry(pair, dimension, Classification.NOT_APPLICABLE, "only create returns Location");
    }
    if (
      dimension == Dimension.DUPLICATE_SLUG &&
      !EnumSet.of(RoutePair.CREATE, RoutePair.REPLACE, RoutePair.PATCH).contains(pair)
    ) {
      return entry(
        pair,
        dimension,
        Classification.NOT_APPLICABLE,
        "operation does not write a slug"
      );
    }
    if (
      dimension == Dimension.MOVE_OR_PLACEMENT &&
      !EnumSet.of(
        RoutePair.CREATE,
        RoutePair.REPLACE,
        RoutePair.PATCH,
        RoutePair.MOVE,
        RoutePair.REORDER
      ).contains(pair)
    ) {
      return entry(
        pair,
        dimension,
        Classification.NOT_APPLICABLE,
        "operation has no parent or placement input"
      );
    }
    if (
      dimension == Dimension.LIFECYCLE &&
      !EnumSet.of(RoutePair.ACTIVATE, RoutePair.DEACTIVATE).contains(pair)
    ) {
      return entry(
        pair,
        dimension,
        Classification.NOT_APPLICABLE,
        "operation is not a lifecycle transition"
      );
    }
    if (
      dimension == Dimension.LOCKING &&
      !EnumSet.of(
        RoutePair.CREATE,
        RoutePair.REPLACE,
        RoutePair.PATCH,
        RoutePair.MOVE,
        RoutePair.REORDER
      ).contains(pair)
    ) {
      return entry(
        pair,
        dimension,
        Classification.NOT_APPLICABLE,
        "operation acquires no category slug, hierarchy, or rank lock"
      );
    }
    if (
      dimension == Dimension.TRANSPORT_DECODING &&
      EnumSet.of(
        RoutePair.CAPABILITIES,
        RoutePair.DELETE,
        RoutePair.ACTIVATE,
        RoutePair.DEACTIVATE
      ).contains(pair)
    ) {
      return entry(pair, dimension, Classification.NOT_APPLICABLE, "operation has no request body");
    }
    if (dimension == Dimension.NUMERIC_IDENTIFIERS && pair == RoutePair.CAPABILITIES) {
      return entry(
        pair,
        dimension,
        Classification.NOT_APPLICABLE,
        "capabilities has no category identifier"
      );
    }
    if (dimension == Dimension.AUTHORIZATION) {
      return entry(
        pair,
        dimension,
        Classification.FROZEN_ROUTE_SPECIFIC,
        "generic admin permission and catalogue scope/role aliases remain independent"
      );
    }
    if (dimension == Dimension.TRANSACTION_OR_ROLLBACK && pair == RoutePair.CAPABILITIES) {
      return entry(
        pair,
        dimension,
        Classification.FROZEN_ROUTE_SPECIFIC,
        "generic read-only transaction differs from public immutable metadata lookup"
      );
    }
    if (dimension == Dimension.STRUCTURED_LOGS && pair != RoutePair.CAPABILITIES) {
      return entry(
        pair,
        dimension,
        Classification.FROZEN_ROUTE_SPECIFIC,
        "generic route adds one bounded generic success event after the catalogue event"
      );
    }
    if (dimension == Dimension.LOCATION && pair == RoutePair.CREATE) {
      return entry(
        pair,
        dimension,
        Classification.FROZEN_ROUTE_SPECIFIC,
        "each create response retains its own route prefix"
      );
    }
    if (dimension == Dimension.BODY_SHAPE && pair == RoutePair.CAPABILITIES) {
      return entry(
        pair,
        dimension,
        Classification.FROZEN_ROUTE_SPECIFIC,
        "public four-field and generic fourteen-field DTOs retain common ranking facts"
      );
    }
    if (
      dimension == Dimension.STABLE_ERRORS &&
      EnumSet.of(
        RoutePair.CREATE,
        RoutePair.REPLACE,
        RoutePair.PATCH,
        RoutePair.DELETE,
        RoutePair.MOVE,
        RoutePair.REORDER
      ).contains(pair)
    ) {
      return entry(
        pair,
        dimension,
        Classification.FROZEN_ROUTE_SPECIFIC,
        "route-owned parent, delete, move, placement, and validation vocabularies are frozen"
      );
    }
    return entry(
      pair,
      dimension,
      Classification.EQUIVALENT,
      "PostgreSQL pair probe retains the same domain decision and catalogue-owned result"
    );
  }

  private static LedgerEntry entry(
    RoutePair pair,
    Dimension dimension,
    Classification classification,
    String reason
  ) {
    return new LedgerEntry(pair, dimension, classification, reason, "classification-only");
  }

  private static String ledgerSummary(List<LedgerEntry> ledger) {
    long unclassified = ledger
      .stream()
      .filter(entry -> entry.classification() == Classification.UNCLASSIFIED)
      .count();
    Map<RoutePair, Long> counts = new LinkedHashMap<>();
    for (RoutePair pair : RoutePair.values()) {
      counts.put(
        pair,
        ledger
          .stream()
          .filter(entry -> entry.pair() == pair)
          .count()
      );
    }
    return (
      "pairs=" +
      counts.size() +
      ";deleteGuards=2;patchCases=" +
      PATCH_COMPATIBILITY_CASES.size() +
      ";unclassified=" +
      unclassified +
      ";rows=" +
      ledger.size()
    );
  }

  private static List<RoutePair> repeatedPairs() {
    List<RoutePair> pairs = new ArrayList<>();
    for (RoutePair pair : RoutePair.values()) {
      for (int index = 0; index < Dimension.values().length; index++) {
        pairs.add(pair);
      }
    }
    return pairs;
  }

  private enum RoutePair {
    CAPABILITIES,
    CREATE,
    REPLACE,
    PATCH,
    DELETE,
    MOVE,
    REORDER,
    ACTIVATE,
    DEACTIVATE,
  }

  private enum Dimension {
    AUTHORIZATION,
    TRANSPORT_DECODING,
    VALIDATION_ORDER,
    STATUS,
    BODY_SHAPE,
    HEADERS,
    LOCATION,
    NUMERIC_IDENTIFIERS,
    STABLE_ERRORS,
    DUPLICATE_SLUG,
    MISSING_CATEGORY_OR_PARENT,
    MOVE_OR_PLACEMENT,
    LIFECYCLE,
    TRANSACTION_OR_ROLLBACK,
    LOCKING,
    RANKING,
    EVENTS,
    STRUCTURED_LOGS,
    PERSISTED_RESULT,
  }

  private enum Classification {
    EQUIVALENT,
    FROZEN_ROUTE_SPECIFIC,
    NOT_APPLICABLE,
    UNCLASSIFIED,
  }

  private record LedgerEntry(
    RoutePair pair,
    Dimension dimension,
    Classification classification,
    String reason,
    String observation
  ) {}

  private record PairRuns(PairRun publicRun, PairRun genericRun) {}

  private record PairRun(
    int status,
    JsonNode body,
    String location,
    String requestId,
    int requestBytes,
    List<StoredCategory> beforeCategories,
    List<StoredClosure> beforeClosures,
    List<StoredCategory> categories,
    List<StoredClosure> closures,
    List<String> catalogueLogTokens,
    List<String> genericLogTokens,
    List<ObservedLog> catalogueLogs,
    List<ObservedLog> genericLogs,
    List<String> slugLockCalls,
    List<String> closureCalls,
    List<String> rankCalls,
    long categoryApplicationEvents,
    List<TransactionSnapshot> catalogueCreateTransactions,
    List<TransactionSnapshot> genericCreateTransactions,
    List<TransactionSnapshot> genericCapabilityTransactions,
    List<TransactionSnapshot> publicCapabilityTransactions
  ) {}

  private record ObservedLog(
    String loggerName,
    String message,
    boolean transactionActive,
    Map<String, String> fields
  ) {}

  private record TransactionSnapshot(boolean active, boolean readOnly, String name) {}

  private record StoredCategory(
    Long id,
    Long parentId,
    Long sortRank,
    String name,
    String slug,
    boolean active
  ) {}

  private record StoredClosure(Long ancestorId, Long descendantId, int depth) {}

  private enum PatchSetup {
    NONE,
    ROOT,
    CHILD,
  }

  private record PatchCase(
    String name,
    PatchSetup setup,
    Long targetId,
    String body,
    int publicStatus,
    int genericStatus
  ) {}

  private record AuthorizationProbe(
    int publicUnauthenticated,
    int publicForbidden,
    int genericUnauthenticated,
    int genericForbidden
  ) {}

  private record LedgerSupport(
    Map<RoutePair, AuthorizationProbe> authorization,
    Map<RoutePair, PairRuns> missing,
    Map<RoutePair, PairRuns> missingParent,
    Map<RoutePair, PairRuns> duplicateSlug,
    PairRuns patchValidationOrder
  ) {}
}
