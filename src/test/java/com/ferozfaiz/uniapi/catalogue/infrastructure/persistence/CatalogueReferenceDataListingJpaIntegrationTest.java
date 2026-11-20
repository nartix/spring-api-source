package com.ferozfaiz.uniapi.catalogue.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueReferenceDataApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertBrandCommand;
import com.ferozfaiz.uniapi.catalogue.application.command.UpsertManufacturerCommand;
import com.ferozfaiz.uniapi.catalogue.application.query.ListReferenceDataQuery;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.shared.pagination.PageCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import com.ferozfaiz.uniapi.shared.pagination.SortOrder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

@WithMockUser(authorities = "SCOPE_catalogue:read")
class CatalogueReferenceDataListingJpaIntegrationTest
  extends AbstractCataloguePersistenceIntegrationTest
{

  @Autowired
  private CatalogueReferenceDataApplicationService referenceData;

  @Test
  void brandListingPreservesSearchActiveSortPaginationAndMetadata() {
    referenceData.createOrUpdateBrand(new UpsertBrandCommand(null, "Samsung", "samsung", true));
    referenceData.createOrUpdateBrand(new UpsertBrandCommand(null, "Crucial", "crucial", false));
    referenceData.createOrUpdateBrand(new UpsertBrandCommand(null, "Sabrent", "sabrent", true));

    var page = referenceData.listBrands(
      new ListReferenceDataQuery(
        new PageCriteria(0, 1),
        new SortCriteria(
          List.of(new SortOrder("name", SortDirection.DESC), new SortOrder("id", SortDirection.ASC))
        ),
        "sa",
        true
      )
    );

    assertThat(page.content()).extracting(CatalogueBrand::slug).containsExactly("samsung");
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.totalPages()).isEqualTo(2);
    assertThat(page.first()).isTrue();
    assertThat(page.last()).isFalse();
  }

  @Test
  void manufacturerListingPreservesSearchActiveSortPaginationAndMetadata() {
    referenceData.createOrUpdateManufacturer(
      new UpsertManufacturerCommand(null, "Samsung Electronics", "samsung-electronics", true)
    );
    referenceData.createOrUpdateManufacturer(
      new UpsertManufacturerCommand(null, "Micron", "micron", false)
    );

    var page = referenceData.listManufacturers(
      new ListReferenceDataQuery(
        new PageCriteria(0, 20),
        SortCriteria.of("slug", SortDirection.ASC),
        "SUNG",
        true
      )
    );

    assertThat(page.content()).extracting("slug").containsExactly("samsung-electronics");
    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(page.totalPages()).isEqualTo(1);
  }
}
