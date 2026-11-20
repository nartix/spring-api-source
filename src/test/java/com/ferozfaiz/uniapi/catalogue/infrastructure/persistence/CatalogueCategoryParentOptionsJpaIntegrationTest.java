package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryParentOptionsCursor;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOption;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOptionDisabledReason;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOptionSlice;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryParentOptionLookup;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryRepository;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
class CatalogueCategoryParentOptionsJpaIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  private static final String BASE =
    "/api/v1/admin/category-systems/catalogue/categories/parent-options";
  private static final String CATALOGUE_CURSOR =
    "eyJ2ZXJzaW9uIjoxLCJtb2RlIjoiU0VBUkNIIiwicSI6ImdwdSIsImluY2x1ZGVJbmFjdGl2ZSI6dHJ1ZSwiZXhjbHVkZVN1YnRyZWVJZCI6OSwicmFuayI6MiwiYWN0aXZlIjp0cnVlLCJuYW1lIjoiZ3JhcGhpY3MgY2FyZHMiLCJpZCI6NDR9";
  private static final String DESCRIPTOR_CURSOR =
    "MQlkZXNjcmlwdG9yLWEJd29qR3pNSXp2UE5TSWdkSjgxSDBfQzJoNVRDQVpjNFQ3MWR4cXNwRW50NAkyCXRydWUJWjNKaGNHaHBZM01nWTJGeVpITQk0NA";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired
  private MockMvc mvc;

  @Autowired
  private CatalogueCategoryApplicationService categoryService;

  @Autowired
  private CatalogueCategoryRepository categories;

  @Test
  void literalCatalogueCursorRemainsByteCompatible() {
    assertThat(
      AdminCatalogueCategoryParentOptionsCursor.encode(
        AdminCatalogueCategoryParentOptionsCursor.Mode.SEARCH,
        "GPU",
        true,
        9L,
        2,
        true,
        "Graphics Cards",
        44L
      )
    ).isEqualTo(CATALOGUE_CURSOR);
    assertThat(AdminCatalogueCategoryParentOptionsCursor.decode(CATALOGUE_CURSOR)).isEqualTo(
      new AdminCatalogueCategoryParentOptionsCursor(
        1,
        AdminCatalogueCategoryParentOptionsCursor.Mode.SEARCH,
        "gpu",
        true,
        9L,
        2,
        true,
        "graphics cards",
        44L
      )
    );
    assertThat(CATALOGUE_CURSOR).doesNotContain("+", "/", "=");
  }

  @Test
  void searchesByNameSlugNumericIdAndEscapesLiteralWildcardCharacters() {
    var exactSlug = createCategory("Graphics", "gpu", null);
    var slugPrefix = createCategory("Adapters", "gpu-adapters", null);
    var namePrefix = createCategory("GPU Brackets", "brackets", null);
    var contains = createCategory("External GPU Cases", "external-cases", null);
    createCategory("CPU Coolers", "cpu-coolers", null);
    createCategory("100%_\\ Deals", "symbol-deals", null);

    assertThat(parentOptions(lookup("gpu", null, 10)).data())
      .extracting(option -> option.category().id())
      .containsExactly(exactSlug.id(), slugPrefix.id(), namePrefix.id(), contains.id());

    assertThat(parentOptions(lookup(String.valueOf(namePrefix.id()), namePrefix.id(), 10)).data())
      .extracting(option -> option.category().id())
      .first()
      .isEqualTo(namePrefix.id());

    assertThat(parentOptions(lookup("100%_\\", null, 10)).data())
      .extracting(option -> option.category().slug())
      .containsExactly("symbol-deals");
  }

  @Test
  void cursorTransportBindsOnlyTheCatalogueOrdinaryContext() throws Exception {
    JsonNode payload = OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(CATALOGUE_CURSOR));
    List<String> fields = new ArrayList<>();
    payload.fieldNames().forEachRemaining(fields::add);
    assertThat(fields).containsExactly(
      "version",
      "mode",
      "q",
      "includeInactive",
      "excludeSubtreeId",
      "rank",
      "active",
      "name",
      "id"
    );
    assertThat(payload.path("version").intValue()).isEqualTo(1);
    assertThat(payload.path("q").textValue()).isEqualTo("gpu");
    assertThat(payload.has("systemKey")).isFalse();
    assertThat(payload.has("includeIds")).isFalse();
    assertThat(payload.has("limit")).isFalse();

    AdminCatalogueCategoryParentOptionsQuery compatible =
      AdminCatalogueCategoryParentOptionsQuery.from(
        "  gPu  ",
        50,
        CATALOGUE_CURSOR,
        List.of("2", "6"),
        "9",
        "true"
      );
    assertThat(compatible.limit()).isEqualTo(50);
    assertThat(compatible.includeIds()).containsExactly(2L, 6L);
    assertThat(compatible.cursor()).isEqualTo(
      new AdminCatalogueCategoryParentOptionsCursor(
        1,
        AdminCatalogueCategoryParentOptionsCursor.Mode.SEARCH,
        "gpu",
        true,
        9L,
        2,
        true,
        "graphics cards",
        44L
      )
    );

    AdminCatalogueCategoryParentOptionsQuery reorderedAndDeduplicated =
      AdminCatalogueCategoryParentOptionsQuery.from(
        "gpu",
        25,
        CATALOGUE_CURSOR,
        List.of("5", "2", "5"),
        "9",
        "true"
      );
    assertThat(reorderedAndDeduplicated.includeIds()).containsExactly(5L, 2L);

    assertCursorContextMismatch(CATALOGUE_CURSOR, "cpu", "true", "9");
    assertCursorContextMismatch(CATALOGUE_CURSOR, null, "true", "9");
    assertCursorContextMismatch(CATALOGUE_CURSOR, "gpu", "false", "9");
    assertCursorContextMismatch(CATALOGUE_CURSOR, "gpu", "true", "10");
  }

  @Test
  void blankLookupOrdersActiveCandidatesAndHydratesRootToOptionPaths() {
    var root = createCategory("Components", "components", null);
    var child = createCategory("Storage", "storage", root.id());
    var grandchild = createCategory("NVMe SSDs", "nvme-ssds", child.id());
    createCategory("Adapters", "adapters", null);

    var slice = parentOptions(lookup(null, null, 10));

    assertThat(slice.data())
      .extracting(option -> option.category().slug())
      .containsExactly("adapters", "components", "nvme-ssds", "storage");
    CatalogueCategoryParentOption option = slice
      .data()
      .stream()
      .filter(item -> item.category().id().equals(grandchild.id()))
      .findFirst()
      .orElseThrow();
    assertThat(option.depth()).isEqualTo(2);
    assertThat(option.path())
      .extracting(item -> item.category().slug(), item -> item.depth())
      .containsExactly(
        org.assertj.core.groups.Tuple.tuple("components", 0),
        org.assertj.core.groups.Tuple.tuple("storage", 1),
        org.assertj.core.groups.Tuple.tuple("nvme-ssds", 2)
      );
  }

  @Test
  void includedCandidatesArePrependedDeduplicatedAndInactiveSelectionsRemainVisible() {
    var inactive = createCategory("Legacy GPUs", "legacy-gpus", null);
    categoryService.deactivate(inactive.id());
    var active = createCategory("Modern GPUs", "modern-gpus", null);
    createCategory("GPU Accessories", "gpu-accessories", null);

    var slice = parentOptions(
      new CatalogueCategoryParentOptionLookup(
        "gpu",
        null,
        2,
        null,
        null,
        List.of(inactive.id(), inactive.id(), active.id()),
        false
      )
    );

    assertThat(slice.data()).hasSize(2);
    assertThat(slice.data())
      .extracting(option -> option.category().id())
      .containsExactly(inactive.id(), active.id());
    assertThat(slice.data().getFirst().selectable()).isFalse();
    assertThat(slice.data().getFirst().disabledReason()).isEqualTo(
      CatalogueCategoryParentOptionDisabledReason.INACTIVE
    );
    assertThat(slice.hasMore()).isFalse();
    assertThat(slice.nextCursor()).isNull();
  }

  @Test
  void excludeSubtreeOmitsSelfDescendantsAndIncludedIdsInsideSubtree() {
    var root = createCategory("Components", "components", null);
    var child = createCategory("Storage", "storage", root.id());
    var grandchild = createCategory("NVMe SSDs", "nvme-ssds", child.id());
    var unrelated = createCategory("Peripherals", "peripherals", null);

    var slice = parentOptions(
      new CatalogueCategoryParentOptionLookup(
        null,
        null,
        10,
        null,
        child.id(),
        List.of(grandchild.id()),
        true
      )
    );

    assertThat(slice.data())
      .extracting(option -> option.category().id())
      .containsExactly(root.id(), unrelated.id());
  }

  @Test
  void cursorContinuationReturnsNonOverlappingOrdinaryWindows() {
    createCategory("Alpha", "alpha", null);
    createCategory("Beta", "beta", null);
    createCategory("Gamma", "gamma", null);

    var first = parentOptions(lookup(null, null, 2));

    assertThat(first.data())
      .extracting(option -> option.category().slug())
      .containsExactly("alpha", "beta");
    assertThat(first.hasMore()).isTrue();
    assertThat(first.nextCursor()).isNotBlank();

    var second = parentOptions(
      new CatalogueCategoryParentOptionLookup(
        null,
        null,
        2,
        AdminCatalogueCategoryParentOptionsCursor.decode(first.nextCursor()),
        null,
        List.of(),
        true
      )
    );

    assertThat(second.data())
      .extracting(option -> option.category().slug())
      .containsExactly("gamma");
    assertThat(second.hasMore()).isFalse();
    assertThat(second.nextCursor()).isNull();
  }

  @Test
  void searchContinuationUsesTheCompleteEqualNameIdTieBreaker() {
    var firstCreated = createCategory("Twin GPU", "twin-gpu-a", null);
    var secondCreated = createCategory("Twin GPU", "twin-gpu-b", null);

    CatalogueCategoryParentOptionSlice first = categoryService.findParentOptions(
      AdminCatalogueCategoryParentOptionsQuery.from("twin", 1, null, List.of(), null, "true")
    );

    assertThat(first.data())
      .extracting(option -> option.category().id())
      .containsExactly(firstCreated.id());
    AdminCatalogueCategoryParentOptionsCursor position =
      AdminCatalogueCategoryParentOptionsCursor.decode(first.nextCursor());
    assertThat(position.rank()).isEqualTo(2);
    assertThat(position.name()).isEqualTo("twin gpu");
    assertThat(position.id()).isEqualTo(firstCreated.id());

    CatalogueCategoryParentOptionSlice second = categoryService.findParentOptions(
      AdminCatalogueCategoryParentOptionsQuery.from(
        "TWIN",
        1,
        first.nextCursor(),
        List.of(),
        null,
        "true"
      )
    );

    assertThat(second.data())
      .extracting(option -> option.category().id())
      .containsExactly(secondCreated.id());
    assertThat(second.hasMore()).isFalse();
    assertThat(second.nextCursor()).isNull();
  }

  @Test
  void selectedHydrationRepeatsAcrossPagesAndDoesNotBindMembershipOrLimit() {
    var selectedRoot = createCategory("Zeta Parent", "zeta-parent", null);
    var selectedChild = createCategory("Zeta Child", "zeta-child", selectedRoot.id());
    var alpha = createCategory("Alpha", "alpha", null);
    var beta = createCategory("Beta", "beta", null);
    var gamma = createCategory("Gamma", "gamma", null);

    CatalogueCategoryParentOptionSlice first = categoryService.findParentOptions(
      AdminCatalogueCategoryParentOptionsQuery.from(
        null,
        2,
        null,
        List.of("999999", selectedChild.id().toString()),
        null,
        "true"
      )
    );

    assertThat(first.data())
      .extracting(option -> option.category().id())
      .containsExactly(selectedChild.id(), alpha.id());
    assertThat(first.data().getFirst().path())
      .extracting(item -> item.category().id())
      .containsExactly(selectedRoot.id(), selectedChild.id());
    assertThat(first.nextCursor()).isNotBlank();

    CatalogueCategoryParentOptionSlice repeated = categoryService.findParentOptions(
      AdminCatalogueCategoryParentOptionsQuery.from(
        null,
        3,
        first.nextCursor(),
        List.of(selectedChild.id().toString()),
        null,
        "true"
      )
    );
    assertThat(repeated.data())
      .extracting(option -> option.category().id())
      .containsExactly(selectedChild.id(), beta.id(), gamma.id());

    CatalogueCategoryParentOptionSlice changedSelection = categoryService.findParentOptions(
      AdminCatalogueCategoryParentOptionsQuery.from(
        null,
        2,
        first.nextCursor(),
        List.of(selectedRoot.id().toString()),
        null,
        "true"
      )
    );
    assertThat(changedSelection.data())
      .extracting(option -> option.category().id())
      .containsExactly(selectedRoot.id(), beta.id());
  }

  @Test
  void ordinaryInactiveOptionsRemainVisibleButDisabledOnlyWhenRequested() {
    var active = createCategory("Alpha", "alpha", null);
    var inactive = createCategory("Beta", "beta", null);
    categoryService.deactivate(inactive.id());

    CatalogueCategoryParentOptionSlice withInactive = categoryService.findParentOptions(
      AdminCatalogueCategoryParentOptionsQuery.from(null, 10, null, List.of(), null, "true")
    );

    assertThat(withInactive.data())
      .extracting(option -> option.category().id())
      .containsExactly(active.id(), inactive.id());
    CatalogueCategoryParentOption inactiveOption = withInactive.data().getLast();
    assertThat(inactiveOption.selectable()).isFalse();
    assertThat(inactiveOption.disabledReason()).isEqualTo(
      CatalogueCategoryParentOptionDisabledReason.INACTIVE
    );

    CatalogueCategoryParentOptionSlice activeOnly = categoryService.findParentOptions(
      AdminCatalogueCategoryParentOptionsQuery.from(null, 10, null, List.of(), null, "false")
    );
    assertThat(activeOnly.data())
      .extracting(option -> option.category().id())
      .containsExactly(active.id());
  }

  @Test
  void fabricatedStalePositionReturnsANormalTerminalSlice() {
    createCategory("Alpha", "alpha", null);
    String staleCursor = AdminCatalogueCategoryParentOptionsCursor.encode(
      AdminCatalogueCategoryParentOptionsCursor.Mode.DEFAULT,
      null,
      true,
      null,
      null,
      false,
      "zzzzzz",
      Long.MAX_VALUE
    );

    CatalogueCategoryParentOptionSlice slice = categoryService.findParentOptions(
      AdminCatalogueCategoryParentOptionsQuery.from(null, 25, staleCursor, List.of(), null, "true")
    );

    assertThat(slice.data()).isEmpty();
    assertThat(slice.hasMore()).isFalse();
    assertThat(slice.nextCursor()).isNull();
  }

  @Test
  void exactPublicBoundsReachTheRealCatalogueProvider() throws Exception {
    mvc
      .perform(
        get(BASE)
          .param("q", "q".repeat(120))
          .param("limit", "50")
          .param("includeIds", Collections.nCopies(25, "999999").toArray(String[]::new))
          .with(readJwt())
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data").isArray())
      .andExpect(jsonPath("$.hasMore").value(false));

    mvc
      .perform(get(BASE).param("q", "q".repeat(121)).with(readJwt()))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mvc
      .perform(get(BASE).param("limit", "51").with(readJwt()))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mvc
      .perform(
        get(BASE)
          .param("includeIds", Collections.nCopies(26, "1").toArray(String[]::new))
          .with(readJwt())
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void realProviderInvalidCursorReturnsSanitizedHttpBodyAndAccessLog() throws Exception {
    for (String rawCursor : List.of(
      "catalogue-secret-cursor-Authorization-Bearer!",
      DESCRIPTOR_CURSOR
    )) {
      try (
        LogCapture logs = LogCapture.attach(
          "com.ferozfaiz.uniapi.platform.observability.HttpRequestLoggingFilter"
        )
      ) {
        String body = mvc
          .perform(get(BASE).param("cursor", rawCursor).with(readJwt()))
          .andExpect(status().isBadRequest())
          .andReturn()
          .getResponse()
          .getContentAsString();

        JsonNode error = OBJECT_MAPPER.readTree(body);
        List<String> errorFields = new ArrayList<>();
        error.fieldNames().forEachRemaining(errorFields::add);
        assertThat(errorFields).containsExactlyInAnyOrder(
          "code",
          "message",
          "path",
          "timestamp",
          "fieldErrors"
        );
        assertThat(error.path("code").textValue()).isEqualTo(
          "CATEGORY_PARENT_OPTIONS_CURSOR_INVALID"
        );
        assertThat(error.path("message").textValue()).isEqualTo("parent-options cursor is invalid");
        assertThat(error.path("path").textValue()).isEqualTo(BASE);
        assertThat(error.path("timestamp").isTextual()).isTrue();
        assertThat(error.path("timestamp").textValue()).isNotBlank();
        assertThat(error.path("fieldErrors").isArray()).isTrue();
        assertThat(error.path("fieldErrors")).isEmpty();
        assertThat(body)
          .doesNotContain(rawCursor)
          .doesNotContain("catalogue-secret-cursor")
          .doesNotContain("Authorization")
          .doesNotContain("Bearer")
          .doesNotContain("descriptor-a")
          .doesNotContain("wojGzMIzvPNSIgdJ81H0_C2h5TCAZc4T71dxqspEnt4")
          .doesNotContain("InvalidRequestException");

        ILoggingEvent accessEvent = logs.singleEvent();
        assertThat(accessEvent.getLevel()).isEqualTo(Level.WARN);
        assertThat(accessEvent.getFormattedMessage()).isEqualTo("http request completed");
        Map<String, String> accessFields = keyValues(accessEvent);
        assertThat(accessFields).containsOnlyKeys(
          "http.method",
          "http.path",
          "http.status_code",
          "http.duration_ms"
        );
        assertThat(accessFields)
          .containsEntry("http.method", "GET")
          .containsEntry("http.path", BASE)
          .containsEntry("http.status_code", "400");
        assertThat(Long.parseLong(accessFields.get("http.duration_ms"))).isBetween(
          0L,
          Duration.ofMinutes(1).toMillis()
        );
        assertThat(accessEvent.getMDCPropertyMap()).containsOnlyKeys("request.id");
        assertThat(accessEvent.getMDCPropertyMap().get("request.id")).matches(
          "[A-Za-z0-9._-]{1,64}"
        );
        assertThat(accessEvent.getArgumentArray()).isNullOrEmpty();
        assertThat(accessEvent.getThrowableProxy()).isNull();
        assertThat(accessEvent.getFormattedMessage() + " " + accessFields)
          .doesNotContain(rawCursor)
          .doesNotContain("catalogue-secret-cursor")
          .doesNotContain("Authorization")
          .doesNotContain("Bearer")
          .doesNotContain("descriptor-a")
          .doesNotContain("wojGzMIzvPNSIgdJ81H0_C2h5TCAZc4T71dxqspEnt4")
          .doesNotContain("InvalidRequestException");
      }
    }
  }

  @Test
  void parentOptionSearchIndexesAreAppliedAndRollbackPolicyIsDocumented() throws Exception {
    assertThat(
      jdbc.queryForObject("SELECT COUNT(*) FROM pg_extension WHERE extname = 'pg_trgm'", Long.class)
    ).isOne();
    assertThat(indexExists("idx_catalogue_category_lower_name_trgm")).isTrue();
    assertThat(indexExists("idx_catalogue_category_lower_slug_trgm")).isTrue();
    assertThat(indexExists("idx_catalogue_category_closure_descendant_depth")).isTrue();

    String migration = Files.readString(
      Path.of(
        "src/main/resources/db/changelog/modules/catalogue/changes/category/005-add-catalogue-category-parent-option-search-indexes.sql"
      )
    );
    assertThat(migration).contains(
      "DROP INDEX IF EXISTS idx_catalogue_category_closure_descendant_depth",
      "DROP INDEX IF EXISTS idx_catalogue_category_lower_slug_trgm",
      "DROP INDEX IF EXISTS idx_catalogue_category_lower_name_trgm"
    );
    assertThat(migration).doesNotContain("DROP EXTENSION");
  }

  @Test
  void largeCatalogueParentOptionsP95Timing() {
    Assumptions.assumeTrue(
      Boolean.parseBoolean(System.getProperty("catalogueParentOptionsLargeProfile", "false")),
      "Enable with -PcatalogueParentOptionsLargeProfile=true"
    );
    jdbc.execute(
      """
      INSERT INTO catalogue_category (name, slug, parent_id, sort_rank, is_active)
      SELECT
        'Profile Category ' || series,
        'profile-category-' || series,
        NULL,
        series * 1000000000::BIGINT,
        series % 10 <> 0
      FROM generate_series(1, 100000) AS series
      """
    );
    jdbc.execute(
      """
      INSERT INTO catalogue_category_closure (ancestor_id, descendant_id, depth)
      SELECT id, id, 0
      FROM catalogue_category
      WHERE slug LIKE 'profile-category-%'
      """
    );

    parentOptions(lookup("profile", null, 25));
    List<Long> timings = new ArrayList<>();
    for (int index = 0; index < 20; index++) {
      long started = System.nanoTime();
      parentOptions(lookup(index % 2 == 0 ? "profile" : null, null, 25));
      timings.add(System.nanoTime() - started);
    }
    timings.sort(Long::compareTo);
    long p95Nanos = timings.get(18);

    System.out.println(
      "catalogue-parent-options-large-profile-p95-ms=" + Duration.ofNanos(p95Nanos).toMillis()
    );
    assertThat(Duration.ofNanos(p95Nanos)).isLessThanOrEqualTo(Duration.ofSeconds(1));
  }

  private CatalogueCategory createCategory(String name, String slug, Long parentId) {
    return categoryService.create(
      new UpsertCatalogueCategoryCommand(null, parentId, name, slug, true)
    );
  }

  private CatalogueCategoryParentOptionSlice parentOptions(
    CatalogueCategoryParentOptionLookup lookup
  ) {
    return categories.findParentOptions(lookup);
  }

  private static CatalogueCategoryParentOptionLookup lookup(
    String q,
    Long numericQuery,
    int limit
  ) {
    return new CatalogueCategoryParentOptionLookup(
      q,
      numericQuery,
      limit,
      null,
      null,
      List.of(),
      true
    );
  }

  private static void assertCursorContextMismatch(
    String cursor,
    String q,
    String includeInactive,
    String excludeSubtreeId
  ) {
    assertThatThrownBy(() ->
      AdminCatalogueCategoryParentOptionsQuery.from(
        q,
        25,
        cursor,
        List.of(),
        excludeSubtreeId,
        includeInactive
      )
    )
      .isInstanceOf(InvalidRequestException.class)
      .extracting("messageKey")
      .isEqualTo("catalogue.api.parent-options.cursor.context");
  }

  private static RequestPostProcessor readJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("admin:catalogue-categories:read"));
  }

  private static Map<String, String> keyValues(ILoggingEvent event) {
    return event
      .getKeyValuePairs()
      .stream()
      .collect(
        java.util.stream.Collectors.toMap(
          pair -> pair.key,
          pair -> String.valueOf(pair.value),
          (left, right) -> right
        )
      );
  }

  private boolean indexExists(String indexName) {
    Long count = jdbc.queryForObject(
      """
      SELECT COUNT(*)
      FROM pg_indexes
      WHERE schemaname = current_schema()
        AND indexname = ?
      """,
      Long.class,
      indexName
    );
    return count != null && count == 1;
  }

  private static final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level originalLevel;
    private final ListAppender<ILoggingEvent> appender;

    private LogCapture(Logger logger, Level originalLevel, ListAppender<ILoggingEvent> appender) {
      this.logger = logger;
      this.originalLevel = originalLevel;
      this.appender = appender;
    }

    static LogCapture attach(String loggerName) {
      Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
      Level originalLevel = logger.getLevel();
      logger.setLevel(Level.INFO);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
      return new LogCapture(logger, originalLevel, appender);
    }

    ILoggingEvent singleEvent() {
      assertThat(appender.list).hasSize(1);
      return appender.list.getFirst();
    }

    @Override
    public void close() {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
    }
  }
}
