package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.category.application.command.AdminCategoryCreateCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryMoveCommand;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryFilters;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentScope;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.List;
import java.util.Map;
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
    "app.category.admin.jdbc.enabled-systems[1]=descriptor-b",
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
    "uniapi.category-systems.descriptor-b.owning-module=category",
    "uniapi.category-systems.descriptor-b.node-table=test_category_b",
    "uniapi.category-systems.descriptor-b.closure-table=test_category_b_closure",
    "uniapi.category-systems.descriptor-b.ranking.enabled=true",
    "uniapi.category-systems.descriptor-b.ranking.column-name=sort_rank",
    "uniapi.category-systems.descriptor-b.ranking.placement-modes[0]=APPEND",
    "uniapi.category-systems.descriptor-b.ranking.placement-modes[1]=BEFORE_ID",
    "uniapi.category-systems.descriptor-b.ranking.placement-modes[2]=AFTER_ID",
    "uniapi.category-systems.descriptor-b.ranking.placement-modes[3]=TARGET_INDEX",
    "uniapi.category-systems.descriptor-b.ranking.sort-rank-exposed=true",
    "uniapi.category-systems.descriptor-b.fallback-order[0].property=name",
    "uniapi.category-systems.descriptor-b.fallback-order[0].column-name=name",
    "uniapi.category-systems.descriptor-b.fallback-order[0].direction=ASC",
    "uniapi.category-systems.descriptor-b.fallback-order[1].property=id",
    "uniapi.category-systems.descriptor-b.fallback-order[1].column-name=id",
    "uniapi.category-systems.descriptor-b.fallback-order[1].direction=ASC",
  }
)
class DescriptorDrivenAdminCategoryJdbcCrossSystemIsolationTest {

  private static final String CATALOGUE_CURSOR_LITERAL =
    "eyJ2ZXJzaW9uIjoxLCJtb2RlIjoiU0VBUkNIIiwicSI6ImdwdSIsImluY2x1ZGVJbmFjdGl2ZSI6dHJ1ZSwiZXhjbHVkZVN1YnRyZWVJZCI6OSwicmFuayI6MiwiYWN0aXZlIjp0cnVlLCJuYW1lIjoiZ3JhcGhpY3MgY2FyZHMiLCJpZCI6NDR9";

  @Autowired
  private DescriptorDrivenAdminCategoryJdbcProviderFactory factory;

