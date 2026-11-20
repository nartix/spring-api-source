package com.ferozfaiz.uniapi.catalogue.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.catalogue.application.command.UpsertCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.provider.CatalogueAdminCategoryProvider;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryCreateCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryMoveCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryPatchCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryReorderCommand;
import com.ferozfaiz.uniapi.category.application.command.AdminCategoryUpdateCommand;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryBasicMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryHierarchyMutationPort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryLifecyclePort;
import com.ferozfaiz.uniapi.category.application.provider.AdminCategoryOrderingPort;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class CatalogueAdminCategoryProviderMutationTest {

  @Test
  void directMutationPortsDelegateCommandsToCatalogueService() {
    CatalogueCategoryApplicationService service = mock(CatalogueCategoryApplicationService.class);
    CatalogueAdminCategoryProvider provider = new CatalogueAdminCategoryProvider(service);
    assertThat(provider).isInstanceOf(AdminCategoryBasicMutationPort.class);
    assertThat(provider).isInstanceOf(AdminCategoryHierarchyMutationPort.class);
    assertThat(provider).isInstanceOf(AdminCategoryOrderingPort.class);
    assertThat(provider).isInstanceOf(AdminCategoryLifecyclePort.class);
    AdminCategoryBasicMutationPort basicMutation = (AdminCategoryBasicMutationPort) provider;
    AdminCategoryHierarchyMutationPort hierarchyMutation =
      (AdminCategoryHierarchyMutationPort) provider;
    AdminCategoryOrderingPort ordering = (AdminCategoryOrderingPort) provider;
    AdminCategoryLifecyclePort lifecycle = (AdminCategoryLifecyclePort) provider;
    when(service.create(any(UpsertCatalogueCategoryCommand.class))).thenReturn(category());
    when(service.update(any(UpsertCatalogueCategoryCommand.class))).thenReturn(category());
    when(service.get(10L)).thenReturn(category());
    when(service.reorder(any(), any())).thenReturn(category());

    assertThat(basicMutation.create(createCommand()).slug()).isEqualTo("storage");
    assertThat(basicMutation.replace(10L, updateCommand()).slug()).isEqualTo("storage");
    assertThat(basicMutation.patch(10L, patchCommand()).slug()).isEqualTo("storage");
    hierarchyMutation.move(10L, new AdminCategoryMoveCommand(null, SparseOrderTarget.append()));
    assertThat(
      ordering
        .reorder(10L, new AdminCategoryReorderCommand(null, SparseOrderTarget.append()))
        .slug()
    ).isEqualTo("storage");
    lifecycle.activate(10L);
    lifecycle.deactivate(10L);
    basicMutation.delete(10L);

    verify(service).create(any(UpsertCatalogueCategoryCommand.class));
    verify(service).get(10L);
    verify(service, times(2)).update(any(UpsertCatalogueCategoryCommand.class));
    verify(service).move(10L, null, SparseOrderTarget.append());
    verify(service).activate(10L);
    verify(service).deactivate(10L);
    verify(service).delete(10L);
  }

  @Test
  void mapsDuplicateSlugToGenericErrorCode() {
    CatalogueCategoryApplicationService service = mock(CatalogueCategoryApplicationService.class);
    CatalogueAdminCategoryProvider provider = new CatalogueAdminCategoryProvider(service);
    when(service.create(any(UpsertCatalogueCategoryCommand.class))).thenThrow(
      DuplicateCatalogueSlugException.category("storage")
    );

    assertThatThrownBy(() -> provider.create(createCommand()))
      .isInstanceOf(AdminCategoryException.class)
      .extracting("code")
      .isEqualTo("DUPLICATE_CATEGORY_SLUG");
  }

  private static AdminCategoryCreateCommand createCommand() {
    return new AdminCategoryCreateCommand(
      null,
      "Storage",
      "storage",
      true,
      SparseOrderTarget.append()
    );
  }

  private static AdminCategoryUpdateCommand updateCommand() {
    return new AdminCategoryUpdateCommand(null, "Storage", "storage", true);
  }

  private static AdminCategoryPatchCommand patchCommand() {
    return new AdminCategoryPatchCommand(null, false, "Storage", true, "storage", true, true, true);
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
