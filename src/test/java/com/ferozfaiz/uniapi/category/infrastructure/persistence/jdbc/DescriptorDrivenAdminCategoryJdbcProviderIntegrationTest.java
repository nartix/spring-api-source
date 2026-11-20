package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyReadPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryFilters;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentScope;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryTreeQuery;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryRuntimePorts;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

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
class DescriptorDrivenAdminCategoryJdbcProviderIntegrationTest {

  @Autowired
  private DescriptorDrivenAdminCategoryJdbcProviderFactory factory;

  @Autowired
  private JdbcTemplate jdbc;

  private Long rootId;
  private Long gpuId;
  private Long hiddenId;

  @BeforeEach
  void setUp() {
    DescriptorBackedAdminCategoryJdbcFixture.dropSchema(jdbc);
    DescriptorBackedAdminCategoryJdbcFixture.createSchema(jdbc);
    rootId = insertNode("Components", "components", null, true, 1_000_000_000L);
    gpuId = insertNode("GPUs", "gpus", rootId, true, 1_000_000_000L);
    Long gamingId = insertNode("Gaming Gear", "gaming-gear", rootId, true, 2_000_000_000L);
    hiddenId = insertNode("Hidden GPUs", "hidden-gpus", rootId, false, 3_000_000_000L);

    insertPath(rootId, rootId, 0);
    insertPath(gpuId, gpuId, 0);
    insertPath(rootId, gpuId, 1);
    insertPath(gamingId, gamingId, 0);
    insertPath(rootId, gamingId, 1);
    insertPath(hiddenId, hiddenId, 0);
    insertPath(rootId, hiddenId, 1);
  }

  @Test
  void readsDescriptorBackedListTreeDetailBreadcrumbAndParentOptions() {
    AdminCategoryReadPort categoryRead = ports().categoryRead().orElseThrow();
    AdminCategoryHierarchyReadPort hierarchyRead = ports().hierarchyRead().orElseThrow();

    var list = categoryRead.list(
      new AdminCategoryListQuery(
        AdminCategoryParentScope.children(rootId),
        new AdminCategoryFilters(true, "gpu", null),
        new PageCriteria(0, 20),
        sort("name", SortDirection.ASC)
      )
    );
    var tree = hierarchyRead.tree(
      new AdminCategoryTreeQuery(new PageCriteria(0, 20), sort("id", SortDirection.ASC))
    );
    var detail = categoryRead.get(gpuId);
    var node = categoryRead.getNode(gpuId);
    var breadcrumb = hierarchyRead.breadcrumb(gpuId);
    var firstOptions = hierarchyRead.parentOptions(
      new AdminCategoryParentOptionsQuery("g", 1, null, List.of(), null, false)
    );
    var secondOptions = hierarchyRead.parentOptions(
      new AdminCategoryParentOptionsQuery("g", 1, firstOptions.nextCursor(), List.of(), null, false)
    );
    var includedInactiveOptions = hierarchyRead.parentOptions(
      new AdminCategoryParentOptionsQuery(null, 10, null, List.of(hiddenId), null, false)
    );
    var excludedSubtreeOptions = hierarchyRead.parentOptions(
      new AdminCategoryParentOptionsQuery(null, 10, null, List.of(), rootId, true)
    );

    assertThat(list.content())
      .extracting(item -> item.category().slug())
      .containsExactly("gpus");
    assertThat(list.totalElements()).isEqualTo(1);
    assertThat(tree.totalElements()).isEqualTo(4);
    assertThat(detail.slug()).isEqualTo("gpus");
    assertThat(node.depth()).isEqualTo(1);
    assertThat(breadcrumb)
      .extracting(item -> item.category().slug())
      .containsExactly("components", "gpus");
    assertThat(firstOptions.data()).hasSize(1);
    assertThat(firstOptions.hasMore()).isTrue();
    assertThat(firstOptions.nextCursor()).isNotBlank();
    assertThat(secondOptions.data()).hasSize(1);
    assertThat(secondOptions.data().getFirst().category().id()).isNotEqualTo(
      firstOptions.data().getFirst().category().id()
    );
    assertThat(includedInactiveOptions.data())
      .extracting(option -> option.category().id())
      .contains(hiddenId);
    assertThat(includedInactiveOptions.data().getFirst().path()).isNotEmpty();
    assertThat(excludedSubtreeOptions.data()).isEmpty();
  }

  @Test
  void hydratesAndRepeatsSelectedRowsWhileKeepingInactiveOptionsSelectable() {
    AdminCategoryHierarchyReadPort hierarchyRead = ports().hierarchyRead().orElseThrow();

    var firstPage = hierarchyRead.parentOptions(
      new AdminCategoryParentOptionsQuery(null, 2, null, List.of(hiddenId), null, false)
    );
    var secondPage = hierarchyRead.parentOptions(
      new AdminCategoryParentOptionsQuery(
        null,
        2,
        firstPage.nextCursor(),
        List.of(hiddenId),
        null,
        false
      )
    );
    var ordinaryInactive = hierarchyRead
      .parentOptions(new AdminCategoryParentOptionsQuery(null, 10, null, List.of(), null, true))
      .data()
      .stream()
      .filter(option -> option.category().id().equals(hiddenId))
      .findFirst()
      .orElseThrow();

    assertThat(firstPage.data())
      .extracting(option -> option.category().id())
      .containsExactly(hiddenId, rootId);
    assertThat(firstPage.hasMore()).isTrue();
    assertThat(firstPage.nextCursor()).isNotBlank();
    assertThat(secondPage.data())
      .extracting(option -> option.category().name())
      .containsExactly("Hidden GPUs", "Gaming Gear");
    assertThat(secondPage.hasMore()).isTrue();
    assertThat(secondPage.nextCursor()).isNotBlank();

    var selectedInactive = firstPage.data().getFirst();
    assertThat(selectedInactive.path())
      .extracting(item -> item.category().name())
      .containsExactly("Components", "Hidden GPUs");
    assertThat(selectedInactive.selectable()).isTrue();
    assertThat(selectedInactive.disabledReason()).isNull();
    assertThat(ordinaryInactive.selectable()).isTrue();
    assertThat(ordinaryInactive.disabledReason()).isNull();
  }

