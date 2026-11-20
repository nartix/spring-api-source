package com.ferozfaiz.uniapi.category.application.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryParentOptionsValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPlacementValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryTreeValidationInput;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminCategoryDefinitionValidatorTest {

  private final AdminCategoryDefinitionValidator validator = new AdminCategoryDefinitionValidator();
  private final com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition definition =
    AdminCategoryRuntimeTestFixture.completeDefinition();

  @Test
  void listAppliesFrozenDefaultsMaximumsAndStableTieBreak() {
    var defaults = validator.validateList(list(null, null, null, List.of(), null));
    var maximum = validator.validateList(list(0, 1_000, "0", List.of("name,desc"), null));

    assertThat(defaults.page().page()).isZero();
    assertThat(defaults.page().size()).isEqualTo(20);
    assertThat(defaults.sort().orders())
      .extracting(order -> order.property() + ":" + order.direction())
      .containsExactly("updatedAt:DESC", "id:ASC");
    assertThat(maximum.page().size()).isEqualTo(1_000);
    assertThat(maximum.sort().orders())
      .extracting(order -> order.property() + ":" + order.direction())
      .containsExactly("name:DESC", "id:ASC");
  }

  @Test
  void listRejectsBoundsMalformedDuplicateDisallowedAndTooManySorts() {
    assertInvalid(() -> validator.validateList(list(-1, 20, null, List.of(), null)));
    assertInvalid(() -> validator.validateList(list(0, 0, null, List.of(), null)));
    assertInvalid(() -> validator.validateList(list(0, 1_001, null, List.of(), null)));
    assertInvalid(() ->
      validator.validateList(list(0, 20, null, List.of("name,asc", "name,desc"), null))
    );
    assertInvalid(() ->
      validator.validateList(list(0, 20, null, List.of("internalColumn,asc"), null))
    );
    assertInvalid(() ->
      validator.validateList(
        list(
          0,
          20,
          null,
          List.of("name,asc", "slug,asc", "active,asc", "createdAt,asc", "updatedAt,asc", "id,asc"),
          null
        )
      )
    );
  }

  @Test
  void treeUsesTenThousandDefaultAndMaximum() {
    var defaults = validator.validateTree(
      new AdminCategoryTreeValidationInput(definition, null, null, List.of(), null)
    );
    var maximum = validator.validateTree(
      new AdminCategoryTreeValidationInput(definition, 0, 10_000, List.of(), null)
    );

    assertThat(defaults.page().size()).isEqualTo(10_000);
    assertThat(maximum.page().size()).isEqualTo(10_000);
    assertThatThrownBy(() ->
      validator.validateTree(
        new AdminCategoryTreeValidationInput(definition, 0, 10_001, List.of(), null)
      )
    ).isInstanceOf(RuntimeException.class);
  }

  @Test
  void parentOptionsValidatesRawLimitsBeforeNormalization() {
    var defaults = validator.validateParentOptions(parentOptions(null, null, List.of()));
    var maximum = validator.validateParentOptions(
      parentOptions("q".repeat(120), 50, numericStrings(25))
    );

    assertThat(defaults.limit()).isEqualTo(25);
    assertThat(maximum.limit()).isEqualTo(50);
    assertThat(maximum.includeIds()).hasSize(25);
    assertInvalid(() ->
      validator.validateParentOptions(parentOptions(" " + "q".repeat(120), 25, List.of()))
    );
    assertInvalid(() -> validator.validateParentOptions(parentOptions(null, 51, List.of())));
    assertInvalid(() ->
      validator.validateParentOptions(parentOptions(null, 25, numericStrings(26)))
    );
  }

  @Test
  void placementAcceptsOneDeclaredTargetAndRejectsConflictsOrNegativeIndex() {
    assertThat(
      validator
        .validatePlacement(
          new AdminCategoryPlacementValidationInput(definition, Operation.MOVE, 2L, 3L, null, null)
        )
        .beforeId()
    ).isEqualTo(3L);
    assertInvalid(() ->
      validator.validatePlacement(
        new AdminCategoryPlacementValidationInput(definition, Operation.MOVE, 2L, 3L, 4L, null)
      )
    );
    assertInvalid(() ->
      validator.validatePlacement(
        new AdminCategoryPlacementValidationInput(definition, Operation.REORDER, 2L, null, null, -1)
      )
    );
  }

  private AdminCategoryListValidationInput list(
    Integer page,
    Integer size,
    String parentId,
    List<String> sort,
    String direction
  ) {
    return new AdminCategoryListValidationInput(
      definition,
      page,
      size,
      parentId,
      null,
      null,
      null,
      sort,
      direction
    );
  }

  private AdminCategoryParentOptionsValidationInput parentOptions(
    String q,
    Integer limit,
    List<String> includeIds
  ) {
    return new AdminCategoryParentOptionsValidationInput(
      definition,
      q,
      limit,
      null,
      includeIds,
      null,
      null
    );
  }

  private static List<String> numericStrings(int count) {
    return java.util.stream.IntStream.rangeClosed(1, count).mapToObj(String::valueOf).toList();
  }

  private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call).isInstanceOf(InvalidRequestException.class);
  }
}
