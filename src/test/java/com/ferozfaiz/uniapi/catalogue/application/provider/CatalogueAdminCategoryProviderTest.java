package com.ferozfaiz.uniapi.catalogue.application.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryFilters;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminListCatalogueCategoriesQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.CategoryParentScope;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryNode;
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
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogueAdminCategoryProviderTest {

  @Test
  void implementsExactlyTheSixNarrowPortsWithTheFinalMethodSurface() {
    CatalogueCategoryApplicationService service = mock(CatalogueCategoryApplicationService.class);
    CatalogueAdminCategoryProvider provider = new CatalogueAdminCategoryProvider(service);

    assertThat(provider.getClass().getInterfaces()).containsExactlyInAnyOrder(
      AdminCategoryReadPort.class,
      AdminCategoryHierarchyReadPort.class,
      AdminCategoryBasicMutationPort.class,
      AdminCategoryHierarchyMutationPort.class,
      AdminCategoryOrderingPort.class,
      AdminCategoryLifecyclePort.class
    );
    assertThat(
      Arrays.stream(provider.getClass().getDeclaredMethods()).map(Method::getName).toList()
    )
      .contains("replace")
      .doesNotContain("systemKey", "update");
  }

  @Test
  void listTranslatesGenericQueryAndMapsCategoryNodes() {
    CatalogueCategoryApplicationService service = mock(CatalogueCategoryApplicationService.class);
    CatalogueAdminCategoryProvider provider = new CatalogueAdminCategoryProvider(service);
    AdminCategoryListQuery query = query();
    when(service.findAllNodes(any(AdminListCatalogueCategoriesQuery.class))).thenReturn(
      pageOf(
        new CatalogueCategoryNode(
          new CatalogueCategory(
            9L,
            4L,
            1_000_000_000L,
            "Storage",
            "storage",
            true,
            OffsetDateTime.parse("2026-06-29T00:00:00Z"),
            OffsetDateTime.parse("2026-06-30T00:00:00Z")
          ),
          1,
          true
        ),
        query.sort()
      )
    );

    var page = provider.list(query);

    verify(service).findAllNodes(
      new AdminListCatalogueCategoriesQuery(
        CategoryParentScope.children(4L),
        new AdminCatalogueCategoryFilters(true, "stor", "storage"),
        new PageCriteria(1, 50),
        query.sort()
      )
    );
    assertThat(page.content()).hasSize(1);
    assertThat(page.content().getFirst().depth()).isEqualTo(1);
    assertThat(page.content().getFirst().hasChildren()).isTrue();
    assertThat(page.content().getFirst().category().id()).isEqualTo(9L);
    assertThat(page.content().getFirst().category().parentId()).isEqualTo(4L);
    assertThat(page.content().getFirst().category().sortRank()).isEqualTo(1_000_000_000L);
    assertThat(page.content().getFirst().category().name()).isEqualTo("Storage");
    assertThat(page.content().getFirst().category().slug()).isEqualTo("storage");
    assertThat(page.content().getFirst().category().active()).isTrue();
    assertThat(page.content().getFirst().category().createdAt()).isEqualTo(
      OffsetDateTime.parse("2026-06-29T00:00:00Z")
    );
    assertThat(page.content().getFirst().category().updatedAt()).isEqualTo(
      OffsetDateTime.parse("2026-06-30T00:00:00Z")
    );
    assertThat(page.page()).isEqualTo(1);
    assertThat(page.size()).isEqualTo(50);
    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(page.totalPages()).isEqualTo(1);
    assertThat(page.first()).isTrue();
    assertThat(page.last()).isTrue();
    assertThat(page.sort()).isSameAs(query.sort());
  }

  @Test
  void listTranslatesAllRootAndChildrenParentScopesExactly() {
    CatalogueCategoryApplicationService service = mock(CatalogueCategoryApplicationService.class);
    CatalogueAdminCategoryProvider provider = new CatalogueAdminCategoryProvider(service);
    when(service.findAllNodes(any(AdminListCatalogueCategoriesQuery.class))).thenAnswer(
      invocation -> {
        AdminListCatalogueCategoriesQuery query = invocation.getArgument(0);
        return new PageResult<CatalogueCategoryNode>(
          List.of(),
          1,
          50,
          0,
          0,
          false,
          true,
          query.sort()
        );
      }
    );

    provider.list(query(AdminCategoryParentScope.all()));
    provider.list(query(AdminCategoryParentScope.roots()));
    provider.list(query(AdminCategoryParentScope.children(4L)));

    verify(service).findAllNodes(catalogueQuery(CategoryParentScope.all()));
    verify(service).findAllNodes(catalogueQuery(CategoryParentScope.roots()));
    verify(service).findAllNodes(catalogueQuery(CategoryParentScope.children(4L)));
  }

  @Test
  void listMapsMissingChildrenParentToGenericParentNotFoundError() {
    CatalogueCategoryApplicationService service = mock(CatalogueCategoryApplicationService.class);
    CatalogueAdminCategoryProvider provider = new CatalogueAdminCategoryProvider(service);
    when(service.findAllNodes(any(AdminListCatalogueCategoriesQuery.class))).thenThrow(
      CatalogueNotFoundException.category("4")
    );

    assertThatThrownBy(() -> provider.list(query())).isInstanceOfSatisfying(
      AdminCategoryException.class,
      failure -> {
        assertThat(failure.code()).isEqualTo("CATEGORY_PARENT_NOT_FOUND");
        assertThat(failure.status()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
        assertThat(failure.messageKey()).isEqualTo("category.application.parent.not-found");
        assertThat(failure.messageArgs()).containsExactly(4L);
      }
    );
  }

  @Test
  void listMapsNonChildrenLookupFailuresToExactGenericCategoryNotFoundError() {
    CatalogueCategoryApplicationService service = mock(CatalogueCategoryApplicationService.class);
    CatalogueAdminCategoryProvider provider = new CatalogueAdminCategoryProvider(service);
    when(service.findAllNodes(any(AdminListCatalogueCategoriesQuery.class))).thenThrow(
      CatalogueNotFoundException.category("404")
    );

    assertThatThrownBy(() ->
      provider.list(query(AdminCategoryParentScope.all()))
    ).isInstanceOfSatisfying(AdminCategoryException.class, failure -> {
      assertThat(failure.code()).isEqualTo("CATEGORY_NOT_FOUND");
      assertThat(failure.status()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
      assertThat(failure.messageKey()).isEqualTo("category.application.category.not-found");
      assertThat(failure.messageArgs()).containsExactly(404L);
    });
  }

  private static AdminCategoryListQuery query() {
    return query(AdminCategoryParentScope.children(4L));
  }

  private static AdminCategoryListQuery query(AdminCategoryParentScope parentScope) {
    return new AdminCategoryListQuery(
      parentScope,
      new AdminCategoryFilters(true, "stor", "storage"),
      new PageCriteria(1, 50),
      new SortCriteria(List.of(new SortOrder("sortRank", SortDirection.ASC)))
    );
  }

  private static AdminListCatalogueCategoriesQuery catalogueQuery(CategoryParentScope parentScope) {
    AdminCategoryListQuery source = query();
    return new AdminListCatalogueCategoriesQuery(
      parentScope,
      new AdminCatalogueCategoryFilters(true, "stor", "storage"),
      source.page(),
      source.sort()
    );
  }

  private static PageResult<CatalogueCategoryNode> pageOf(
    CatalogueCategoryNode node,
    SortCriteria sort
  ) {
    return new PageResult<>(List.of(node), 1, 50, 1, 1, true, true, sort);
  }
}
