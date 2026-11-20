package com.ferozfaiz.uniapi.category.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistration;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class AdminCategoryApplicationServiceTest {

  @Test
  void listRejectsUnknownRegistrationBeforeAnyPortLookup() {
    var fixture = AdminCategoryRuntimeTestFixture.harness();
    when(fixture.registry().findRegistration("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> fixture.service().list(" unknown ", listInput(fixture)))
      .isInstanceOf(AdminCategoryException.class)
      .extracting("code")
      .isEqualTo("UNKNOWN_CATEGORY_SYSTEM");

    verify(fixture.registry()).findRegistration("unknown");
    verifyNoInteractions(fixture.ports().categoryRead());
  }

  @Test
  void listUsesOnlyTheSelectedRegistrationReadPort() {
    var fixture = AdminCategoryRuntimeTestFixture.harness();
    var page = page();
    when(fixture.ports().categoryRead().list(org.mockito.ArgumentMatchers.any())).thenReturn(page);

    var result = fixture.service().list("catalogue", listInput(fixture));

    assertThat(result).isSameAs(page);
    verify(fixture.ports().categoryRead()).list(org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(
      fixture.ports().hierarchyRead(),
      fixture.ports().basicMutation(),
      fixture.ports().hierarchyMutation(),
      fixture.ports().ordering(),
      fixture.ports().lifecycle()
    );
  }

  @Test
  void listResolvesPoliciesOnlyFromTheSelectedRuntimeBinding() {
    var fixture = AdminCategoryRuntimeTestFixture.harness();
    var registration = mock(AdminCategorySystemRegistration.class);
    when(registration.definition()).thenReturn(Optional.of(fixture.definition()));
    when(registration.runtimeBinding()).thenReturn(fixture.registration().runtimeBinding());
    when(fixture.registry().findRegistration("catalogue")).thenReturn(Optional.of(registration));
    when(fixture.ports().categoryRead().list(org.mockito.ArgumentMatchers.any())).thenReturn(
      page()
    );

    assertThat(fixture.service().list("catalogue", listInput(fixture))).isEqualTo(page());

    verify(registration).runtimeBinding();
    verifyNoInteractions(fixture.ports().hierarchyRead());
  }

  @Test
  void selectedPortFailureIsPropagatedWithoutFallback() {
    var fixture = AdminCategoryRuntimeTestFixture.harness();
    var failure = new IllegalStateException("selected provider failed");
    when(fixture.ports().categoryRead().list(org.mockito.ArgumentMatchers.any())).thenThrow(
      failure
    );

    assertThatThrownBy(() -> fixture.service().list("catalogue", listInput(fixture))).isSameAs(
      failure
    );
    verify(fixture.ports().categoryRead()).list(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void listRetainsReadOnlyMethodTransactionMetadataUnderTheClassWriteBoundary()
    throws NoSuchMethodException {
    Transactional classBoundary = AdminCategoryApplicationService.class.getAnnotation(
      Transactional.class
    );
    Method list = AdminCategoryApplicationService.class.getMethod(
      "list",
      String.class,
      AdminCategoryListValidationInput.class
    );
    Transactional listBoundary = list.getAnnotation(Transactional.class);

    assertThat(classBoundary).isNotNull();
    assertThat(classBoundary.readOnly()).isFalse();
    assertThat(listBoundary).isNotNull();
    assertThat(listBoundary.readOnly()).isTrue();
    assertThat(AdminCategoryApplicationService.class.getConstructors())
      .singleElement()
      .satisfies(constructor ->
        assertThat(constructor.getParameterTypes()).containsExactly(
          com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry.class
        )
      );
  }

  private static AdminCategoryListValidationInput listInput(
    AdminCategoryRuntimeTestFixture.Harness fixture
  ) {
    return new AdminCategoryListValidationInput(
      fixture.definition(),
      null,
      null,
      null,
      null,
      null,
      null,
      List.of(),
      null
    );
  }

  private static PageResult<AdminCategoryModels.CategoryNode> page() {
    var timestamp = OffsetDateTime.parse("2026-07-14T00:00:00Z");
    return new PageResult<>(
      List.of(
        new AdminCategoryModels.CategoryNode(
          new AdminCategoryModels.Category(
            1L,
            null,
            null,
            "Storage",
            "storage",
            true,
            timestamp,
            timestamp
          ),
          0,
          false
        )
      ),
      0,
      20,
      1,
      1,
      true,
      true,
      SortCriteria.of("id", SortDirection.ASC)
    );
  }
}
