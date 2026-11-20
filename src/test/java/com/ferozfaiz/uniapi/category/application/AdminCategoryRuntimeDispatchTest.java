package com.ferozfaiz.uniapi.category.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryCreateValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryParentOptionsValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPatchValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPlacementValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryReplaceValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryTreeValidationInput;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdminCategoryRuntimeDispatchTest {

  @Test
  void allFourteenOperationsUseZeroOrOneExactNarrowPort() {
    var fixture = AdminCategoryRuntimeTestFixture.harness();
    var service = fixture.service();
    var definition = fixture.definition();
    var node = node();
    var page = new PageResult<>(
      List.of(node),
      0,
      20,
      1,
      1,
      true,
      true,
      SortCriteria.of("id", SortDirection.ASC)
    );
    when(fixture.ports().categoryRead().list(org.mockito.ArgumentMatchers.any())).thenReturn(page);
    when(fixture.ports().categoryRead().getNode(1L)).thenReturn(node);
    when(fixture.ports().hierarchyRead().tree(org.mockito.ArgumentMatchers.any())).thenReturn(page);
    when(
      fixture.ports().hierarchyRead().parentOptions(org.mockito.ArgumentMatchers.any())
    ).thenReturn(new AdminCategoryModels.ParentOptionSlice(List.of(), false, null));
    when(fixture.ports().hierarchyRead().breadcrumb(1L)).thenReturn(List.of());
    when(fixture.ports().basicMutation().create(org.mockito.ArgumentMatchers.any())).thenReturn(
      node.category()
    );
    when(
      fixture
        .ports()
        .basicMutation()
        .replace(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any())
    ).thenReturn(node.category());
    when(
      fixture
        .ports()
        .basicMutation()
        .patch(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any())
    ).thenReturn(node.category());
    when(
      fixture
        .ports()
        .ordering()
        .reorder(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any())
    ).thenReturn(node.category());

    service.capabilities("catalogue");
    verifyNoInteractions(
      fixture.ports().categoryRead(),
      fixture.ports().hierarchyRead(),
      fixture.ports().basicMutation(),
      fixture.ports().hierarchyMutation(),
      fixture.ports().ordering(),
      fixture.ports().lifecycle()
    );
    service.list(
      "catalogue",
      new AdminCategoryListValidationInput(
        definition,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        null
      )
    );
    service.tree(
      "catalogue",
      new AdminCategoryTreeValidationInput(definition, null, null, List.of(), null)
    );
    service.parentOptions(
      "catalogue",
      new AdminCategoryParentOptionsValidationInput(
        definition,
        null,
        null,
        null,
        List.of(),
        null,
        null
      )
    );
    service.get("catalogue", 1L);
    service.breadcrumb("catalogue", 1L);
    service.create(
      "catalogue",
      new AdminCategoryCreateValidationInput(
        definition,
        null,
        null,
        null,
        null,
        "Name",
        "name",
        true
      )
    );
    service.update(
      "catalogue",
      1L,
      new AdminCategoryReplaceValidationInput(definition, 1L, null, "Name", "name", true)
    );
    service.patch(
      "catalogue",
      1L,
      new AdminCategoryPatchValidationInput(
        definition,
        1L,
        null,
        false,
        "Updated",
        true,
        null,
        false,
        null,
        false
      )
    );
    service.delete("catalogue", 1L);
    service.move(
      "catalogue",
      1L,
      new AdminCategoryPlacementValidationInput(definition, Operation.MOVE, null, null, null, null)
    );
    service.reorder(
      "catalogue",
      1L,
      new AdminCategoryPlacementValidationInput(
        definition,
        Operation.REORDER,
        null,
        null,
        null,
        null
      )
    );
    service.activate("catalogue", 1L);
    service.deactivate("catalogue", 1L);

    verify(fixture.ports().categoryRead()).list(org.mockito.ArgumentMatchers.any());
    verify(fixture.ports().categoryRead()).getNode(1L);
    verify(fixture.ports().hierarchyRead()).tree(org.mockito.ArgumentMatchers.any());
    verify(fixture.ports().hierarchyRead()).parentOptions(org.mockito.ArgumentMatchers.any());
    verify(fixture.ports().hierarchyRead()).breadcrumb(1L);
    verify(fixture.ports().basicMutation()).create(org.mockito.ArgumentMatchers.any());
    verify(fixture.ports().basicMutation()).replace(
      org.mockito.ArgumentMatchers.eq(1L),
      org.mockito.ArgumentMatchers.any()
    );
    verify(fixture.ports().basicMutation()).patch(
      org.mockito.ArgumentMatchers.eq(1L),
      org.mockito.ArgumentMatchers.any()
    );
    verify(fixture.ports().basicMutation()).delete(1L);
    verify(fixture.ports().hierarchyMutation()).move(
      org.mockito.ArgumentMatchers.eq(1L),
      org.mockito.ArgumentMatchers.any()
    );
    verify(fixture.ports().ordering()).reorder(
      org.mockito.ArgumentMatchers.eq(1L),
      org.mockito.ArgumentMatchers.any()
    );
    verify(fixture.ports().lifecycle()).activate(1L);
    verify(fixture.ports().lifecycle()).deactivate(1L);
    verify(fixture.registry(), times(14)).findRegistration("catalogue");
  }

  @Test
  void unsupportedCapabilityWinsBeforeDefinitionValidationAndPortWork() {
    var definition = AdminCategoryRuntimeTestFixture.completeDefinition(
      "flat-restricted",
      Set.of(Operation.CAPABILITIES, Operation.LIST, Operation.GET)
    );
    var fixture = AdminCategoryRuntimeTestFixture.harness(definition);

    assertThatThrownBy(() ->
      fixture
        .service()
        .tree(
          "flat-restricted",
          new AdminCategoryTreeValidationInput(definition, -1, -1, List.of("invalid"), "bad")
        )
    )
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("operation is not supported for category system");
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
  void activateOnlyRegistrationNeverReachesItsUndeclaredDeactivateSibling() {
    var definition = AdminCategoryRuntimeTestFixture.completeDefinition(
      "activate-only",
      Set.of(Operation.CAPABILITIES, Operation.ACTIVATE)
    );
    var fixture = AdminCategoryRuntimeTestFixture.harness(definition);

    fixture.service().activate("activate-only", 1L);

    verify(fixture.ports().lifecycle()).activate(1L);
    assertThatThrownBy(() -> fixture.service().deactivate("activate-only", 1L))
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("operation is not supported for category system");
    verify(fixture.ports().lifecycle(), never()).deactivate(1L);
  }

  private static AdminCategoryModels.CategoryNode node() {
    var timestamp = OffsetDateTime.parse("2026-07-14T00:00:00Z");
    return new AdminCategoryModels.CategoryNode(
      new AdminCategoryModels.Category(1L, null, null, "Name", "name", true, timestamp, timestamp),
      0,
      false
    );
  }
}
