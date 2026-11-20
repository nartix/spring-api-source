package com.ferozfaiz.uniapi.catalogue.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferozfaiz.uniapi.catalogue.api.dto.ProductListingRequest;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.shared.error.InvalidRequestException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingRequestTest {

  @Test
  void defaultsPageSortMarketplaceAndFamily() {
    ProductListingRequest request = emptyRequest(List.of("currentPrice,desc"));

    var query = request.toQuery(24, "CA", ProductFamily.GPU);

    assertThat(query.page().page()).isZero();
    assertThat(query.page().size()).isEqualTo(24);
    assertThat(query.marketplace()).isEqualTo("CA");
    assertThat(query.family()).isEqualTo(ProductFamily.GPU);
    assertThat(query.sort().orders()).extracting("property").containsExactly("currentPrice", "id");
  }

  @Test
  void rejectsUnknownSortField() {
    ProductListingRequest request = emptyRequest(List.of("passwordHash,asc"));

    assertThatThrownBy(() -> request.toSortCriteria()).isInstanceOf(InvalidRequestException.class);
  }

  private static ProductListingRequest emptyRequest(List<String> sort) {
    return new ProductListingRequest(
      null,
      null,
      sort,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null
    );
  }
}
