package com.ferozfaiz.uniapi.data.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.data.application.command.CreateCategoryCommand;
import com.ferozfaiz.uniapi.data.application.command.MoveCategoryCommand;
import com.ferozfaiz.uniapi.data.application.command.PatchCategoryCommand;
import com.ferozfaiz.uniapi.data.application.command.ReorderCategoryCommand;
import com.ferozfaiz.uniapi.data.application.command.UpdateCategoryCommand;
import com.ferozfaiz.uniapi.data.application.policy.CategoryHierarchyPolicy;
import com.ferozfaiz.uniapi.data.application.policy.CategoryHierarchyPolicyProperties;
import com.ferozfaiz.uniapi.data.application.policy.CategoryListPolicy;
import com.ferozfaiz.uniapi.data.application.policy.CategoryListPolicyProperties;
import com.ferozfaiz.uniapi.data.application.query.ListCategoriesQuery;
import com.ferozfaiz.uniapi.data.domain.exception.CategoryHierarchyPolicyViolationException;
import com.ferozfaiz.uniapi.data.domain.model.Category;
import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveEstimate;
import com.ferozfaiz.uniapi.data.domain.model.CategoryMoveMode;
import com.ferozfaiz.uniapi.data.domain.repository.CategoryRepository;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class CategoryApplicationServiceTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-08T06:00:00Z");

  @Mock
  private CategoryRepository categories;

  private CategoryHierarchyPolicyProperties properties;
  private CategoryListPolicyProperties listProperties;
  private CategoryApplicationService service;

  @BeforeEach
  void setUp() {
    properties = new CategoryHierarchyPolicyProperties();
    listProperties = new CategoryListPolicyProperties();
    service = new CategoryApplicationService(
      categories,
      new CategoryHierarchyPolicy(properties),
      new CategoryListPolicy(listProperties),
      Clock.fixed(NOW.toInstant(), ZoneOffset.UTC)
    );
  }

  @Test
  void findAllRejectsPageSizeAboveConfiguredLimit() {
    listProperties.setMaxPageSize(50);

    assertThatThrownBy(() -> service.findAll(listQuery(51))).isInstanceOf(
      InvalidRequestException.class
    );

    verify(categories, never()).findAll(any(), any());
  }

  @Test
  void findAllAllowsPageSizeAtConfiguredLimit() {
    listProperties.setMaxPageSize(50);

    service.findAll(listQuery(50));

    verify(categories).findAll(new PageCriteria(0, 50), SortCriteria.of("id", SortDirection.ASC));
  }

  @Test
  void findAllAllowsOversizedPageWhenPolicyDisabled() {
    listProperties.setMaxPageSize(50);
    listProperties.setEnforceMaxPageSize(false);

    service.findAll(listQuery(500));

    verify(categories).findAll(new PageCriteria(0, 500), SortCriteria.of("id", SortDirection.ASC));
  }

  @Test
  void createPassesPlacementTargetToRootSave() {
    SparseOrderTarget target = SparseOrderTarget.from(2L, null, null);
    when(categories.saveRoot(any(Category.class), eq(target))).thenReturn(
      category(1L, null, "finance", "Finance")
    );

    service.create(new CreateCategoryCommand(null, "finance", "Finance", null, true, target));

    verify(categories).saveRoot(
      argThat(category -> category.sortRank() == null && category.slug().equals("finance")),
      eq(target)
    );
  }

  @Test
  void updateAndPatchPreserveExistingSortRank() {
    Category existing = rankedCategory(1L, null, 1_000_000_000L, "finance", "Finance");
    when(categories.findById(1L)).thenReturn(Optional.of(existing));
    when(categories.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Category updated = service.update(
      1L,
      new UpdateCategoryCommand("finance-updated", "Finance Updated", "Updated", true)
    );
    Category patched = service.patch(
      1L,
      new PatchCategoryCommand(null, "Finance Patched", null, null)
    );

    assertThat(updated.sortRank()).isEqualTo(1_000_000_000L);
    assertThat(patched.sortRank()).isEqualTo(1_000_000_000L);
  }

  @Test
  void reorderUsesExistingParentWhenRequestOmitsParentAndPassesTarget() {
    SparseOrderTarget target = SparseOrderTarget.from(null, 2L, null);
    Category existing = rankedCategory(1L, 10L, 1_000_000_000L, "finance", "Finance");
    when(categories.findById(1L)).thenReturn(
      Optional.of(existing),
      Optional.of(existing),
      Optional.of(rankedCategory(1L, 10L, 2_000_000_000L, "finance", "Finance"))
    );
    when(categories.findById(10L)).thenReturn(
      Optional.of(rankedCategory(10L, null, 1_000_000_000L, "root", "Root"))
    );
    when(categories.estimateMove(1L, 10L)).thenReturn(
      new CategoryMoveEstimate(1, 1, 1, 1, 1, 2, 0, 0, 1, false)
    );

    Category reordered = service.reorder(1L, new ReorderCategoryCommand(null, target));

    assertThat(reordered.sortRank()).isEqualTo(2_000_000_000L);
    verify(categories).move(
      eq(1L),
      eq(10L),
      eq(CategoryMoveMode.LEAF_ONLY),
      eq(hierarchyPolicyTimeout()),
      eq(target)
    );
  }

  @Test
  void moveRejectsLeafOnlyModeWhenCategoryHasDescendants() {
    when(categories.findById(1L)).thenReturn(Optional.of(category(1L, null, "finance", "Finance")));
    when(categories.findById(2L)).thenReturn(
      Optional.of(category(2L, null, "operations", "Operations"))
    );
    when(categories.estimateMove(1L, 2L)).thenReturn(
      new CategoryMoveEstimate(2, 0, 1, 0, 2, 2, 1, 0, 2, true)
    );

    assertThatThrownBy(() ->
      service.move(1L, new MoveCategoryCommand(2L, CategoryMoveMode.LEAF_ONLY))
    ).isInstanceOf(CategoryHierarchyPolicyViolationException.class);

    verify(categories, never()).move(eq(1L), eq(2L), eq(CategoryMoveMode.LEAF_ONLY), any());
  }

  @Test
  void moveRejectsSynchronousOperationWhenEstimatedRowsExceedPolicy() {
    when(categories.findById(1L)).thenReturn(Optional.of(category(1L, null, "finance", "Finance")));
    when(categories.findById(2L)).thenReturn(
      Optional.of(category(2L, null, "operations", "Operations"))
    );
    when(categories.estimateMove(1L, 2L)).thenReturn(
      new CategoryMoveEstimate(10, 10, 10, 50_001, 50_000, 100_001, 1, 0, 2, true)
    );

    assertThatThrownBy(() ->
      service.move(1L, new MoveCategoryCommand(2L, CategoryMoveMode.SUBTREE))
    ).isInstanceOf(CategoryHierarchyPolicyViolationException.class);

    verify(categories, never()).move(eq(1L), eq(2L), eq(CategoryMoveMode.SUBTREE), any());
  }

  @Test
  void findTreeRejectsResponsesAboveReadPolicy() {
    properties.setMaxTreeReadNodes(1);
    when(categories.count()).thenReturn(2L);

    assertThatThrownBy(() -> service.findTree()).isInstanceOf(
      CategoryHierarchyPolicyViolationException.class
    );

    verify(categories, never()).findAllForTree();
  }

  @Test
  void readMethodsDeclareCategoryReadPolicyAuthorization() throws Exception {
    assertPreAuthorize(
      "findById",
      new Class<?>[] { Long.class },
      "@categoryReadAuthorizationPolicy.canRead(authentication, #id)"
    );
    assertPreAuthorize(
      "findRoots",
      new Class<?>[] {},
      "@categoryReadAuthorizationPolicy.canRead(authentication)"
    );
    assertPreAuthorize(
      "findAll",
      new Class<?>[] { ListCategoriesQuery.class },
      "@categoryReadAuthorizationPolicy.canRead(authentication)"
    );
    assertPreAuthorize(
      "findChildren",
      new Class<?>[] { Long.class },
      "@categoryReadAuthorizationPolicy.canRead(authentication, #parentId)"
    );
    assertPreAuthorize(
      "findAncestors",
      new Class<?>[] { Long.class },
      "@categoryReadAuthorizationPolicy.canRead(authentication, #id)"
    );
    assertPreAuthorize(
      "findDescendants",
      new Class<?>[] { Long.class },
      "@categoryReadAuthorizationPolicy.canRead(authentication, #id)"
    );
    assertPreAuthorize(
      "findTree",
      new Class<?>[] {},
      "@categoryReadAuthorizationPolicy.canRead(authentication)"
    );
    assertPreAuthorize(
      "findTree",
      new Class<?>[] { Long.class },
      "@categoryReadAuthorizationPolicy.canRead(authentication, #id)"
    );
  }

  @Test
  void writeOnlyEstimateMoveKeepsWritePolicySemantics() throws Exception {
    Method method = CategoryApplicationService.class.getDeclaredMethod(
      "estimateMove",
      Long.class,
      MoveCategoryCommand.class
    );

    assertThat(method.getAnnotation(PreAuthorize.class)).isNull();
  }

  private static void assertPreAuthorize(
    String methodName,
    Class<?>[] parameterTypes,
    String expectedExpression
  ) throws NoSuchMethodException {
    Method method = CategoryApplicationService.class.getDeclaredMethod(methodName, parameterTypes);

    assertThat(method.getAnnotation(PreAuthorize.class))
      .extracting(PreAuthorize::value)
      .isEqualTo(expectedExpression);
  }

  private static Category category(Long id, Long parentId, String slug, String name) {
    return new Category(id, parentId, slug, name, null, true, NOW, NOW, "test", "test");
  }

  private static Category rankedCategory(
    Long id,
    Long parentId,
    Long sortRank,
    String slug,
    String name
  ) {
    return new Category(id, parentId, sortRank, slug, name, null, true, NOW, NOW, "test", "test");
  }

  private java.time.Duration hierarchyPolicyTimeout() {
    return properties.getSyncStatementTimeout();
  }

  private static ListCategoriesQuery listQuery(int size) {
    return new ListCategoriesQuery(
      new PageCriteria(0, size),
      SortCriteria.of("id", SortDirection.ASC)
    );
  }
}
