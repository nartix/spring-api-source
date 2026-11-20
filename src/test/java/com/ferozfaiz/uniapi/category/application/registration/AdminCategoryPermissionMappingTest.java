package com.ferozfaiz.uniapi.category.application.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.ferozfaiz.uniapi.category.application.registration.AdminCategoryPermissionMapping.Binding;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AdminCategoryPermissionMappingTest {

  @Test
  void storesBindingsInCanonicalOperationCodeOrder() {
    AdminCategoryPermissionMapping mapping =
      AdminCategoryRegistrationFixtures.completePermissionMapping("taxonomic-fixture");

    assertThat(mapping.bindings())
      .extracting(Binding::operation)
      .containsExactly(
        Operation.ACTIVATE,
        Operation.BREADCRUMB,
        Operation.CAPABILITIES,
        Operation.CREATE,
        Operation.DEACTIVATE,
        Operation.DELETE,
        Operation.GET,
        Operation.LIST,
        Operation.MOVE,
        Operation.PARENT_OPTIONS,
        Operation.PATCH,
        Operation.REORDER,
        Operation.REPLACE,
        Operation.TREE
      );
  }

  @Test
  void looksUpPresentAndAbsentOperationBindings() {
    AdminCategoryPermissionMapping complete =
      AdminCategoryRegistrationFixtures.completePermissionMapping("taxonomic-fixture");
    AdminCategoryPermissionMapping partial = new AdminCategoryPermissionMapping(
      List.of(new Binding(Operation.LIST, "admin:taxonomic-fixture-categories:read"))
    );

    assertThat(complete.permissionFor(Operation.LIST)).contains(
      "admin:taxonomic-fixture-categories:read"
    );
    assertThat(complete.permissionFor(Operation.CREATE)).contains(
      "admin:taxonomic-fixture-categories:write"
    );
    assertThat(partial.permissionFor(Operation.GET)).isEmpty();
  }

  @Test
  void equalityAndHashCodeUseTheCanonicalBindingList() {
    List<Binding> reversed = new ArrayList<>(
      AdminCategoryRegistrationFixtures.completePermissionMapping("taxonomic-fixture").bindings()
    );
    Collections.reverse(reversed);

    AdminCategoryPermissionMapping first = new AdminCategoryPermissionMapping(reversed);
    AdminCategoryPermissionMapping second =
      AdminCategoryRegistrationFixtures.completePermissionMapping("taxonomic-fixture");

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }

  @Test
  void snapshotsTheInputAndExposesAnImmutableCanonicalList() {
    List<Binding> source = new ArrayList<>(
      AdminCategoryRegistrationFixtures.completePermissionMapping("taxonomic-fixture").bindings()
    );
    AdminCategoryPermissionMapping mapping = new AdminCategoryPermissionMapping(source);

    source.clear();

    assertThat(mapping.bindings()).hasSize(Operation.values().length);
    assertThatThrownBy(() -> mapping.bindings().clear()).isInstanceOf(
      UnsupportedOperationException.class
    );
  }

  @Test
  void acceptsTheExactRawAuthorityGrammarBoundariesWithoutSemanticFiltering() {
    assertThat(new Binding(Operation.LIST, "A").permission()).isEqualTo("A");
    assertThat(new Binding(Operation.LIST, "A".repeat(86)).permission()).hasSize(86);
    assertThat(new Binding(Operation.LIST, "ROLE_CATEGORY_ADMIN").permission()).isEqualTo(
      "ROLE_CATEGORY_ADMIN"
    );
    assertThat(new Binding(Operation.LIST, "SCOPE_category-read").permission()).isEqualTo(
      "SCOPE_category-read"
    );
    assertThat(new Binding(Operation.LIST, "admin:*-categories:read").permission()).isEqualTo(
      "admin:*-categories:read"
    );
  }

  @ParameterizedTest(name = "invalid permission token [{index}]")
  @MethodSource("invalidPermissionTokens")
  void rejectsEveryMalformedUnsafeOrOversizedRawAuthority(String permission) {
    assertMappingFailure(
      () -> new Binding(Operation.LIST, permission),
      AdminCategoryValidationCategory.INVALID_PERMISSION_MAPPING,
      AdminCategoryValidationCapability.LIST
    );
  }

  @Test
  void rejectsNullAndForgedOperationsBeforeCapabilityDerivation() {
    assertMappingFailure(
      () -> new Binding(null, "admin:taxonomic-fixture-categories:read"),
      AdminCategoryValidationCategory.INVALID_PERMISSION_MAPPING,
      null
    );
    Operation forgedOperation = mock(Operation.class);
    assertMappingFailure(
      () -> new Binding(forgedOperation, "admin:taxonomic-fixture-categories:read"),
      AdminCategoryValidationCategory.INVALID_PERMISSION_MAPPING,
      null
    );
  }

  @Test
  void rejectsNullCollectionsMembersAndDuplicateOperationsAtConstruction() {
    assertMappingFailure(
      () -> new AdminCategoryPermissionMapping((Collection<Binding>) null),
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      null
    );
    assertMappingFailure(
      () ->
        new AdminCategoryPermissionMapping(
          Arrays.asList(
            new Binding(Operation.LIST, "admin:taxonomic-fixture-categories:read"),
            null
          )
        ),
      AdminCategoryValidationCategory.INVALID_COLLECTION,
      null
    );
    assertMappingFailure(
      () ->
        new AdminCategoryPermissionMapping(
          List.of(
            new Binding(Operation.LIST, "admin:taxonomic-fixture-categories:read"),
            new Binding(Operation.LIST, "admin:taxonomic-fixture-categories:write")
          )
        ),
      AdminCategoryValidationCategory.INVALID_PERMISSION_MAPPING,
      AdminCategoryValidationCapability.LIST
    );
  }

  @Test
  void rejectsNullLookupWhileAValidAbsentOperationRemainsEmpty() {
    AdminCategoryPermissionMapping mapping = new AdminCategoryPermissionMapping(
      List.of(new Binding(Operation.LIST, "admin:taxonomic-fixture-categories:read"))
    );

    assertMappingFailure(
      () -> mapping.permissionFor(null),
      AdminCategoryValidationCategory.INVALID_PERMISSION_MAPPING,
      null
    );
    assertThat(mapping.permissionFor(Operation.GET)).isEmpty();
  }

  private static Stream<String> invalidPermissionTokens() {
    return Stream.concat(
      Stream.of((String) null),
      Stream.of(
        "",
        " ",
        "read ",
        " read",
        "A".repeat(87),
        "admin:category/read",
        "admin.category.read",
        "admin category read",
        "admin:\nread",
        "café"
      )
    );
  }

  private static void assertMappingFailure(
    Executable executable,
    AdminCategoryValidationCategory category,
    AdminCategoryValidationCapability capability
  ) {
    AdminCategoryRegistrationValidationException failure = assertThrows(
      AdminCategoryRegistrationValidationException.class,
      executable
    );

    assertThat(failure.diagnostic().safeSystemKey()).isEqualTo("<unavailable>");
    assertThat(failure.diagnostic().category()).isSameAs(category);
    assertThat(failure.diagnostic().component()).contains(
      AdminCategoryValidationComponent.PERMISSION_MAPPING
    );
    assertThat(failure.diagnostic().capability()).isEqualTo(Optional.ofNullable(capability));
    assertThat(failure.diagnostic().policyReference()).isEmpty();
  }
}
