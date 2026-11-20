package com.ferozfaiz.uniapi.catalogue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.catalogue.application.provider.CatalogueAdminCategoryProvider;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.catalogue.application.query.AdminCatalogueCategoryTreeQuery;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryBreadcrumbItem;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryNode;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOption;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryParentOptionSlice;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategoryTreeNode;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyReadPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryReadPort;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryParentOptionsQuery;
import com.ferozfaiz.uniapi.category.application.query.AdminCategoryTreeQuery;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CatalogueAdminCategoryProviderContextTest {

  @Test
  void treeGetBreadcrumbAndParentOptionsDelegateToCatalogueService() {
    CatalogueCategoryApplicationService service = mock(CatalogueCategoryApplicationService.class);
    CatalogueAdminCategoryProvider provider = new CatalogueAdminCategoryProvider(service);
    assertThat(provider).isInstanceOf(AdminCategoryReadPort.class);
    assertThat(provider).isInstanceOf(AdminCategoryHierarchyReadPort.class);
    AdminCategoryReadPort categoryRead = (AdminCategoryReadPort) provider;
    AdminCategoryHierarchyReadPort hierarchyRead = (AdminCategoryHierarchyReadPort) provider;
    AdminCategoryTreeQuery treeQuery = new AdminCategoryTreeQuery(
      new PageCriteria(0, 100),
      new SortCriteria(List.of(new SortOrder("sortRank", SortDirection.ASC)))
    );
    when(service.findTree(any(AdminCatalogueCategoryTreeQuery.class))).thenReturn(
      new PageResult<>(
        List.of(new CatalogueCategoryTreeNode(category(), 0, false)),
        0,
        100,
        1,
        1,
        true,
        true,
        treeQuery.sort()
      )
    );
    when(service.get(10L)).thenReturn(category());
    when(service.findBreadcrumb(10L)).thenReturn(
      List.of(new CatalogueCategoryBreadcrumbItem(category(), 0))
    );
    when(service.findParentOptions(any(AdminCatalogueCategoryParentOptionsQuery.class))).thenReturn(
      new CatalogueCategoryParentOptionSlice(
        List.of(
          new CatalogueCategoryParentOption(
            category(),
            0,
            "Storage",
            List.of(new CatalogueCategoryBreadcrumbItem(category(), 0)),
            true,
            null
          )
        ),
        false,
        null
      )
    );

    var tree = hierarchyRead.tree(treeQuery);
    var detail = categoryRead.get(10L);
    var breadcrumb = hierarchyRead.breadcrumb(10L);
    var options = hierarchyRead.parentOptions(
      new AdminCategoryParentOptionsQuery("stor", 25, null, List.of(10L), 99L, true)
    );

    ArgumentCaptor<AdminCatalogueCategoryTreeQuery> treeCaptor = ArgumentCaptor.forClass(
      AdminCatalogueCategoryTreeQuery.class
    );
    verify(service).findTree(treeCaptor.capture());
    assertThat(treeCaptor.getValue().page()).isEqualTo(treeQuery.page());
    assertThat(treeCaptor.getValue().sort()).isEqualTo(treeQuery.sort());
    verify(service).get(10L);
    verify(service).findBreadcrumb(10L);
    verify(service).findParentOptions(any(AdminCatalogueCategoryParentOptionsQuery.class));
    assertThat(tree.content().getFirst().category().slug()).isEqualTo("storage");
    assertThat(detail.slug()).isEqualTo("storage");
    assertThat(breadcrumb).hasSize(1);
    assertThat(options.data()).hasSize(1);
  }

  private static CatalogueCategory category() {
    return new CatalogueCategory(
      10L,
      null,
      1_000_000_000L,
      "Storage",
      "storage",
      true,
      OffsetDateTime.parse("2026-06-29T00:00:00Z"),
      OffsetDateTime.parse("2026-06-30T00:00:00Z")
    );
  }
}