  @Test
  void continuesAcrossEqualNameTiesByIdentifier() {
    Long firstTieId = insertNode("Same Name", "same-name-one", null, true, 4_000_000_000L);
    Long secondTieId = insertNode("Same Name", "same-name-two", null, true, 5_000_000_000L);
    insertPath(firstTieId, firstTieId, 0);
    insertPath(secondTieId, secondTieId, 0);
    AdminCategoryHierarchyReadPort hierarchyRead = ports().hierarchyRead().orElseThrow();

    var firstPage = hierarchyRead.parentOptions(
      new AdminCategoryParentOptionsQuery("same name", 1, null, List.of(), null, false)
    );
    var secondPage = hierarchyRead.parentOptions(
      new AdminCategoryParentOptionsQuery(
        "same name",
        1,
        firstPage.nextCursor(),
        List.of(),
        null,
        false
      )
    );

    assertThat(firstPage.data())
      .extracting(option -> option.category().id())
      .containsExactly(firstTieId);
    assertThat(firstPage.hasMore()).isTrue();
    assertThat(secondPage.data())
      .extracting(option -> option.category().id())
      .containsExactly(secondTieId);
    assertThat(secondPage.hasMore()).isFalse();
    assertThat(secondPage.nextCursor()).isNull();
  }

  @Test
  void acceptsFabricatedStalePositionsAsOrdinaryKeysetPositions() {
    var context = new AdminCategoryParentOptionsCursorCodec.RequestContext(
      null,
      false,
      null,
      List.of()
    );
    String staleCursor = new AdminCategoryParentOptionsCursorCodec().encode(
      DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A,
      context,
      new AdminCategoryParentOptionsCursorCodec.Position(
        0,
        true,
        "fabricated deleted row",
        Long.MAX_VALUE
      )
    );

    var result = ports()
      .hierarchyRead()
      .orElseThrow()
      .parentOptions(
        new AdminCategoryParentOptionsQuery(null, 10, staleCursor, List.of(), null, false)
      );

    assertThat(result.data())
      .extracting(option -> option.category().name())
      .containsExactly("Gaming Gear", "GPUs");
    assertThat(result.hasMore()).isFalse();
    assertThat(result.nextCursor()).isNull();
  }

  @Test
  void mapsMissingCategoryParentAndInvalidCursorToGenericErrors() {
    AdminCategoryReadPort categoryRead = ports().categoryRead().orElseThrow();
    AdminCategoryHierarchyReadPort hierarchyRead = ports().hierarchyRead().orElseThrow();
    String rawCursor = "invalid-secret-parent-options-token!";

    assertThatThrownBy(() -> categoryRead.get(404L)).isInstanceOfSatisfying(
      AdminCategoryException.class,
      ex -> assertThat(ex.code()).isEqualTo("CATEGORY_NOT_FOUND")
    );
    assertThatThrownBy(() ->
      categoryRead.list(
        new AdminCategoryListQuery(
          AdminCategoryParentScope.children(404L),
          new AdminCategoryFilters(null, null, null),
          new PageCriteria(0, 20),
          sort("id", SortDirection.ASC)
        )
      )
    ).isInstanceOfSatisfying(AdminCategoryException.class, ex ->
      assertThat(ex.code()).isEqualTo("CATEGORY_PARENT_NOT_FOUND")
    );
    assertThatThrownBy(() ->
      hierarchyRead.parentOptions(
        new AdminCategoryParentOptionsQuery(null, 25, rawCursor, List.of(), null, false)
      )
    ).isInstanceOfSatisfying(AdminCategoryException.class, ex -> {
      assertThat(ex.code()).isEqualTo("CATEGORY_PARENT_OPTIONS_CURSOR_INVALID");
      assertThat(ex.getMessage()).doesNotContain(rawCursor, "secret-parent-options-token");
    });
  }

  private AdminCategoryRuntimePorts ports() {
    return factory.registrations().getFirst().runtimeBinding().orElseThrow().ports();
  }

  private Long insertNode(String name, String slug, Long parentId, boolean active, long sortRank) {
    return DescriptorBackedAdminCategoryJdbcFixture.insertNode(
      jdbc,
      DescriptorBackedAdminCategoryJdbcFixture.TABLE_A,
      name,
      slug,
      parentId,
      active,
      sortRank
    );
  }

  private void insertPath(Long ancestorId, Long descendantId, int depth) {
    DescriptorBackedAdminCategoryJdbcFixture.insertClosure(
      jdbc,
      DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_A,
      ancestorId,
      descendantId,
      depth
    );
  }

  private static SortCriteria sort(String property, SortDirection direction) {
    return new SortCriteria(List.of(new SortOrder(property, direction)));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ContainersConfiguration {

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer<?> postgresContainer() {
      return new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"));
    }
  }
}
