package com.ferozfaiz.uniapi.category.infrastructure.persistence.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.category.application.command.AdminCategoryCreateCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryMoveCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryPatchCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryReorderCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryUpdateCommand;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryBasicMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyReadPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryLifecyclePort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOrderingPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryFilters;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentScope;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
class DescriptorDrivenAdminCategoryJdbcProviderMutationIntegrationTest {

  @Autowired
  private DescriptorDrivenAdminCategoryJdbcProviderFactory factory;

  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    DescriptorBackedAdminCategoryJdbcFixture.dropSchema(jdbc);
    DescriptorBackedAdminCategoryJdbcFixture.createSchema(jdbc);
  }

  @Test
  void mutatesDescriptorBackedCategoriesThroughFullLifecycle() {
    DescriptorPorts ports = ports(DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A);
    var parent = ports.basicMutation().create(create("Components", "components", null));
    var second = ports.basicMutation().create(create("Second", "second", parent.id()));
    var first = ports
      .basicMutation()
      .create(
        new AdminCategoryCreateCommand(
          parent.id(),
          "First",
          "first",
          true,
          SparseOrderTarget.from(second.id(), null, null)
        )
      );

    assertThat(parent.id()).isPositive();
    assertThat(parent.createdAt()).isNotNull();
    assertThat(parent.updatedAt()).isNotNull();
    assertThat(childSlugs(ports.categoryRead(), parent.id())).containsExactly("first", "second");

    var updated = ports
      .basicMutation()
      .replace(
        first.id(),
        new AdminCategoryUpdateCommand(parent.id(), "First Updated", "first-updated", true)
      );
    assertThat(updated.slug()).isEqualTo("first-updated");

    var patched = ports
      .basicMutation()
      .patch(
        first.id(),
        new AdminCategoryPatchCommand(null, true, "First Root", true, null, false, false, true)
      );
    assertThat(patched.parentId()).isNull();
    assertThat(patched.name()).isEqualTo("First Root");
    assertThat(patched.active()).isFalse();

    ports.lifecycle().activate(first.id());
    assertThat(ports.categoryRead().get(first.id()).active()).isTrue();

    ports
      .hierarchyMutation()
      .move(first.id(), new AdminCategoryMoveCommand(parent.id(), SparseOrderTarget.append()));
    ports
      .ordering()
      .reorder(
        first.id(),
        new AdminCategoryReorderCommand(
          parent.id(),
          SparseOrderTarget.from(null, second.id(), null)
        )
      );
    assertThat(childSlugs(ports.categoryRead(), parent.id())).containsExactly(
      "second",
      "first-updated"
    );

    ports.lifecycle().deactivate(first.id());
    assertThat(ports.categoryRead().get(first.id()).active()).isFalse();

    ports.basicMutation().delete(first.id());
    assertThat(rowCount(DescriptorBackedAdminCategoryJdbcFixture.TABLE_A, first.id())).isZero();
    assertThat(childSlugs(ports.categoryRead(), parent.id())).containsExactly("second");
  }

  @Test
  void mapsMutationFailuresToGenericCategoryErrors() {
    DescriptorPorts ports = ports(DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A);
    var root = ports.basicMutation().create(create("Components", "components", null));
    var child = ports.basicMutation().create(create("Child", "child", root.id()));
    var grandchild = ports.basicMutation().create(create("Grandchild", "grandchild", child.id()));
    var sibling = ports.basicMutation().create(create("Sibling", "sibling", root.id()));

    assertError(
      () -> ports.basicMutation().create(create("Duplicate", "components", null)),
      "DUPLICATE_CATEGORY_SLUG"
    );
    assertError(
      () -> ports.basicMutation().create(create("Missing", "missing", 404L)),
      "CATEGORY_PARENT_NOT_FOUND"
    );
    assertError(
      () ->
        ports
          .basicMutation()
          .replace(404L, new AdminCategoryUpdateCommand(null, "Missing", "missing", true)),
      "CATEGORY_NOT_FOUND"
    );
    assertError(
      () ->
        ports
          .basicMutation()
          .replace(root.id(), new AdminCategoryUpdateCommand(grandchild.id(), "Bad", "bad", true)),
      "INVALID_CATEGORY_MOVE"
    );
    assertError(
      () ->
        ports
          .hierarchyMutation()
          .move(root.id(), new AdminCategoryMoveCommand(root.id(), SparseOrderTarget.append())),
      "INVALID_CATEGORY_MOVE"
    );
    assertError(
      () ->
        ports
          .hierarchyMutation()
          .move(root.id(), new AdminCategoryMoveCommand(child.id(), SparseOrderTarget.append())),
      "INVALID_CATEGORY_MOVE"
    );
    assertError(
      () ->
        ports
          .ordering()
          .reorder(
            child.id(),
            new AdminCategoryReorderCommand(
              root.id(),
              SparseOrderTarget.from(grandchild.id(), null, null)
            )
          ),
      "INVALID_CATEGORY_PLACEMENT_TARGET"
    );
    assertError(() -> ports.basicMutation().delete(root.id()), "CATEGORY_DELETE_BLOCKED");
    assertThat(ports.categoryRead().get(sibling.id()).slug()).isEqualTo("sibling");
  }

  @Test
  void rollsBackParentChangeWhenRankPlacementFailsAfterClosureMove() {
    DescriptorPorts ports = ports(DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A);
    var source = ports.basicMutation().create(create("Source", "source", null));
    var destination = ports.basicMutation().create(create("Destination", "destination", null));
    var outsideTarget = ports.basicMutation().create(create("Outside", "outside", source.id()));
    var moving = ports.basicMutation().create(create("Moving", "moving", source.id()));
    ports
      .basicMutation()
      .create(create("Destination Child", "destination-child", destination.id()));

    assertError(
      () ->
        ports
          .hierarchyMutation()
          .move(
            moving.id(),
            new AdminCategoryMoveCommand(
              destination.id(),
              SparseOrderTarget.from(outsideTarget.id(), null, null)
            )
          ),
      "INVALID_CATEGORY_PLACEMENT_TARGET"
    );

    assertThat(ports.categoryRead().get(moving.id()).parentId()).isEqualTo(source.id());
    assertThat(isDescendant(moving.id(), source.id())).isTrue();
    assertThat(isDescendant(moving.id(), destination.id())).isFalse();
  }

  @Test
  void everyDescriptorMutationJoinsAnOuterTransactionAndRollsBackTogether() {
    DescriptorPorts ports = ports(DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A);
    var source = ports.basicMutation().create(create("Source", "source", null));
    var destination = ports.basicMutation().create(create("Destination", "destination", null));
    var existing = ports.basicMutation().create(create("Original", "original", source.id()));
    Long originalRank = existing.sortRank();
    AtomicReference<Long> rolledBackCreateId = new AtomicReference<>();

    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      rolledBackCreateId.set(
        ports
          .basicMutation()
          .create(create("Rolled Back Create", "rolled-back-create", source.id()))
          .id()
      );
      ports
        .basicMutation()
        .replace(
          existing.id(),
          new AdminCategoryUpdateCommand(source.id(), "Updated", "updated", true)
        );
      ports
        .basicMutation()
        .patch(
          existing.id(),
          new AdminCategoryPatchCommand(null, false, "Patched", true, null, false, null, false)
        );
      ports
        .hierarchyMutation()
        .move(
          existing.id(),
          new AdminCategoryMoveCommand(destination.id(), SparseOrderTarget.append())
        );
      ports
        .ordering()
        .reorder(
          existing.id(),
          new AdminCategoryReorderCommand(destination.id(), SparseOrderTarget.from(null, null, 0))
        );
      ports.lifecycle().deactivate(existing.id());
      ports.lifecycle().activate(existing.id());
      ports.basicMutation().delete(existing.id());
      status.setRollbackOnly();
    });

    var restored = ports.categoryRead().get(existing.id());
    assertThat(restored.name()).isEqualTo("Original");
    assertThat(restored.slug()).isEqualTo("original");
    assertThat(restored.parentId()).isEqualTo(source.id());
    assertThat(restored.sortRank()).isEqualTo(originalRank);
    assertThat(restored.active()).isTrue();
    assertThat(rowCount(DescriptorBackedAdminCategoryJdbcFixture.TABLE_A, existing.id())).isOne();
    assertThat(
      rowCount(DescriptorBackedAdminCategoryJdbcFixture.TABLE_A, rolledBackCreateId.get())
    ).isZero();
    assertThat(isDescendant(existing.id(), source.id())).isTrue();
    assertThat(isDescendant(existing.id(), destination.id())).isFalse();
  }

  @Test
  void maintainsHierarchyAndOrderingAcrossPlacementLifecycleAndDeletionBranches() {
    DescriptorPorts ports = ports(DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A);
    var source = ports.basicMutation().create(create("Source", "source", null));
    var destination = ports.basicMutation().create(create("Destination", "destination", null));
    var first = ports.basicMutation().create(create("First", "first", source.id()));
    var second = ports.basicMutation().create(create("Second", "second", source.id()));
    var third = ports.basicMutation().create(create("Third", "third", source.id()));
    var destinationChild = ports
      .basicMutation()
      .create(create("Destination Child", "destination-child", destination.id()));

    ports
      .ordering()
      .reorder(
        third.id(),
        new AdminCategoryReorderCommand(source.id(), SparseOrderTarget.from(null, null, 0))
      );
    assertThat(childSlugs(ports.categoryRead(), source.id())).containsExactly(
      "third",
      "first",
      "second"
    );

    ports
      .ordering()
      .reorder(
        third.id(),
        new AdminCategoryReorderCommand(
          source.id(),
          SparseOrderTarget.from(null, second.id(), null)
        )
      );
    assertThat(childSlugs(ports.categoryRead(), source.id())).containsExactly(
      "first",
      "second",
      "third"
    );

    ports
      .hierarchyMutation()
      .move(
        third.id(),
        new AdminCategoryMoveCommand(
          destination.id(),
          SparseOrderTarget.from(destinationChild.id(), null, null)
        )
      );
    assertThat(childSlugs(ports.categoryRead(), destination.id())).containsExactly(
      "third",
      "destination-child"
    );
    assertThat(ports.hierarchyRead().breadcrumb(third.id()))
      .extracting(item -> item.category().slug())
      .containsExactly("destination", "third");
    assertThat(isDescendant(third.id(), source.id())).isFalse();
    assertThat(isDescendant(third.id(), destination.id())).isTrue();

    ports.lifecycle().deactivate(second.id());
    assertThat(ports.categoryRead().get(second.id()).active()).isFalse();
    ports.lifecycle().activate(second.id());
    assertThat(ports.categoryRead().get(second.id()).active()).isTrue();

    assertError(() -> ports.basicMutation().delete(source.id()), "CATEGORY_DELETE_BLOCKED");
    ports.basicMutation().delete(third.id());
    assertThat(rowCount(DescriptorBackedAdminCategoryJdbcFixture.TABLE_A, third.id())).isZero();
    assertThat(closureRowCount(third.id())).isZero();
  }

  @Test
  void keepsDescriptorBackedMutationsIsolatedBySelectedSystem() {
    DescriptorPorts portsA = ports(DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_A);
    DescriptorPorts portsB = ports(DescriptorBackedAdminCategoryJdbcFixture.SYSTEM_B);

    var a = portsA.basicMutation().create(create("Components A", "components", null));
    var b = portsB.basicMutation().create(create("Components B", "components", null));
    var aDestination = portsA.basicMutation().create(create("Destination A", "destination", null));
    var bDestination = portsB.basicMutation().create(create("Destination B", "destination", null));
    var aChild = portsA.basicMutation().create(create("Child A", "child", a.id()));
    var bChild = portsB.basicMutation().create(create("Child B", "child", b.id()));

    portsA
      .basicMutation()
      .replace(aChild.id(), new AdminCategoryUpdateCommand(a.id(), "Updated A", "updated-a", true));
    portsA
      .basicMutation()
      .patch(
        aChild.id(),
        new AdminCategoryPatchCommand(null, false, "Patched A", true, null, false, null, false)
      );
    portsA
      .hierarchyMutation()
      .move(
        aChild.id(),
        new AdminCategoryMoveCommand(aDestination.id(), SparseOrderTarget.append())
      );
    portsA
      .ordering()
      .reorder(
        aChild.id(),
        new AdminCategoryReorderCommand(aDestination.id(), SparseOrderTarget.from(null, null, 0))
      );
    portsA.lifecycle().deactivate(aChild.id());
    portsA.lifecycle().activate(aChild.id());
    portsA.basicMutation().delete(aChild.id());
    portsA.lifecycle().deactivate(a.id());

    assertThat(portsA.categoryRead().get(a.id()).active()).isFalse();
    assertThat(portsB.categoryRead().get(b.id()).active()).isTrue();
    assertThat(portsB.categoryRead().get(bDestination.id()).name()).isEqualTo("Destination B");
    assertThat(portsB.categoryRead().get(bChild.id()).name()).isEqualTo("Child B");
    assertThat(portsB.categoryRead().get(bChild.id()).parentId()).isEqualTo(b.id());
    assertThat(portsB.categoryRead().get(bChild.id()).active()).isTrue();
    assertThat(rowCount(DescriptorBackedAdminCategoryJdbcFixture.TABLE_A, a.id())).isOne();
    assertThat(rowCount(DescriptorBackedAdminCategoryJdbcFixture.TABLE_B, b.id())).isOne();
    assertThat(rowCount(DescriptorBackedAdminCategoryJdbcFixture.TABLE_A, aChild.id())).isZero();
    assertThat(rowCount(DescriptorBackedAdminCategoryJdbcFixture.TABLE_B, bChild.id())).isOne();
  }

  private DescriptorPorts ports(String systemKey) {
    var runtimePorts = factory
      .registrations()
      .stream()
      .filter(registration -> registration.systemKey().filter(systemKey::equals).isPresent())
      .findFirst()
      .flatMap(registration -> registration.runtimeBinding())
      .orElseThrow()
      .ports();
    return new DescriptorPorts(
      runtimePorts.categoryRead().orElseThrow(),
      runtimePorts.hierarchyRead().orElseThrow(),
      runtimePorts.basicMutation().orElseThrow(),
      runtimePorts.hierarchyMutation().orElseThrow(),
      runtimePorts.ordering().orElseThrow(),
      runtimePorts.lifecycle().orElseThrow()
    );
  }

  private static AdminCategoryCreateCommand create(String name, String slug, Long parentId) {
    return new AdminCategoryCreateCommand(parentId, name, slug, true, SparseOrderTarget.append());
  }

  private List<String> childSlugs(AdminCategoryReadPort categoryRead, Long parentId) {
    return categoryRead
      .list(
        new AdminCategoryListQuery(
          AdminCategoryParentScope.children(parentId),
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
      .map(node -> node.category().slug())
      .toList();
  }

  private record DescriptorPorts(
    AdminCategoryReadPort categoryRead,
    AdminCategoryHierarchyReadPort hierarchyRead,
    AdminCategoryBasicMutationPort basicMutation,
    AdminCategoryHierarchyMutationPort hierarchyMutation,
    AdminCategoryOrderingPort ordering,
    AdminCategoryLifecyclePort lifecycle
  ) {}

  private int rowCount(String tableName, Long id) {
    return jdbc.queryForObject(
      "SELECT COUNT(*) FROM " + tableName + " WHERE id = ?",
      Integer.class,
      id
    );
  }

  private boolean isDescendant(Long descendantId, Long ancestorId) {
    return Boolean.TRUE.equals(
      jdbc.queryForObject(
        "SELECT EXISTS (SELECT 1 FROM test_category_a_closure WHERE descendant_id = ? AND ancestor_id = ? AND depth > 0)",
        Boolean.class,
        descendantId,
        ancestorId
      )
    );
  }

  private int closureRowCount(Long id) {
    return jdbc.queryForObject(
      "SELECT COUNT(*) FROM test_category_a_closure WHERE descendant_id = ? OR ancestor_id = ?",
      Integer.class,
      id,
      id
    );
  }

  private static void assertError(Runnable action, String code) {
    assertThatThrownBy(action::run).isInstanceOfSatisfying(AdminCategoryException.class, ex ->
      assertThat(ex.code()).isEqualTo(code)
    );
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
