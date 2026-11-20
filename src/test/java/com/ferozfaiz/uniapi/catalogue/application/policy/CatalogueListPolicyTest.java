package com.ferozfaiz.uniapi.catalogue.application.policy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogueListPolicyTest {

  @Test
  void rejectsPageSizeAboveConfiguredMaximum() {
    CatalogueListPolicyProperties properties = new CatalogueListPolicyProperties();
    properties.setMaxPageSize(50);
    CatalogueListPolicy policy = new CatalogueListPolicy(properties);

    assertThatThrownBy(() ->
      policy.enforce(
        new PageCriteria(0, 51),
        SortCriteria.of("title", SortDirection.ASC),
        null,
        ProductFamily.STORAGE,
        Map.of()
      )
    ).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void rejectsIncompatibleFamilySpecificFilter() {
    CatalogueListPolicy policy = new CatalogueListPolicy(new CatalogueListPolicyProperties());

    assertThatThrownBy(() ->
      policy.enforce(
        new PageCriteria(0, 24),
        SortCriteria.of("title", SortDirection.ASC),
        null,
        ProductFamily.CPU,
        Map.of("capacityGbMin", 1000)
      )
    ).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void rejectsTooManySortOrders() {
    CatalogueListPolicyProperties properties = new CatalogueListPolicyProperties();
    properties.setMaxSortOrders(1);
    CatalogueListPolicy policy = new CatalogueListPolicy(properties);

    assertThatThrownBy(() ->
      policy.enforce(
        new PageCriteria(0, 24),
        new SortCriteria(
          List.of(new SortOrder("title", SortDirection.ASC), new SortOrder("id", SortDirection.ASC))
        ),
        null,
        ProductFamily.STORAGE,
        Map.of()
      )
    ).isInstanceOf(InvalidRequestException.class);
  }
}