  @Autowired
  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    DescriptorBackedAdminCategoryJdbcFixture.dropSchema(jdbc);
    DescriptorBackedAdminCategoryJdbcFixture.createSchema(jdbc);
  }

  @Test
  void keepsReadsMutationsHierarchyAndRankingScopedToSelectedSystem() {
    assertThat(factory.registrations())
      .extracting(registration -> registration.systemKey().orElseThrow())
      .containsExactlyInAnyOrder(
        DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A,
        DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_B
      );
    DescriptorDrivenAdminCategoryJdbcProvider providerA = provider(
      DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A
    );
    DescriptorDrivenAdminCategoryJdbcProvider providerB = provider(
      DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_B
    );

    assertThat(providerA).isNotSameAs(providerB);

    var aRoot = providerA.create(create("Components A", "shared", null));
    var bRoot = providerB.create(create("Components B", "shared", null));
    var aFirst = providerA.create(create("A First", "first", aRoot.id()));
    var aSecond = providerA.create(create("A Second", "second", aRoot.id()));
    var bFirst = providerB.create(create("B First", "first", bRoot.id()));

    assertThat(aRoot.id()).isEqualTo(bRoot.id());
    assertThat(aFirst.id()).isEqualTo(bFirst.id());
    assertThat(providerA.get(aRoot.id()).name()).isEqualTo("Components A");
    assertThat(providerB.get(bRoot.id()).name()).isEqualTo("Components B");
    assertThat(providerA.getNode(aFirst.id()).category().name()).isEqualTo("A First");
    assertThat(providerB.getNode(bFirst.id()).category().name()).isEqualTo("B First");
    assertThat(providerA.breadcrumb(aFirst.id()))
      .extracting(item -> item.category().name())
      .containsExactly("Components A", "A First");
    assertThat(providerB.breadcrumb(bFirst.id()))
      .extracting(item -> item.category().name())
      .containsExactly("Components B", "B First");
    assertThat(
      providerA
        .parentOptions(new AdminCategoryParentOptionsQuery(null, 20, null, List.of(), null, true))
        .data()
    )
      .extracting(option -> option.category().name())
      .containsExactly("A First", "A Second", "Components A")
      .doesNotContain("B First", "Components B");
    assertThat(
      providerB
        .parentOptions(new AdminCategoryParentOptionsQuery(null, 20, null, List.of(), null, true))
        .data()
    )
      .extracting(option -> option.category().name())
      .containsExactly("B First", "Components B")
      .doesNotContain("A First", "A Second", "Components A");

    providerA.move(aSecond.id(), new AdminCategoryMoveCommand(null, SparseOrderTarget.append()));
    providerA.deactivate(aRoot.id());

    assertThat(providerA.get(aRoot.id()).active()).isFalse();
    assertThat(providerB.get(bRoot.id()).active()).isTrue();
    assertThat(rootNames(providerA)).containsExactly("Components A", "A Second");
    assertThat(rootNames(providerB)).containsExactly("Components B");
    assertThat(rowCount(DescriptorBackedAdminCategoryJdbcFixture.TABLE_A, aRoot.id())).isOne();
    assertThat(rowCount(DescriptorBackedAdminCategoryJdbcFixture.TABLE_B, bRoot.id())).isOne();
  }

  @Test
  void rejectsCursorsFromOtherDescriptorBackedSystems() {
    DescriptorDrivenAdminCategoryJdbcProvider providerA = provider(
      DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A
    );
    DescriptorDrivenAdminCategoryJdbcProvider providerB = provider(
      DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_B
    );
    providerA.create(create("Alpha", "alpha", null));
    providerA.create(create("Beta", "beta", null));
    providerB.create(create("Only B", "only-b", null));
    providerB.create(create("Second B", "second-b", null));

    var firstPageA = providerA.parentOptions(
      new AdminCategoryParentOptionsQuery(null, 1, null, List.of(), null, true)
    );
    var firstPageB = providerB.parentOptions(
      new AdminCategoryParentOptionsQuery(null, 1, null, List.of(), null, true)
    );

    assertThat(firstPageA.hasMore()).isTrue();
    assertThat(firstPageB.hasMore()).isTrue();
    assertThatThrownBy(() ->
      providerB.parentOptions(
        new AdminCategoryParentOptionsQuery(null, 1, firstPageA.nextCursor(), List.of(), null, true)
      )
    ).isInstanceOfSatisfying(AdminCategoryException.class, ex ->
      assertThat(ex.code()).isEqualTo("CATEGORY_PARENT_OPTIONS_CURSOR_INVALID")
    );
    assertThatThrownBy(() ->
      providerA.parentOptions(
        new AdminCategoryParentOptionsQuery(null, 1, firstPageB.nextCursor(), List.of(), null, true)
      )
    ).isInstanceOfSatisfying(AdminCategoryException.class, ex ->
      assertThat(ex.code()).isEqualTo("CATEGORY_PARENT_OPTIONS_CURSOR_INVALID")
    );

    assertThat(
      providerA
        .parentOptions(
          new AdminCategoryParentOptionsQuery(
            null,
            1,
            firstPageA.nextCursor(),
            List.of(),
            null,
            true
          )
        )
        .data()
    )
      .extracting(option -> option.category().name())
      .containsExactly("Beta");
    assertThat(
      providerB
        .parentOptions(
          new AdminCategoryParentOptionsQuery(
            null,
            1,
            firstPageB.nextCursor(),
            List.of(),
            null,
            true
          )
        )
        .data()
    )
      .extracting(option -> option.category().name())
      .containsExactly("Second B");
  }

  @Test
  void wrongDescriptorKeyFailsBeforeNonSelectedStorageCanBeUsedAsFallback() {
    DescriptorDrivenAdminCategoryJdbcProvider providerA = provider(
      DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A
    );
    DescriptorDrivenAdminCategoryJdbcProvider providerB = provider(
      DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_B
    );
    providerA.create(create("A Alpha", "shared-alpha", null));
    providerA.create(create("A Beta", "shared-beta", null));
    providerB.create(create("B Alpha", "shared-alpha", null));
    providerB.create(create("B Beta", "shared-beta", null));

    var firstPageA = providerA.parentOptions(
      new AdminCategoryParentOptionsQuery(null, 1, null, List.of(), null, true)
    );
    DescriptorStoreFingerprint selectedBefore = fingerprint(
      DescriptorBackedAdminCategoryJdbcFixture.TABLE_B,
      DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_B
    );
    assertThat(firstPageA.nextCursor()).isNotBlank();

    dropStorage(
      DescriptorBackedAdminCategoryJdbcFixture.TABLE_A,
      DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_A
    );
    assertThat(tableExists(DescriptorBackedAdminCategoryJdbcFixture.TABLE_A)).isFalse();

    assertThatThrownBy(() ->
      providerB.parentOptions(
        new AdminCategoryParentOptionsQuery(null, 1, firstPageA.nextCursor(), List.of(), null, true)
      )
    ).isInstanceOfSatisfying(AdminCategoryException.class, ex ->
      assertThat(ex.code()).isEqualTo("CATEGORY_PARENT_OPTIONS_CURSOR_INVALID")
    );
    assertThat(
      fingerprint(
        DescriptorBackedAdminCategoryJdbcFixture.TABLE_B,
        DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_B
      )
    ).isEqualTo(selectedBefore);
  }

  @Test
  void catalogueCursorFailsInsideTheSelectedDescriptorWithoutCrossFormatRecovery() {
    DescriptorDrivenAdminCategoryJdbcProvider providerA = provider(
      DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A
    );
    DescriptorDrivenAdminCategoryJdbcProvider providerB = provider(
      DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_B
    );
    providerA.create(create("A Shared", "shared", null));
    providerB.create(create("B Shared", "shared", null));
    DescriptorStoreFingerprint selectedBefore = fingerprint(
      DescriptorBackedAdminCategoryJdbcFixture.TABLE_A,
      DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_A
    );

    dropStorage(
      DescriptorBackedAdminCategoryJdbcFixture.TABLE_B,
      DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_B
    );
    assertThat(tableExists(DescriptorBackedAdminCategoryJdbcFixture.TABLE_B)).isFalse();

    assertThatThrownBy(() ->
      providerA.parentOptions(
        new AdminCategoryParentOptionsQuery(
          "gpu",
          25,
          CATALOGUE_CURSOR_LITERAL,
          List.of(2L, 5L),
          9L,
          true
        )
      )
    ).isInstanceOfSatisfying(AdminCategoryException.class, ex ->
      assertThat(ex.code()).isEqualTo("CATEGORY_PARENT_OPTIONS_CURSOR_INVALID")
    );
    assertThat(
      fingerprint(
        DescriptorBackedAdminCategoryJdbcFixture.TABLE_A,
        DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_A
      )
    ).isEqualTo(selectedBefore);
  }

  @Test
  void selectedHydrationUsesOnlyTheSelectedDescriptorWhenIdsAndSlugsOverlap() {
    DescriptorDrivenAdminCategoryJdbcProvider providerA = provider(
      DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A
    );
    DescriptorDrivenAdminCategoryJdbcProvider providerB = provider(
      DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_B
    );
    var aRoot = providerA.create(create("A Root", "shared-root", null));
    var bRoot = providerB.create(create("B Root", "shared-root", null));
    var aChild = providerA.create(create("A Child", "shared-child", aRoot.id()));
    var bChild = providerB.create(create("B Child", "shared-child", bRoot.id()));
    var aSelected = providerA.create(create("A Selected", "shared-selected", aRoot.id()));
    var bSelected = providerB.create(create("B Selected", "shared-selected", bRoot.id()));
    providerA.deactivate(aSelected.id());
    providerB.deactivate(bSelected.id());

    assertThat(aRoot.id()).isEqualTo(bRoot.id());
    assertThat(aChild.id()).isEqualTo(bChild.id());
    assertThat(aSelected.id()).isEqualTo(bSelected.id());
    DescriptorStoreFingerprint aBefore = fingerprint(
      DescriptorBackedAdminCategoryJdbcFixture.TABLE_A,
      DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_A
    );
    DescriptorStoreFingerprint bBefore = fingerprint(
      DescriptorBackedAdminCategoryJdbcFixture.TABLE_B,
      DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_B
    );

    assertThat(
      providerA
        .parentOptions(
          new AdminCategoryParentOptionsQuery(
            null,
            10,
            null,
            List.of(aSelected.id(), aSelected.id(), aChild.id()),
            null,
            false
          )
        )
        .data()
    )
      .extracting(option -> option.category().name())
      .contains("A Selected", "A Child")
      .doesNotContain("B Selected", "B Child");
    assertThat(
      providerB
        .parentOptions(
          new AdminCategoryParentOptionsQuery(
            null,
            10,
            null,
            List.of(bSelected.id(), bSelected.id(), bChild.id()),
            null,
            false
          )
        )
        .data()
    )
      .extracting(option -> option.category().name())
      .contains("B Selected", "B Child")
      .doesNotContain("A Selected", "A Child");

    assertThat(
      fingerprint(
        DescriptorBackedAdminCategoryJdbcFixture.TABLE_A,
        DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_A
      )
    ).isEqualTo(aBefore);
    assertThat(
      fingerprint(
        DescriptorBackedAdminCategoryJdbcFixture.TABLE_B,
        DescriptorBackedAdminCategoryJdbcFixture.CLOSURE_B
      )
    ).isEqualTo(bBefore);
  }

  private DescriptorDrivenAdminCategoryJdbcProvider provider(String systemKey) {
    return factory
      .registrations()
      .stream()
      .filter(registration -> registration.systemKey().filter(systemKey::equals).isPresent())
      .findFirst()
      .flatMap(registration -> registration.runtimeBinding())
      .flatMap(binding -> binding.ports().categoryRead())
      .filter(DescriptorDrivenAdminCategoryJdbcProvider.class::isInstance)
      .map(DescriptorDrivenAdminCategoryJdbcProvider.class::cast)
      .orElseThrow();
  }

  private static AdminCategoryCreateCommand create(String name, String slug, Long parentId) {
    return new AdminCategoryCreateCommand(parentId, name, slug, true, SparseOrderTarget.append());
  }

  private List<String> rootNames(DescriptorDrivenAdminCategoryJdbcProvider provider) {
    return provider
      .list(
        new AdminCategoryListQuery(
          AdminCategoryParentScope.roots(),
          new AdminCategoryFilters(null, null, null),
          new PageCriteria(0, 20),
          new SortCriteria(
            List.of(
              new SortOrder("sortRank", SortDirection.ASC),
              new SortOrder("id", SortDirection.ASC)
            )
          )
        )
      )
      .content()
      .stream()
      .map(node -> node.category().name())
      .toList();
  }

  private int rowCount(String tableName, Long id) {
    return jdbc.queryForObject(
      "SELECT COUNT(*) FROM " + tableName + " WHERE id = ?",
      Integer.class,
      id
    );
  }

  private DescriptorStoreFingerprint fingerprint(String tableName, String closureTableName) {
    List<Map<String, Object>> rows = jdbc.queryForList(
      "SELECT id, parent_id, sort_rank, name, slug, active FROM " + tableName + " ORDER BY id"
    );
    List<Map<String, Object>> closureRows = jdbc.queryForList(
      "SELECT ancestor_id, descendant_id, depth FROM " +
        closureTableName +
        " ORDER BY ancestor_id, descendant_id"
    );
    return new DescriptorStoreFingerprint(rows, closureRows);
  }

  private void dropStorage(String tableName, String closureTableName) {
    jdbc.execute("DROP TABLE " + closureTableName);
    jdbc.execute("DROP TABLE " + tableName);
  }

  private boolean tableExists(String tableName) {
    return Boolean.TRUE.equals(
      jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, tableName)
    );
  }

  private record DescriptorStoreFingerprint(
    List<Map<String, Object>> rows,
    List<Map<String, Object>> closureRows
  ) {}

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
