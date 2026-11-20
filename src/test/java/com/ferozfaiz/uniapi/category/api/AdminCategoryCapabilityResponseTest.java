package com.ferozfaiz.uniapi.category.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferozfaiz.uniapi.category.api.mapper.AdminCategoryApiMapper;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminCategoryCapabilityResponseTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void serializesTheExactAdditiveCapabilityContractDeterministically() throws Exception {
    var response = AdminCategoryApiMapper.toCapabilityResponse(
      AdminCategoryRuntimeTestFixture.completeDefinition()
    );

    var first = json.writeValueAsBytes(response);
    var second = json.writeValueAsBytes(response);
    var document = json.readTree(first);

    assertThat(document.propertyStream().map(java.util.Map.Entry::getKey).toList()).containsExactly(
      "rankingEnabled",
      "placementModes",
      "sortRankExposed",
      "fallbackOrder",
      "supportedOperations",
      "hierarchy",
      "limits",
      "lifecycle",
      "identifierProfile",
      "fields",
      "searchableFields",
      "allowedFilters",
      "allowedSorts",
      "ordering"
    );
    assertThat(document.get("supportedOperations").findValuesAsText("")).isEmpty();
    assertThat(response.supportedOperations()).containsExactly(
      "capabilities",
      "list",
      "tree",
      "parent-options",
      "get",
      "breadcrumb",
      "create",
      "replace",
      "patch",
      "delete",
      "move",
      "reorder",
      "activate",
      "deactivate"
    );
    assertThat(response.rankingEnabled()).isEqualTo(response.ordering().rankingEnabled());
    assertThat(response.placementModes()).isEqualTo(response.ordering().placementModes());
    assertThat(response.sortRankExposed()).isEqualTo(response.ordering().rankExposed());
    assertThat(response.fallbackOrder()).isEqualTo(response.ordering().fallbackOrder());
    assertThat(response.hierarchy().readOperations()).containsExactly(
      "tree",
      "parent-options",
      "breadcrumb"
    );
    assertThat(response.hierarchy().mutationOperations()).containsExactly("move");
    assertThat(response.lifecycle().states()).containsExactly("ACTIVE", "INACTIVE");
    assertThat(response.lifecycle().actions()).containsExactly("activate", "deactivate");
    assertThat(response.identifierProfile()).isEqualTo("NUMERIC_LONG_V1");
    assertThat(first).containsExactly(second);
    assertThat(new String(first, java.nio.charset.StandardCharsets.UTF_8)).doesNotContain(
      "permission",
      "policy",
      "provider",
      "storage",
      "cursor",
      "logicalId"
    );
  }

  @Test
  void nestedCollectionsRejectNullsAtConstruction() {
    org.assertj.core.api.Assertions.assertThatNullPointerException().isThrownBy(() ->
      new com.ferozfaiz.uniapi.category.api.dto.AdminCategoryCapabilityResponse(
        false,
        null,
        false,
        List.of(),
        List.of(),
        null,
        null,
        null,
        "NUMERIC_LONG_V1",
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null
      )
    );
  }
}
