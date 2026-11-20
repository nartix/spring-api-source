package com.ferozfaiz.uniapi.catalogue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ferozfaiz.uniapi.catalogue.application.command.ReorderCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.port.CatalogueSlugWriteLock;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryFilters;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryParentOptionsCursor;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryTreeQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminListCatalogueCategoriesQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.CategoryParentScope;
import com.ferozfaiz.uniapi.catalogue.application.validation.CatalogueCategoryCommandValidator;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryBreadcrumbItem;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryNode;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOption;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOptionSlice;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryTreeNode;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryParentOptionLookup;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueCategoryRepository;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.policy.AdminCategoryTreePolicy;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.DisabledRankingRequestPolicy;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.FallbackOrderDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.PlacementMode;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.RankingCapabilityDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseRankTableDescriptor;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class CatalogueCategoryApplicationServiceTest {

  @Mock
  private CatalogueCategoryRepository categories;

  @Mock
  private CatalogueSlugWriteLock slugWriteLock;

  @Mock
  private CategorySystemRegistry categorySystems;

  @Test
  void parentOptionsQueryNormalizesRequestPolicyFields() {
    AdminCatalogueCategoryParentOptionsQuery query = AdminCatalogueCategoryParentOptionsQuery.from(
      " 123 ",
      null,
      null,
      List.of("42", "42", "7"),
      null,
      null
    );

    assertThat(query.q()).isEqualTo("123");
    assertThat(query.numericQuery()).isEqualTo(123L);
    assertThat(query.limit()).isEqualTo(25);
    assertThat(query.includeIds()).containsExactly(42L, 7L);
    assertThat(query.includeInactive()).isTrue();
  }

  @Test
  void parentOptionsQueryFreezesSearchLimitAndRawIncludeIdBoundaries() {
    AdminCatalogueCategoryParentOptionsQuery maximum =
      AdminCatalogueCategoryParentOptionsQuery.from(
        "q".repeat(120),
        50,
        null,
        Collections.nCopies(25, "7"),
        null,
        "false"
      );

    assertThat(maximum.q()).hasSize(120);
    assertThat(maximum.limit()).isEqualTo(50);
    assertThat(maximum.includeIds()).containsExactly(7L);
    assertThat(maximum.includeInactive()).isFalse();

    assertThatThrownBy(() ->
      AdminCatalogueCategoryParentOptionsQuery.from(
        " " + "q".repeat(120),
        25,
        null,
        List.of(),
        null,
        null
      )
    )
      .isInstanceOf(InvalidRequestException.class)
      .hasMessage("q must be at most 120 characters");

    assertThatThrownBy(() ->
      AdminCatalogueCategoryParentOptionsQuery.from(null, 0, null, List.of(), null, null)
    )
      .isInstanceOf(InvalidRequestException.class)
      .hasMessage("limit must be at least 1");

    assertThatThrownBy(() ->
      AdminCatalogueCategoryParentOptionsQuery.from(null, 51, null, List.of(), null, null)
    )
      .isInstanceOf(InvalidRequestException.class)
      .hasMessage("limit must be at most 50");

    assertThatThrownBy(() ->
      AdminCatalogueCategoryParentOptionsQuery.from(
        null,
        25,
        null,
        Collections.nCopies(26, "7"),
        null,
        null
      )
    )
      .isInstanceOf(InvalidRequestException.class)
      .hasMessage("includeIds must contain at most 25 values");
  }

  @Test
  void parentOptionsValidatesExcludedCategoryBeforeRepositoryLookup() {
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );
    AdminCatalogueCategoryParentOptionsQuery query = AdminCatalogueCategoryParentOptionsQuery.from(
      null,
      null,
      null,
      List.of(),
      "404",
      null
    );

    assertThatThrownBy(() -> service.findParentOptions(query)).isInstanceOf(
      CatalogueNotFoundException.class
    );

    verify(categories).findById(404L);
    verify(categories, never()).findParentOptions(any());
  }

  @Test
  void parentOptionsDelegatesNormalizedLookupAndDisambiguatesDuplicateLabels() {
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );
    when(categories.findById(10L)).thenReturn(
      Optional.of(category(10L, null, "Components", "components"))
    );
    when(categories.findParentOptions(any(CatalogueCategoryParentOptionLookup.class))).thenReturn(
      new CatalogueCategoryParentOptionSlice(
        List.of(
          parentOption(
            20L,
            10L,
            "Adapters",
            "audio-adapters",
            breadcrumbItem(10L, null, "Components", "components", 0),
            breadcrumbItem(20L, 10L, "Adapters", "audio-adapters", 1)
          ),
          parentOption(
            30L,
            10L,
            "Adapters",
            "network-adapters",
            breadcrumbItem(10L, null, "Components", "components", 0),
            breadcrumbItem(30L, 10L, "Adapters", "network-adapters", 1)
          )
        ),
        false,
        null
      )
    );
    AdminCatalogueCategoryParentOptionsQuery query = AdminCatalogueCategoryParentOptionsQuery.from(
      " gpu ",
      2,
      null,
      List.of("20", "20", "30"),
      "10",
      "false"
    );

    CatalogueCategoryParentOptionSlice result = service.findParentOptions(query);

    assertThat(result.data())
      .extracting(CatalogueCategoryParentOption::label)
      .containsExactly("Components / Adapters", "Components / Adapters");
    verify(categories).findParentOptions(
      argThat(
        lookup ->
          "gpu".equals(lookup.q()) &&
          lookup.limit() == 2 &&
          lookup.includeIds().equals(List.of(20L, 30L)) &&
          Long.valueOf(10L).equals(lookup.excludeSubtreeId()) &&
          !lookup.includeInactive()
      )
    );
  }

  @Test
  void parentOptionsCursorBindsOnlySearchInactiveAndExcludedSubtreeContext() {
    String cursor = AdminCatalogueCategoryParentOptionsCursor.encode(
      AdminCatalogueCategoryParentOptionsCursor.Mode.SEARCH,
      "gpu",
      false,
      9L,
      3,
      true,
      "graphics cards",
      1L
    );

    AdminCatalogueCategoryParentOptionsQuery compatible =
      AdminCatalogueCategoryParentOptionsQuery.from(
        "  GPU  ",
        50,
        cursor,
        List.of("20", "30"),
        "9",
        "false"
      );

    assertThat(compatible.limit()).isEqualTo(50);
    assertThat(compatible.includeIds()).containsExactly(20L, 30L);
    assertThat(compatible.cursor()).isNotNull();

    assertCursorContextMismatch(() ->
      AdminCatalogueCategoryParentOptionsQuery.from(
        "cpu",
        50,
        cursor,
        List.of("20", "30"),
        "9",
        "false"
      )
    );
    assertCursorContextMismatch(() ->
      AdminCatalogueCategoryParentOptionsQuery.from(
        "gpu",
        50,
        cursor,
        List.of("20", "30"),
        "9",
        "true"
      )
    );
    assertCursorContextMismatch(() ->
      AdminCatalogueCategoryParentOptionsQuery.from(
        "gpu",
        50,
        cursor,
        List.of("20", "30"),
        "10",
        "false"
      )
    );

    String unsupportedVersion = Base64.getUrlEncoder().withoutPadding().encodeToString(
      """
      {"version":2,"mode":"DEFAULT","q":null,"includeInactive":true,"excludeSubtreeId":null,"rank":null,"active":true,"name":"components","id":1}
      """.getBytes(StandardCharsets.UTF_8)
    );

    assertThatThrownBy(() ->
      AdminCatalogueCategoryParentOptionsQuery.from(
        null,
        25,
        unsupportedVersion,
        List.of(),
        null,
        null
      )
    )
      .isInstanceOf(InvalidRequestException.class)
      .hasMessage("cursor is invalid");
  }

  @Test
  void findAllPassesParentScopeToRepositoryForRankedSiblingViews() {
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );
    PageCriteria page = new PageCriteria(0, 20);
    SortCriteria sort = SortCriteria.of("sortRank", SortDirection.ASC);
    CategoryParentScope parentScope = CategoryParentScope.children(10L);
    when(categories.findById(10L)).thenReturn(
      Optional.of(category(10L, null, "Components", "components"))
    );

    service.findAll(
      new AdminListCatalogueCategoriesQuery(
        parentScope,
        AdminCatalogueCategoryFilters.none(),
        page,
        sort
      )
    );

    verify(categories).findAll(parentScope, AdminCatalogueCategoryFilters.none(), page, sort);
  }

  @Test
  void findAllNodesValidatesPositiveParentBeforeRepositoryWork() {
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );
    PageCriteria page = new PageCriteria(0, 20);
    SortCriteria sort = SortCriteria.of("sortRank", SortDirection.ASC);

    assertThatThrownBy(() ->
      service.findAllNodes(
        new AdminListCatalogueCategoriesQuery(
          CategoryParentScope.children(404L),
          AdminCatalogueCategoryFilters.none(),
          page,
          sort
        )
      )
    ).isInstanceOf(CatalogueNotFoundException.class);

    verify(categories, never()).findAllNodes(any(), any(), any(), any());
  }

  @Test
  void findAllNodesDoesNotValidateParentForAllOrRoots() {
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );
    PageCriteria page = new PageCriteria(0, 20);
    SortCriteria sort = SortCriteria.of("sortRank", SortDirection.ASC);
    PageResult<CatalogueCategoryNode> emptyPage = new PageResult<>(
      List.of(),
      0,
      20,
      0,
      0,
      true,
      true,
      sort
    );
    when(
      categories.findAllNodes(
        CategoryParentScope.all(),
        AdminCatalogueCategoryFilters.none(),
        page,
        sort
      )
    ).thenReturn(emptyPage);
    when(
      categories.findAllNodes(
        CategoryParentScope.roots(),
        AdminCatalogueCategoryFilters.none(),
        page,
        sort
      )
    ).thenReturn(emptyPage);

    service.findAllNodes(
      new AdminListCatalogueCategoriesQuery(
        CategoryParentScope.all(),
        AdminCatalogueCategoryFilters.none(),
        page,
        sort
      )
    );
    service.findAllNodes(
      new AdminListCatalogueCategoriesQuery(
        CategoryParentScope.roots(),
        AdminCatalogueCategoryFilters.none(),
        page,
        sort
      )
    );

    verify(categories, never()).findById(any());
  }

  @Test
  void findAllNodesReturnsEmptyPageForExistingParentWithNoChildren() {
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );
    PageCriteria page = new PageCriteria(0, 20);
    SortCriteria sort = SortCriteria.of("sortRank", SortDirection.ASC);
    PageResult<CatalogueCategoryNode> emptyPage = new PageResult<>(
      List.of(),
      0,
      20,
      0,
      0,
      true,
      true,
      sort
    );
    CategoryParentScope parentScope = CategoryParentScope.children(10L);
    when(categories.findById(10L)).thenReturn(
      Optional.of(category(10L, null, "Components", "components"))
    );
    when(
      categories.findAllNodes(parentScope, AdminCatalogueCategoryFilters.none(), page, sort)
    ).thenReturn(emptyPage);

    PageResult<CatalogueCategoryNode> result = service.findAllNodes(
      new AdminListCatalogueCategoriesQuery(
        parentScope,
        AdminCatalogueCategoryFilters.none(),
        page,
        sort
      )
    );

    assertThat(result.content()).isEmpty();
  }

  @Test
  void findTreeAllowsGenericPolicyPageNumbersWhenTreeFitsRequestedSize() {
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );
    var query = new AdminCatalogueCategoryTreeQuery(
      new PageCriteria(1, 1000),
      SortCriteria.of("sortRank", SortDirection.ASC)
    );
    var page = new PageResult<CatalogueCategoryTreeNode>(
      List.of(),
      1,
      1000,
      0,
      0,
      false,
      true,
      query.sort()
    );
    when(categories.countTreeRows()).thenReturn(0L);
    when(categories.findTree(query.page(), query.sort())).thenReturn(page);

    assertThat(service.findTree(query)).isSameAs(page);

    verify(categories).countTreeRows();
    verify(categories).findTree(query.page(), query.sort());
  }

  @Test
  void findTreeRejectsTooLargeTotalBeforeFetchingTreeRows() {
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );
    when(categories.countTreeRows()).thenReturn(3L);

    assertThatThrownBy(() ->
      service.findTree(
        new AdminCatalogueCategoryTreeQuery(
          new PageCriteria(0, 2),
          SortCriteria.of("sortRank", SortDirection.ASC)
        )
      )
    )
      .isInstanceOf(AdminCategoryException.class)
      .satisfies(exception -> {
        AdminCategoryException tooLarge = (AdminCategoryException) exception;
        assertThat(tooLarge.code()).isEqualTo("CATEGORY_TREE_TOO_LARGE");
        assertThat(tooLarge.messageKey()).isEqualTo("category.application.tree.too-large");
      });

    verify(categories).countTreeRows();
    verify(categories, never()).findTree(any(), any());
  }

  @Test
  void findTreeFetchesRowsWhenTotalFitsRequestedSize() {
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );
    var query = new AdminCatalogueCategoryTreeQuery(
      new PageCriteria(0, 2),
      SortCriteria.of("sortRank", SortDirection.ASC)
    );
    var page = new PageResult<CatalogueCategoryTreeNode>(
      List.of(),
      0,
      2,
      0,
      0,
      true,
      true,
      query.sort()
    );
    when(categories.countTreeRows()).thenReturn(2L);
    when(categories.findTree(query.page(), query.sort())).thenReturn(page);

    assertThat(service.findTree(query)).isSameAs(page);
  }

  @Test
  void getNodeReturnsDepthBearingCategoryRead() {
    CatalogueCategoryNode node = new CatalogueCategoryNode(
      category(10L, 20L, "Storage", "storage"),
      1,
      true
    );
    when(categories.findNodeById(10L)).thenReturn(Optional.of(node));
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    assertThat(service.getNode(10L)).isEqualTo(node);
  }

  @Test
  void findBreadcrumbReturnsRepositoryBreadcrumbUnchanged() {
    List<CatalogueCategoryBreadcrumbItem> breadcrumb = List.of(
      new CatalogueCategoryBreadcrumbItem(category(10L, null, "Components", "components"), 0),
      new CatalogueCategoryBreadcrumbItem(category(20L, 10L, "Storage", "storage"), 1)
    );
    when(categories.findBreadcrumbById(20L)).thenReturn(breadcrumb);
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    assertThat(service.findBreadcrumb(20L)).isSameAs(breadcrumb);
  }

  @Test
  void findBreadcrumbMapsEmptyRepositoryResultToNotFound() {
    when(categories.findBreadcrumbById(404L)).thenReturn(List.of());
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    assertThatThrownBy(() -> service.findBreadcrumb(404L))
      .isInstanceOf(CatalogueNotFoundException.class)
      .hasMessage("category was not found");
  }

  @Test
  void rejectsDuplicateCategorySlugBeforeSave() {
    when(categories.existsSlug("storage", null)).thenReturn(true);
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    assertThatThrownBy(() ->
      service.create(new UpsertCatalogueCategoryCommand(null, null, "Storage", "storage", true))
    ).isInstanceOf(DuplicateCatalogueSlugException.class);
  }

  @Test
  void rejectsInvalidCategorySlugBeforeSave() {
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    assertThatThrownBy(() ->
      service.create(new UpsertCatalogueCategoryCommand(null, null, "Storage", "Bad Slug", true))
    )
      .isInstanceOf(CatalogueException.class)
      .hasMessage("slug may contain lowercase letters, numbers, and hyphens");

    verify(categories, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void createPassesPlacementTargetToRepository() {
    SparseOrderTarget target = SparseOrderTarget.from(20L, null, null);
    ListAppender<ILoggingEvent> logs = captureLogs(CatalogueCategoryApplicationService.class);
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );
    when(categories.save(any(CatalogueCategory.class), eq(target))).thenReturn(
      category(10L, null, 1_500_000_000L, "Storage", "storage")
    );

    CatalogueCategory created = service.create(
      new UpsertCatalogueCategoryCommand(null, null, "Storage", "storage", true, target)
    );

    assertThat(created.sortRank()).isEqualTo(1_500_000_000L);
    verify(categories).save(
      argThat(category -> category.sortRank() == null && category.slug().equals("storage")),
      eq(target)
    );
    assertStructuredLog(
      logs.list.getLast(),
      "catalogue category created",
      Map.of(
        "catalogue.event",
        "category.created",
        "category.system",
        "catalogue",
        "category.operation",
        "create",
        "category.id",
        "10",
        "category.parent_id",
        "null",
        "category.ranking.result",
        "assigned"
      )
    );
  }

  @Test
  void updateLogFreezesItsInventoryAfterRepositoryReturnAndBeforeOuterCommit() {
    CatalogueCategory existing = category(10L, null, "Storage", "storage");
    CatalogueCategory updated = category(10L, null, "Updated storage", "updated-storage");
    List<String> timeline = new ArrayList<>();
    when(categories.findById(10L)).thenReturn(Optional.of(existing));
    when(categories.save(any(CatalogueCategory.class), any(SparseOrderTarget.class))).thenAnswer(
      invocation -> {
        timeline.add("catalogue-repository.update.return");
        return updated;
      }
    );
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );
    TransactionLedger transactionManager = new TransactionLedger(timeline);
    TransactionAwareListAppender logs = captureLogs(
      CatalogueCategoryApplicationService.class,
      timeline,
      "catalogue.update.log"
    );
    MDC.put("request.id", "catalogue-request-123");

    try {
      new TransactionTemplate(transactionManager).executeWithoutResult(ignored ->
        service.update(
          new UpsertCatalogueCategoryCommand(10L, null, "Updated storage", "updated-storage", true)
        )
      );

      assertThat(timeline).containsExactly(
        "catalogue-repository.update.return",
        "catalogue.update.log",
        "outer.commit"
      );
      assertThat(transactionManager.commits).isEqualTo(1);
      assertThat(transactionManager.rollbacks).isZero();
      assertThat(logs.transactionActive).containsExactly(true);
      assertThat(logs.list).hasSize(1);
      ILoggingEvent event = logs.list.getFirst();
      assertStructuredLog(
        event,
        "catalogue category updated",
        Map.of("catalogue.event", "category.updated", "category.id", "10")
      );
      assertThat(event.getMDCPropertyMap()).containsExactly(
        Map.entry("request.id", "catalogue-request-123")
      );
      assertThat(event.getMDCPropertyMap().get("request.id")).matches("[A-Za-z0-9._-]{1,128}");
      assertThat(event.getThrowableProxy()).isNull();
      assertThat(event.getArgumentArray()).isNullOrEmpty();
    } finally {
      MDC.clear();
      detachLogs(CatalogueCategoryApplicationService.class, logs);
    }
  }

  @Test
  void repositoryFailureRollsBackWithoutCatalogueSuccessEvent() {
    CatalogueCategory existing = category(10L, null, "Storage", "storage");
    List<String> timeline = new ArrayList<>();
    when(categories.findById(10L)).thenReturn(Optional.of(existing));
    when(categories.save(any(CatalogueCategory.class), any(SparseOrderTarget.class))).thenAnswer(
      invocation -> {
        timeline.add("catalogue-repository.update.failed");
        throw new IllegalStateException("probe raw cause message");
      }
    );
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );
    TransactionLedger transactionManager = new TransactionLedger(timeline);
    TransactionAwareListAppender logs = captureLogs(
      CatalogueCategoryApplicationService.class,
      timeline,
      "catalogue.update.log"
    );

    try {
      assertThatThrownBy(() ->
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored ->
          service.update(
            new UpsertCatalogueCategoryCommand(
              10L,
              null,
              "Updated storage",
              "updated-storage",
              true
            )
          )
        )
      )
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("probe raw cause message");

      assertThat(logs.list).isEmpty();
      assertThat(timeline).containsExactly("catalogue-repository.update.failed", "outer.rollback");
      assertThat(transactionManager.commits).isZero();
      assertThat(transactionManager.rollbacks).isEqualTo(1);
    } finally {
      detachLogs(CatalogueCategoryApplicationService.class, logs);
    }
  }

  @Test
  void preventsMovingCategoryIntoOwnDescendant() {
    when(categories.findById(10L)).thenReturn(
      Optional.of(category(10L, null, "Storage", "storage"))
    );
    when(categories.findById(20L)).thenReturn(
      Optional.of(category(20L, 10L, "NVMe SSDs", "nvme-ssds"))
    );
    when(categories.isDescendantOf(20L, 10L)).thenReturn(true);
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    assertThatThrownBy(() -> service.move(10L, 20L))
      .isInstanceOf(CatalogueConflictException.class)
      .hasMessage("category move is invalid");
    verify(categories, never()).move(10L, 20L);
  }

  @Test
  void rejectsMissingParentBeforeCreateOrMoveMutation() {
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    assertThatThrownBy(() ->
      service.create(new UpsertCatalogueCategoryCommand(10L, 99L, "Storage", "storage", true))
    ).isInstanceOf(CatalogueNotFoundException.class);

    verify(categories, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void movesCategoryToValidParentAndRoot() {
    when(categories.findById(10L)).thenReturn(
      Optional.of(category(10L, null, "Storage", "storage")),
      Optional.of(category(10L, 20L, "Storage", "storage"))
    );
    when(categories.findById(20L)).thenReturn(
      Optional.of(category(20L, null, "Components", "components"))
    );
    when(categories.isDescendantOf(20L, 10L)).thenReturn(false);
    ListAppender<ILoggingEvent> logs = captureLogs(CatalogueCategoryApplicationService.class);
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    service.move(10L, 20L);
    service.move(10L, null);

    verify(categories).move(10L, 20L, SparseOrderTarget.append());
    verify(categories).move(10L, null, SparseOrderTarget.append());
    assertThat(logs.list).hasSize(2);
    assertStructuredLog(
      logs.list.get(0),
      "catalogue category moved",
      rankedMutationFields("category.moved", "move", "20")
    );
    assertStructuredLog(
      logs.list.get(1),
      "catalogue category moved",
      rankedMutationFields("category.moved", "move", "null")
    );
  }

  @Test
  void reorderUsesExistingParentWhenRequestOmitsParentAndPassesTarget() {
    SparseOrderTarget target = SparseOrderTarget.from(null, 30L, null);
    CatalogueCategory existing = category(10L, 20L, 1_000_000_000L, "Storage", "storage");
    CatalogueCategory reordered = category(10L, 20L, 2_000_000_000L, "Storage", "storage");
    when(categories.findById(10L)).thenReturn(Optional.of(existing), Optional.of(reordered));
    when(categories.findById(20L)).thenReturn(
      Optional.of(category(20L, null, 1_000_000_000L, "Components", "components"))
    );
    ListAppender<ILoggingEvent> logs = captureLogs(CatalogueCategoryApplicationService.class);
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    CatalogueCategory result = service.reorder(
      10L,
      new ReorderCatalogueCategoryCommand(null, target)
    );

    assertThat(result.sortRank()).isEqualTo(2_000_000_000L);
    verify(categories).move(10L, 20L, target);
    assertStructuredLog(
      logs.list.getLast(),
      "catalogue category reordered",
      rankedMutationFields("category.reordered", "reorder", "20")
    );
  }

  @Test
  void rejectsPlacementModeNotAdvertisedBeforeRepositoryMutation() {
    SparseOrderTarget target = SparseOrderTarget.from(20L, null, null);
    when(categories.findById(10L)).thenReturn(
      Optional.of(category(10L, null, 1_000_000_000L, "Storage", "storage"))
    );
    CatalogueCategoryApplicationService service = serviceWith(
      rankedDescriptor(List.of(PlacementMode.APPEND), true)
    );

    assertThatThrownBy(() -> service.move(10L, null, target))
      .isInstanceOf(InvalidRequestException.class)
      .satisfies(exception ->
        assertThat(((InvalidRequestException) exception).messageKey()).isEqualTo(
          "catalogue.api.category.placement.unsupported"
        )
      );

    verify(categories, never()).move(eq(10L), any(), any(SparseOrderTarget.class));
  }

  @Test
  void hiddenRankAcceptsConfiguredExplicitPlacementMode() {
    SparseOrderTarget target = SparseOrderTarget.from(20L, null, null);
    when(categories.findById(10L)).thenReturn(
      Optional.of(category(10L, null, 1_000_000_000L, "Storage", "storage"))
    );
    CatalogueCategoryApplicationService service = serviceWith(
      rankedDescriptor(List.of(PlacementMode.APPEND, PlacementMode.BEFORE_ID), false)
    );

    service.move(10L, null, target);

    verify(categories).move(10L, null, target);
  }

  @Test
  void unrankedCatalogueRejectsEveryExplicitPlacementBranchBeforeMutation() {
    when(categories.findById(10L)).thenReturn(
      Optional.of(category(10L, null, 1_000_000_000L, "Storage", "storage"))
    );
    CatalogueCategoryApplicationService service = serviceWith(unrankedDescriptor());
    Map<SparseOrderTarget, String> rejectedTargets = Map.of(
      SparseOrderTarget.from(20L, null, null),
      "catalogue.api.category.ranking.disabled.before-id",
      SparseOrderTarget.from(null, 20L, null),
      "catalogue.api.category.ranking.disabled.after-id",
      SparseOrderTarget.from(null, null, 0),
      "catalogue.api.category.ranking.disabled.target-index"
    );

    rejectedTargets.forEach((target, messageKey) ->
      assertThatThrownBy(() -> service.move(10L, null, target))
        .isInstanceOf(InvalidRequestException.class)
        .extracting("messageKey")
        .isEqualTo(messageKey)
    );

    verify(categories, never()).move(eq(10L), any(), any(SparseOrderTarget.class));
  }

  @Test
  void rejectsSelfMovesAsConflict() {
    when(categories.findById(10L)).thenReturn(
      Optional.of(category(10L, null, "Storage", "storage"))
    );
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    assertThatThrownBy(() -> service.move(10L, 10L)).isInstanceOf(CatalogueConflictException.class);

    verify(categories, never()).move(10L, 10L);
  }

  @Test
  void rejectsDeleteWhenChildrenOrAssignmentsExist() {
    when(categories.findById(10L)).thenReturn(
      Optional.of(category(10L, null, "Storage", "storage"))
    );
    when(categories.hasChildren(10L)).thenReturn(true);
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    assertThatThrownBy(() -> service.delete(10L)).isInstanceOf(CatalogueConflictException.class);

    verify(categories, never()).deleteById(10L);
  }

  @Test
  void rejectsDeleteWhenAssignedProductsExistAfterCheckingChildren() {
    when(categories.findById(10L)).thenReturn(
      Optional.of(category(10L, null, "Storage", "storage"))
    );
    when(categories.hasChildren(10L)).thenReturn(false);
    when(categories.hasAssignedProducts(10L)).thenReturn(true);
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    assertThatThrownBy(() -> service.delete(10L))
      .isInstanceOf(CatalogueConflictException.class)
      .extracting("code")
      .isEqualTo("CATEGORY_DELETE_REJECTED");

    verify(categories).hasChildren(10L);
    verify(categories).hasAssignedProducts(10L);
    verify(categories, never()).deleteById(10L);
  }

  @Test
  void deletesSafeCategoryAndLogsLifecycleEvent() {
    when(categories.findById(10L)).thenReturn(
      Optional.of(category(10L, null, "Storage", "storage"))
    );
    ListAppender<ILoggingEvent> logs = captureLogs(CatalogueCategoryApplicationService.class);
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    service.delete(10L);

    verify(categories).deleteById(10L);
    assertStructuredLog(
      logs.list.getLast(),
      "catalogue category deleted",
      Map.of("catalogue.event", "category.deleted", "category.id", "10")
    );
  }

  @Test
  void repeatedDeleteAfterAbsenceReturnsNotFoundWithoutMutation() {
    when(categories.findById(10L)).thenReturn(Optional.empty());
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    assertThatThrownBy(() -> service.delete(10L)).isInstanceOf(CatalogueNotFoundException.class);

    verify(categories, never()).deleteById(10L);
  }

  @Test
  void lifecycleOperationsAreStateLevelIdempotent() {
    when(categories.findById(10L)).thenReturn(
      Optional.of(category(10L, null, "Storage", "storage")),
      Optional.of(category(10L, null, "Storage", "storage"))
    );
    when(categories.save(org.mockito.ArgumentMatchers.any())).thenReturn(
      category(10L, null, "Storage", "storage"),
      category(10L, null, "Storage", "storage")
    );
    ListAppender<ILoggingEvent> logs = captureLogs(CatalogueCategoryApplicationService.class);
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    service.activate(10L);
    service.deactivate(10L);

    var savedCategories = org.mockito.ArgumentCaptor.forClass(CatalogueCategory.class);
    verify(categories, org.mockito.Mockito.times(2)).save(savedCategories.capture());
    assertThat(savedCategories.getAllValues())
      .extracting(CatalogueCategory::active)
      .containsExactly(true, false);
    assertThat(logs.list).hasSize(2);
    assertStructuredLog(
      logs.list.get(0),
      "catalogue category lifecycle changed",
      Map.of("catalogue.event", "category.activated", "category.id", "10")
    );
    assertStructuredLog(
      logs.list.get(1),
      "catalogue category lifecycle changed",
      Map.of("catalogue.event", "category.deactivated", "category.id", "10")
    );
  }

  @Test
  void lifecycleOperationsPreserveSortRank() {
    when(categories.findById(10L)).thenReturn(
      Optional.of(category(10L, null, 1_000_000_000L, "Storage", "storage"))
    );
    when(categories.save(any(CatalogueCategory.class))).thenAnswer(invocation ->
      invocation.getArgument(0)
    );
    CatalogueCategoryApplicationService service = new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock
    );

    CatalogueCategory activated = service.activate(10L);

    assertThat(activated.sortRank()).isEqualTo(1_000_000_000L);
  }

  private CatalogueCategoryApplicationService serviceWith(CategorySystemDescriptor descriptor) {
    when(categorySystems.requireDescriptor("catalogue")).thenReturn(descriptor);
    return new CatalogueCategoryApplicationService(
      categories,
      new CatalogueCategoryCommandValidator(),
      slugWriteLock,
      categorySystems,
      new AdminCategoryTreePolicy()
    );
  }

  private static CategorySystemDescriptor rankedDescriptor(
    List<PlacementMode> placementModes,
    boolean sortRankExposed
  ) {
    return new CategorySystemDescriptor(
      "catalogue",
      "catalogue",
      ClosureTableDescriptor.of("catalogue_category", "catalogue_category_closure"),
      RankingCapabilityDescriptor.ranked(
        "sort_rank",
        1_000_000_000L,
        1_000_000_000L,
        1_000L,
        DisabledRankingRequestPolicy.REJECT,
        placementModes,
        sortRankExposed
      ),
      new FallbackOrderDescriptor(
        List.of(
          new FallbackOrderDescriptor.Order("name", "name", FallbackOrderDescriptor.Direction.ASC),
          new FallbackOrderDescriptor.Order("id", "id", FallbackOrderDescriptor.Direction.ASC)
        )
      ),
      Optional.of(SparseRankTableDescriptor.of("catalogue_category", "parent_id", "sort_rank"))
    );
  }

  private static CategorySystemDescriptor unrankedDescriptor() {
    return new CategorySystemDescriptor(
      "catalogue",
      "catalogue",
      ClosureTableDescriptor.of("catalogue_category", "catalogue_category_closure"),
      RankingCapabilityDescriptor.unranked(DisabledRankingRequestPolicy.REJECT),
      new FallbackOrderDescriptor(
        List.of(
          new FallbackOrderDescriptor.Order("name", "name", FallbackOrderDescriptor.Direction.ASC),
          new FallbackOrderDescriptor.Order("id", "id", FallbackOrderDescriptor.Direction.ASC)
        )
      ),
      Optional.empty()
    );
  }

  private static CatalogueCategory category(Long id, Long parentId, String name, String slug) {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-12T10:15:30Z");
    return new CatalogueCategory(id, parentId, name, slug, true, now, now);
  }

  private static CatalogueCategory category(
    Long id,
    Long parentId,
    Long sortRank,
    String name,
    String slug
  ) {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-12T10:15:30Z");
    return new CatalogueCategory(id, parentId, sortRank, name, slug, true, now, now);
  }

  private static CatalogueCategoryBreadcrumbItem breadcrumbItem(
    Long id,
    Long parentId,
    String name,
    String slug,
    int depth
  ) {
    return new CatalogueCategoryBreadcrumbItem(category(id, parentId, name, slug), depth);
  }

  private static CatalogueCategoryParentOption parentOption(
    Long id,
    Long parentId,
    String name,
    String slug,
    CatalogueCategoryBreadcrumbItem... path
  ) {
    return CatalogueCategoryParentOption.from(
      category(id, parentId, name, slug),
      path.length == 0 ? 0 : path[path.length - 1].depth(),
      List.of(path)
    );
  }

  private static ListAppender<ILoggingEvent> captureLogs(Class<?> type) {
    TransactionAwareListAppender appender = captureLogs(type, null, null);
    return appender;
  }

  private static TransactionAwareListAppender captureLogs(
    Class<?> type,
    List<String> timeline,
    String eventName
  ) {
    Logger logger = (Logger) LoggerFactory.getLogger(type);
    logger.setLevel(Level.INFO);
    TransactionAwareListAppender appender = new TransactionAwareListAppender(timeline, eventName);
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private static void detachLogs(Class<?> type, ListAppender<ILoggingEvent> appender) {
    Logger logger = (Logger) LoggerFactory.getLogger(type);
    logger.detachAppender(appender);
    appender.stop();
  }

  private static void assertCursorContextMismatch(Runnable invocation) {
    assertThatThrownBy(invocation::run)
      .isInstanceOf(InvalidRequestException.class)
      .hasMessage("cursor does not match the request context");
  }

  private static Map<String, String> rankedMutationFields(
    String event,
    String operation,
    String parentId
  ) {
    return Map.of(
      "catalogue.event",
      event,
      "category.system",
      "catalogue",
      "category.operation",
      operation,
      "category.id",
      "10",
      "category.parent_id",
      parentId,
      "category.ranking.result",
      "assigned"
    );
  }

  private static void assertStructuredLog(
    ILoggingEvent event,
    String message,
    Map<String, String> expectedFields
  ) {
    assertThat(event.getLevel()).isEqualTo(Level.INFO);
    assertThat(event.getFormattedMessage()).isEqualTo(message);
    assertThat(keyValues(event)).containsExactlyInAnyOrderEntriesOf(expectedFields);
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

  private static final class TransactionAwareListAppender extends ListAppender<ILoggingEvent> {

    private final List<Boolean> transactionActive = new ArrayList<>();
    private final List<String> timeline;
    private final String eventName;

    private TransactionAwareListAppender(List<String> timeline, String eventName) {
      this.timeline = timeline;
      this.eventName = eventName;
    }

    @Override
    protected void append(ILoggingEvent event) {
      transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
      if (timeline != null) {
        timeline.add(eventName);
      }
      super.append(event);
    }
  }

  private static final class TransactionLedger extends AbstractPlatformTransactionManager {

    private final List<String> timeline;
    private int commits;
    private int rollbacks;

    private TransactionLedger(List<String> timeline) {
      this.timeline = timeline;
    }

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      commits++;
      timeline.add("outer.commit");
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      rollbacks++;
      timeline.add("outer.rollback");
    }
  }
}
