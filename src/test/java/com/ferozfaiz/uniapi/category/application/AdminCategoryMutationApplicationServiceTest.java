package com.ferozfaiz.uniapi.category.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryCreateValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPatchValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPlacementValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryReplaceValidationInput;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminCategoryMutationApplicationServiceTest {

  @Test
  void allMutationsUseOnlyTheirSelectedNarrowPort() {
    var fixture = AdminCategoryRuntimeTestFixture.harness();
    var category = category();
    when(fixture.ports().basicMutation().create(org.mockito.ArgumentMatchers.any())).thenReturn(
      category
    );
    when(
      fixture
        .ports()
        .basicMutation()
        .replace(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any())
    ).thenReturn(category);
    when(
      fixture
        .ports()
        .basicMutation()
        .patch(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any())
    ).thenReturn(category);
    when(
      fixture
        .ports()
        .ordering()
        .reorder(org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any())
    ).thenReturn(category);

    assertThat(fixture.service().create("catalogue", create(fixture))).isSameAs(category);
    assertThat(fixture.service().update("catalogue", 10L, replace(fixture))).isSameAs(category);
    assertThat(fixture.service().patch("catalogue", 10L, patch(fixture))).isSameAs(category);
    fixture.service().delete("catalogue", 10L);
    fixture.service().move("catalogue", 10L, placement(fixture, Operation.MOVE));
    assertThat(
      fixture.service().reorder("catalogue", 10L, placement(fixture, Operation.REORDER))
    ).isSameAs(category);
    fixture.service().activate("catalogue", 10L);
    fixture.service().deactivate("catalogue", 10L);

    verify(fixture.ports().basicMutation()).create(org.mockito.ArgumentMatchers.any());
    verify(fixture.ports().basicMutation()).replace(
      org.mockito.ArgumentMatchers.eq(10L),
      org.mockito.ArgumentMatchers.any()
    );
    verify(fixture.ports().basicMutation()).patch(
      org.mockito.ArgumentMatchers.eq(10L),
      org.mockito.ArgumentMatchers.any()
    );
    verify(fixture.ports().basicMutation()).delete(10L);
    verify(fixture.ports().hierarchyMutation()).move(
      org.mockito.ArgumentMatchers.eq(10L),
      org.mockito.ArgumentMatchers.any()
    );
    verify(fixture.ports().ordering()).reorder(
      org.mockito.ArgumentMatchers.eq(10L),
      org.mockito.ArgumentMatchers.any()
    );
    verify(fixture.ports().lifecycle()).activate(10L);
    verify(fixture.ports().lifecycle()).deactivate(10L);
    verifyNoInteractions(fixture.ports().categoryRead(), fixture.ports().hierarchyRead());
  }

  @Test
  void unknownMutationNeverTouchesAnyPort() {
    var fixture = AdminCategoryRuntimeTestFixture.harness();
    when(fixture.registry().findRegistration("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> fixture.service().create("unknown", create(fixture)))
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("category system was not found");
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
  void selectedMutationFailureDoesNotFallBackOrEmitAnotherPortCall() {
    var fixture = AdminCategoryRuntimeTestFixture.harness();
    var failure = new IllegalStateException("domain failure");
    when(fixture.ports().basicMutation().create(org.mockito.ArgumentMatchers.any())).thenThrow(
      failure
    );

    assertThatThrownBy(() -> fixture.service().create("catalogue", create(fixture))).isSameAs(
      failure
    );
    verify(fixture.ports().basicMutation()).create(org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(
      fixture.ports().hierarchyMutation(),
      fixture.ports().ordering(),
      fixture.ports().lifecycle()
    );
  }

  private static AdminCategoryCreateValidationInput create(
    AdminCategoryRuntimeTestFixture.Harness fixture
  ) {
    return new AdminCategoryCreateValidationInput(
      fixture.definition(),
      null,
      null,
      null,
      null,
      "Storage",
      "storage",
      true
    );
  }

  private static AdminCategoryReplaceValidationInput replace(
    AdminCategoryRuntimeTestFixture.Harness fixture
  ) {
    return new AdminCategoryReplaceValidationInput(
      fixture.definition(),
      10L,
      null,
      "Storage",
      "storage",
      true
    );
  }

  private static AdminCategoryPatchValidationInput patch(
    AdminCategoryRuntimeTestFixture.Harness fixture
  ) {
    return new AdminCategoryPatchValidationInput(
      fixture.definition(),
      10L,
      null,
      false,
      "Updated",
      true,
      null,
      false,
      null,
      false
    );
  }

  private static AdminCategoryPlacementValidationInput placement(
    AdminCategoryRuntimeTestFixture.Harness fixture,
    Operation operation
  ) {
    return new AdminCategoryPlacementValidationInput(
      fixture.definition(),
      operation,
      null,
      null,
      null,
      null
    );
  }

  private static AdminCategoryModels.Category category() {
    var timestamp = OffsetDateTime.parse("2026-07-14T00:00:00Z");
    return new AdminCategoryModels.Category(
      10L,
      null,
      1_000_000_000L,
      "Storage",
      "storage",
      true,
      timestamp,
      timestamp
    );
  }
}
