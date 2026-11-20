package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.provider.CatalogueAdminCategoryProvider;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryCreateCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryMoveCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryPatchCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryReorderCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryUpdateCommand;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryFilters;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryListQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentScope;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;

class CatalogueCategoryGenericMutationJpaIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  @Autowired
  private CatalogueAdminCategoryProvider provider;

  @Autowired
  private CatalogueCategoryApplicationService categoryService;

  @Autowired
  private TransactionTemplate transactions;

  @Test
  void genericProviderMutationsDelegateToCataloguePersistence() {
    var parent = provider.create(create("Components", "components", null));
    var first = provider.create(create("First", "first", parent.id()));
    var second = provider.create(create("Second", "second", parent.id()));

    provider.reorder(
      first.id(),
      new AdminCategoryReorderCommand(parent.id(), SparseOrderTarget.from(null, second.id(), null))
    );

    assertThat(childSlugs(parent.id())).containsExactly("second", "first");

    var updated = provider.replace(
      first.id(),
      new AdminCategoryUpdateCommand(parent.id(), "First Updated", "first-updated", true)
    );
    assertThat(updated.slug()).isEqualTo("first-updated");

    var patched = provider.patch(
      first.id(),
      new AdminCategoryPatchCommand(null, true, "First Root", true, null, false, false, true)
    );
    assertThat(patched.parentId()).isNull();
    assertThat(patched.name()).isEqualTo("First Root");
    assertThat(patched.active()).isFalse();

    provider.activate(first.id());
    assertThat(categoryService.get(first.id()).active()).isTrue();

    provider.move(
      first.id(),
      new AdminCategoryMoveCommand(parent.id(), SparseOrderTarget.append())
    );
    assertThat(categoryService.get(first.id()).parentId()).isEqualTo(parent.id());

    provider.deactivate(first.id());
    assertThat(categoryService.get(first.id()).active()).isFalse();

    provider.delete(first.id());
    assertThat(childSlugs(parent.id())).containsExactly("second");
  }

  @Test
  void genericProviderDeleteGuardRollsBackWithoutRemovingTheHierarchy() {
    var parent = provider.create(create("Parent", "parent", null));
    var child = provider.create(create("Child", "child", parent.id()));

    assertThatThrownBy(() -> provider.delete(parent.id())).isInstanceOfSatisfying(
      AdminCategoryException.class,
      failure -> {
        assertThat(failure.code()).isEqualTo("CATEGORY_DELETE_BLOCKED");
        assertThat(failure.messageArgs()).containsExactly(parent.id());
      }
    );

    assertThat(provider.get(parent.id()).slug()).isEqualTo("parent");
    assertThat(provider.get(child.id()).parentId()).isEqualTo(parent.id());
  }

  @Test
  void genericProviderInvalidHierarchyMoveRollsBackTheOriginalParentage() {
    var parent = provider.create(create("Parent", "parent", null));
    var child = provider.create(create("Child", "child", parent.id()));

    assertThatThrownBy(() ->
      provider.move(
        parent.id(),
        new AdminCategoryMoveCommand(child.id(), SparseOrderTarget.append())
      )
    ).isInstanceOfSatisfying(AdminCategoryException.class, failure -> {
      assertThat(failure.code()).isEqualTo("INVALID_CATEGORY_MOVE");
      assertThat(failure.messageArgs()).containsExactly(parent.id(), child.id());
    });

    assertThat(provider.get(parent.id()).parentId()).isNull();
    assertThat(provider.get(child.id()).parentId()).isEqualTo(parent.id());
  }

  @Test
  void genericProviderDuplicateSlugUpdateRollsBackTheStoredCategory() {
    var first = provider.create(create("First", "first", null));
    provider.create(create("Second", "second", null));

    assertThatThrownBy(() ->
      provider.replace(first.id(), new AdminCategoryUpdateCommand(null, "Changed", "second", false))
    ).isInstanceOfSatisfying(AdminCategoryException.class, failure -> {
      assertThat(failure.code()).isEqualTo("DUPLICATE_CATEGORY_SLUG");
      assertThat(failure.messageArgs()).containsExactly("second");
    });

    var unchanged = provider.get(first.id());
    assertThat(unchanged.name()).isEqualTo("First");
    assertThat(unchanged.slug()).isEqualTo("first");
    assertThat(unchanged.active()).isTrue();
  }

  @Test
  void genericProviderInvalidReorderTargetRollsBackSiblingOrdering() {
    var parent = provider.create(create("Parent", "parent", null));
    var first = provider.create(create("First", "first", parent.id()));
    var second = provider.create(create("Second", "second", parent.id()));

    assertThatThrownBy(() ->
      provider.reorder(
        first.id(),
        new AdminCategoryReorderCommand(parent.id(), SparseOrderTarget.from(999_999L, null, null))
      )
    ).isInstanceOfSatisfying(AdminCategoryException.class, failure ->
      assertThat(failure.code()).isEqualTo("INVALID_CATEGORY_PLACEMENT_TARGET")
    );

    assertThat(childSlugs(parent.id())).containsExactly("first", "second");
    assertThat(provider.get(first.id()).parentId()).isEqualTo(parent.id());
    assertThat(provider.get(second.id()).parentId()).isEqualTo(parent.id());
  }

  @Test
  void genericProviderPersistsHierarchyLifecycleAndDeletionInCatalogueOwnedTables() {
    var parent = provider.create(create("Parent", "parent", null));
    var child = provider.create(create("Child", "child", parent.id()));

    assertThat(countRows("catalogue_category")).isEqualTo(2);
    assertThat(countRows("catalogue_category_closure")).isEqualTo(3);
    assertThat(parentId(child.id())).isEqualTo(parent.id());
    assertThat(closureDepth(parent.id(), child.id())).isEqualTo(1);
    assertThat(sortRank(child.id())).isPositive();

    provider.deactivate(child.id());
    assertThat(active(child.id())).isFalse();
    assertThat(parentId(child.id())).isEqualTo(parent.id());

    provider.move(child.id(), new AdminCategoryMoveCommand(null, SparseOrderTarget.append()));
    assertThat(parentId(child.id())).isNull();
    assertThat(closureDepth(parent.id(), child.id())).isNull();
    assertThat(closureDepth(child.id(), child.id())).isZero();

    provider.activate(child.id());
    assertThat(active(child.id())).isTrue();
    provider.delete(child.id());

    assertThat(categoryRowCount(child.id())).isZero();
    assertThat(closureRowCount(child.id())).isZero();
    assertThat(categoryRowCount(parent.id())).isEqualTo(1);
  }

  @Test
  void genericProviderJoinsOuterTransactionAndRollsBackCategoryAndClosureWrites() {
    var parent = provider.create(create("Parent", "parent", null));
    long categoriesBefore = countRows("catalogue_category");
    long pathsBefore = countRows("catalogue_category_closure");

    assertThatThrownBy(() ->
      transactions.executeWithoutResult(ignored -> {
        var created = provider.create(create("Rolled Back", "rolled-back", parent.id()));
        assertThat(categoryRowCount(created.id())).isEqualTo(1);
        assertThat(closureDepth(parent.id(), created.id())).isEqualTo(1);
        throw new IllegalStateException("forced outer rollback");
      })
    )
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("forced outer rollback");

    assertThat(countRows("catalogue_category")).isEqualTo(categoriesBefore);
    assertThat(countRows("catalogue_category_closure")).isEqualTo(pathsBefore);
    assertThat(slugRowCount("rolled-back")).isZero();
    assertThat(provider.get(parent.id()).slug()).isEqualTo("parent");
  }

  @Test
  void genericProviderMissingParentDecisionsUseStableErrorsAndPreserveStoredState() {
    long missingParentId = 999_999L;
    var existing = provider.create(create("Existing", "existing", null));

    assertGenericError(
      () -> provider.create(create("Missing Parent", "missing-parent", missingParentId)),
      "CATEGORY_PARENT_NOT_FOUND",
      HttpStatus.NOT_FOUND,
      "category.application.parent.not-found",
      "category parent was not found",
      missingParentId
    );
    assertGenericError(
      () ->
        provider.replace(
          existing.id(),
          new AdminCategoryUpdateCommand(missingParentId, "Changed", "changed", false)
        ),
      "CATEGORY_PARENT_NOT_FOUND",
      HttpStatus.NOT_FOUND,
      "category.application.parent.not-found",
      "category parent was not found",
      missingParentId
    );
    assertGenericError(
      () ->
        provider.patch(
          existing.id(),
          new AdminCategoryPatchCommand(
            missingParentId,
            true,
            null,
            false,
            null,
            false,
            null,
            false
          )
        ),
      "CATEGORY_PARENT_NOT_FOUND",
      HttpStatus.NOT_FOUND,
      "category.application.parent.not-found",
      "category parent was not found",
      missingParentId
    );
    assertGenericError(
      () ->
        provider.move(
          existing.id(),
          new AdminCategoryMoveCommand(missingParentId, SparseOrderTarget.append())
        ),
      "CATEGORY_PARENT_NOT_FOUND",
      HttpStatus.NOT_FOUND,
      "category.application.parent.not-found",
      "category parent was not found",
      missingParentId
    );
    assertGenericError(
      () ->
        provider.reorder(
          existing.id(),
          new AdminCategoryReorderCommand(missingParentId, SparseOrderTarget.append())
        ),
      "CATEGORY_PARENT_NOT_FOUND",
      HttpStatus.NOT_FOUND,
      "category.application.parent.not-found",
      "category parent was not found",
      missingParentId
    );

    assertThat(slugRowCount("missing-parent")).isZero();
    assertThat(categoryRowCount(existing.id())).isEqualTo(1);
    var unchanged = provider.get(existing.id());
    assertThat(unchanged.parentId()).isNull();
    assertThat(unchanged.name()).isEqualTo("Existing");
    assertThat(unchanged.slug()).isEqualTo("existing");
    assertThat(unchanged.active()).isTrue();
  }

  @Test
  void genericProviderMissingCategoryDecisionsUseStableErrorAcrossAllMutations() {
    long missingCategoryId = 999_999L;
    AdminCategoryUpdateCommand update = new AdminCategoryUpdateCommand(
      null,
      "Missing",
      "missing",
      true
    );
    AdminCategoryPatchCommand patch = new AdminCategoryPatchCommand(
      null,
      false,
      null,
      false,
      null,
      false,
      null,
      false
    );

    List<ThrowingCallable> missingCategoryMutations = List.of(
      () -> provider.replace(missingCategoryId, update),
      () -> provider.patch(missingCategoryId, patch),
      () ->
        provider.move(
          missingCategoryId,
          new AdminCategoryMoveCommand(null, SparseOrderTarget.append())
        ),
      () ->
        provider.reorder(
          missingCategoryId,
          new AdminCategoryReorderCommand(null, SparseOrderTarget.append())
        ),
      () -> provider.activate(missingCategoryId),
      () -> provider.deactivate(missingCategoryId),
      () -> provider.delete(missingCategoryId)
    );

    missingCategoryMutations.forEach(action ->
      assertGenericError(
        action,
        "CATEGORY_NOT_FOUND",
        HttpStatus.NOT_FOUND,
        "category.application.category.not-found",
        "category was not found"
      )
    );

    assertThat(countRows("catalogue_category")).isZero();
    assertThat(countRows("catalogue_category_closure")).isZero();
  }

  @Test
  void genericProviderAssignedProductDeleteGuardUsesStableErrorAndPreservesRows() {
    var assignedCategory = provider.create(create("CPUs", "cpus", null));
    Long brandId = insertBrand("AMD", "amd");
    Long manufacturerId = insertManufacturer("AMD", "amd-manufacturer");
    Long productId = insertProduct(
      "Ryzen 7 7800X3D",
      "ryzen-7-7800x3d",
      ProductFamily.CPU,
      brandId,
      manufacturerId,
      true
    );
    jdbc.update(
      "INSERT INTO catalogue_product_category (product_id, category_id) VALUES (?, ?)",
      productId,
      assignedCategory.id()
    );

    assertGenericError(
      () -> provider.delete(assignedCategory.id()),
      "CATEGORY_DELETE_BLOCKED",
      HttpStatus.CONFLICT,
      "category.application.delete.blocked",
      "category cannot be deleted because dependent data exists",
      assignedCategory.id()
    );

    assertThat(categoryRowCount(assignedCategory.id())).isEqualTo(1);
    assertThat(closureDepth(assignedCategory.id(), assignedCategory.id())).isZero();
    assertThat(
      jdbc.queryForObject(
        "SELECT COUNT(*) FROM catalogue_product_category WHERE product_id = ? AND category_id = ?",
        Long.class,
        productId,
        assignedCategory.id()
      )
    ).isEqualTo(1L);
  }

  private static AdminCategoryCreateCommand create(String name, String slug, Long parentId) {
    return new AdminCategoryCreateCommand(parentId, name, slug, true, SparseOrderTarget.append());
  }

  private List<String> childSlugs(Long parentId) {
    return provider
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

  private void assertGenericError(
    ThrowingCallable action,
    String code,
    HttpStatus status,
    String messageKey,
    String message,
    Object... messageArgs
  ) {
    assertThatThrownBy(action).isInstanceOfSatisfying(AdminCategoryException.class, failure -> {
      assertThat(failure.code()).isEqualTo(code);
      assertThat(failure.status()).isEqualTo(status);
      assertThat(failure.messageKey()).isEqualTo(messageKey);
      assertThat(failure).hasMessage(message);
      if (messageArgs.length > 0) {
        assertThat(failure.messageArgs()).containsExactly(messageArgs);
      }
    });
  }

  private Long parentId(Long categoryId) {
    return jdbc.queryForObject(
      "SELECT parent_id FROM catalogue_category WHERE id = ?",
      Long.class,
      categoryId
    );
  }

  private Long sortRank(Long categoryId) {
    return jdbc.queryForObject(
      "SELECT sort_rank FROM catalogue_category WHERE id = ?",
      Long.class,
      categoryId
    );
  }

  private boolean active(Long categoryId) {
    return Boolean.TRUE.equals(
      jdbc.queryForObject(
        "SELECT is_active FROM catalogue_category WHERE id = ?",
        Boolean.class,
        categoryId
      )
    );
  }

  private Integer closureDepth(Long ancestorId, Long descendantId) {
    return jdbc.query(
      "SELECT depth FROM catalogue_category_closure WHERE ancestor_id = ? AND descendant_id = ?",
      result -> result.next() ? result.getInt("depth") : null,
      ancestorId,
      descendantId
    );
  }

  private long categoryRowCount(Long categoryId) {
    return jdbc.queryForObject(
      "SELECT COUNT(*) FROM catalogue_category WHERE id = ?",
      Long.class,
      categoryId
    );
  }

  private long closureRowCount(Long categoryId) {
    return jdbc.queryForObject(
      "SELECT COUNT(*) FROM catalogue_category_closure WHERE ancestor_id = ? OR descendant_id = ?",
      Long.class,
      categoryId,
      categoryId
    );
  }

  private long slugRowCount(String slug) {
    return jdbc.queryForObject(
      "SELECT COUNT(*) FROM catalogue_category WHERE slug = ?",
      Long.class,
      slug
    );
  }
}
