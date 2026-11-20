package com.ferozfaiz.uniapi.category.application.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors.AdminCategoryException;
import org.junit.jupiter.api.Test;

class AdminCategoryTreePolicyTest {

  @Test
  void defaultsMaxTreeReadNodesToSafeDevelopmentValue() {
    AdminCategoryTreePolicy policy = new AdminCategoryTreePolicy();

    assertThat(policy.maxTreeReadNodes()).isEqualTo(10_000);
    assertThat(policy.completeTreePage().page()).isZero();
    assertThat(policy.completeTreePage().size()).isEqualTo(10_000);
  }

  @Test
  void rejectsConfigurationValuesBelowOne() {
    AdminCategoryTreePolicy policy = new AdminCategoryTreePolicy();

    assertThatThrownBy(() -> policy.setMaxTreeReadNodes(0))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("maxTreeReadNodes must be at least 1");
  }

  @Test
  void rejectsTreesAboveConfiguredLimitWithStableErrorCode() {
    AdminCategoryTreePolicy policy = new AdminCategoryTreePolicy();
    policy.setMaxTreeReadNodes(2);

    assertThatThrownBy(() -> policy.enforceCompleteTree(3))
      .isInstanceOf(AdminCategoryException.class)
      .extracting("code")
      .isEqualTo("CATEGORY_TREE_TOO_LARGE");
  }

  @Test
  void rejectsTreesAboveRequestedPageSizeToAvoidPartialTreeResponses() {
    AdminCategoryTreePolicy policy = new AdminCategoryTreePolicy();

    assertThatThrownBy(() -> policy.enforceCompleteTree(2, 1))
      .isInstanceOf(AdminCategoryException.class)
      .extracting("code")
      .isEqualTo("CATEGORY_TREE_TOO_LARGE");
  }

  @Test
  void freezesTheTenThousandNodeBoundaryAndAllowsNonzeroPages() {
    AdminCategoryTreePolicy policy = new AdminCategoryTreePolicy();

    assertThat(policy.resolvePage(7, 10_000).page()).isEqualTo(7);
    assertThat(policy.resolvePage(7, 10_000).size()).isEqualTo(10_000);
    assertThatCode(() -> policy.enforceCompleteTree(10_000)).doesNotThrowAnyException();
    assertThatCode(() -> policy.enforceCompleteTree(10_000, 10_000)).doesNotThrowAnyException();

    assertThatThrownBy(() -> policy.resolvePage(7, 10_001))
      .isInstanceOf(AdminCategoryException.class)
      .extracting("code")
      .isEqualTo("CATEGORY_TREE_TOO_LARGE");
    assertThatThrownBy(() -> policy.enforceCompleteTree(10_001))
      .isInstanceOf(AdminCategoryException.class)
      .extracting("code")
      .isEqualTo("CATEGORY_TREE_TOO_LARGE");
  }
}
