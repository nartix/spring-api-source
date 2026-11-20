package com.ferozfaiz.uniapi.category.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryParentOptionsValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryTreeValidationInput;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminCategoryContextApplicationServiceTest {

  @Test
  void capabilitiesComeOnlyFromTheSelectedDefinition() {
    var fixture = AdminCategoryRuntimeTestFixture.harness();

    var capabilities = fixture.service().capabilities("catalogue");

    assertThat(capabilities).isSameAs(fixture.definition());
    verify(fixture.registry()).findRegistration("catalogue");
    verifyNoInteractions(
      fixture.ports().categoryRead(),
      fixture.ports().hierarchyRead(),
      fixture.ports().basicMutation(),
      fixture.ports().hierarchyMutation(),
      fixture.ports().ordering(),
      fixture.ports().lifecycle()
    );
  }

  @Test
  void contextReadsUseTheirExactSelectedPorts() {
    var fixture = AdminCategoryRuntimeTestFixture.harness();
    var node = node();
    when(fixture.ports().hierarchyRead().tree(org.mockito.ArgumentMatchers.any())).thenReturn(
      new PageResult<>(
        List.of(node),
        0,
        10_000,
        1,
        1,
        true,
        true,
        SortCriteria.of("id", SortDirection.ASC)
      )
    );
    when(fixture.ports().categoryRead().getNode(10L)).thenReturn(node);
    when(fixture.ports().hierarchyRead().breadcrumb(10L)).thenReturn(
      List.of(new AdminCategoryModels.BreadcrumbItem(node.category(), 0))
    );
    when(
      fixture.ports().hierarchyRead().parentOptions(org.mockito.ArgumentMatchers.any())
    ).thenReturn(new AdminCategoryModels.ParentOptionSlice(List.of(), false, null));

    assertThat(
      fixture
        .service()
        .tree(
          "catalogue",
          new AdminCategoryTreeValidationInput(fixture.definition(), null, null, List.of(), null)
        )
    )
      .extracting(PageResult::content)
      .asList()
      .hasSize(1);
    assertThat(fixture.service().getNode("catalogue", 10L)).isSameAs(node);
    assertThat(fixture.service().get("catalogue", 10L)).isSameAs(node.category());
    assertThat(fixture.service().breadcrumb("catalogue", 10L)).hasSize(1);
    assertThat(
      fixture
        .service()
        .parentOptions(
          "catalogue",
          new AdminCategoryParentOptionsValidationInput(
            fixture.definition(),
            null,
            null,
            null,
            List.of(),
            null,
            null
          )
        )
    )
      .extracting(AdminCategoryModels.ParentOptionSlice::data)
      .asList()
      .isEmpty();

    verify(fixture.ports().hierarchyRead()).tree(org.mockito.ArgumentMatchers.any());
    verify(fixture.ports().categoryRead(), org.mockito.Mockito.times(2)).getNode(10L);
    verify(fixture.ports().hierarchyRead()).breadcrumb(10L);
    verify(fixture.ports().hierarchyRead()).parentOptions(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void incompleteTreeResultIsRejectedBeforeReturningToTheController() {
    var fixture = AdminCategoryRuntimeTestFixture.harness();
    when(fixture.ports().hierarchyRead().tree(org.mockito.ArgumentMatchers.any())).thenReturn(
      new PageResult<>(
        List.of(node()),
        0,
        1,
        2,
        2,
        true,
        false,
        SortCriteria.of("id", SortDirection.ASC)
      )
    );

    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
      fixture
        .service()
        .tree(
          "catalogue",
          new AdminCategoryTreeValidationInput(fixture.definition(), 0, 1, List.of("id,asc"), null)
        )
    )
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("category tree is too large");
  }

  private static AdminCategoryModels.CategoryNode node() {
    var timestamp = OffsetDateTime.parse("2026-07-14T00:00:00Z");
    return new AdminCategoryModels.CategoryNode(
      new AdminCategoryModels.Category(
        10L,
        null,
        1_000_000_000L,
        "Storage",
        "storage",
        true,
        timestamp,
        timestamp
      ),
      0,
      false
    );
  }
}
