package com.ferozfaiz.uniapi.catalogue.api;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueQueryService;
import com.ferozfaiz.uniapi.catalogue.application.ProductApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicy;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicyProperties;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueReadAuthorizationPolicy;
import com.ferozfaiz.uniapi.catalogue.application.query.ListProductsQuery;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductListingRepository;
import com.ferozfaiz.uniapi.catalogue.domain.validation.ProductIdentifierRules;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.projection.ProductListItemProjection;
import com.ferozfaiz.uniapi.platform.i18n.LocaleConfiguration;
import com.ferozfaiz.uniapi.platform.i18n.SpringMessageResolver;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.platform.security.SecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.resourceserver.ResourceServerSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.web.RestExceptionHandler;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest({ CatalogueController.class, CatalogueCategoryController.class })
@Import({
  SecurityConfiguration.class,
  ResourceServerSecurityConfiguration.class,
  LocaleConfiguration.class,
  SpringMessageResolver.class,
  RestExceptionHandler.class,
  CatalogueApiExceptionHandler.class,
  CatalogueQueryService.class,
  CatalogueListPolicy.class,
  CatalogueListPolicyProperties.class,
  CatalogueReadAuthorizationPolicy.class,
  CatalogueListingControllerTest.MeterConfiguration.class,
})
class CatalogueListingControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private CatalogueProductListingRepository products;

  @MockitoBean
  private ProductApplicationService productApplicationService;

  @MockitoBean
  private CatalogueCategoryApplicationService categoryApplicationService;

  @MockitoBean
  private CategorySystemRegistry categorySystemRegistry;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @Test
  void listingRequiresAuthentication() throws Exception {
    mvc
      .perform(get("/api/v1/products"))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
      .andExpect(jsonPath("$.path").value("/api/v1/products"));
  }

  @Test
  void listingRequiresCatalogueReadAuthority() throws Exception {
    mvc
      .perform(
        get("/api/v1/products").with(
          jwt().authorities(new SimpleGrantedAuthority("SCOPE_users:read"))
        )
      )
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"))
      .andExpect(jsonPath("$.path").value("/api/v1/products"));
  }

  @Test
  void listingReturnsPageEnvelopeAndLinks() throws Exception {
    when(products.findActiveProducts(any())).thenAnswer(invocation -> {
      ListProductsQuery query = invocation.getArgument(0);
      return new PageResult<>(
        List.of(storageProduct()),
        query.page().page(),
        query.page().size(),
        6,
        2,
        false,
        true,
        query.sort()
      );
    });

    mvc
      .perform(
        get("/api/v1/products")
          .param("family", "STORAGE")
          .param("marketplace", "CA")
          .param("page", "1")
          .param("size", "5")
          .param("sort", "capacityGb,desc")
          .param("sort", "currentPrice,asc")
          .with(catalogueJwt())
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].id").value(1))
      .andExpect(jsonPath("$.data[0].canonicalUrl").value("/api/v1/products/1/samsung-990-pro-2tb"))
      .andExpect(jsonPath("$.data[0].primaryIdentifier.family").value("UPC"))
      .andExpect(jsonPath("$.data[0].primaryIdentifier.canonicalGtin14").value("00036000291452"))
      .andExpect(jsonPath("$.data[0].currentPrice.amount").value(149.99))
      .andExpect(jsonPath("$.data[0].bestOffer.merchantStoreSlug").value("amazon-ca"))
      .andExpect(jsonPath("$.data[0].storage.capacityGb").value(2000))
      .andExpect(jsonPath("$.page.number").value(1))
      .andExpect(jsonPath("$.page.size").value(5))
      .andExpect(jsonPath("$.page.totalElements").value(6))
      .andExpect(jsonPath("$.page.totalPages").value(2))
      .andExpect(jsonPath("$.page.first").value(false))
      .andExpect(jsonPath("$.page.last").value(true))
      .andExpect(jsonPath("$.sort[0].property").value("capacityGb"))
      .andExpect(jsonPath("$.sort[1].property").value("currentPrice"))
      .andExpect(
        jsonPath("$.links.self").value(
          "/api/v1/products?page=1&size=5&family=STORAGE&marketplace=CA&sort=capacityGb,desc&sort=currentPrice,asc&sort=id,asc"
        )
      )
      .andExpect(jsonPath("$.links.next").value(nullValue()))
      .andExpect(
        jsonPath("$.links.previous").value(
          "/api/v1/products?page=0&size=5&family=STORAGE&marketplace=CA&sort=capacityGb,desc&sort=currentPrice,asc&sort=id,asc"
        )
      );

    verify(products).findActiveProducts(
      argThat(
        query ->
          query.page().page() == 1 &&
          query.page().size() == 5 &&
          ProductFamily.STORAGE == query.family() &&
          "CA".equals(query.marketplace()) &&
          query.sort().orders().size() == 3 &&
          "capacityGb".equals(query.sort().orders().get(0).property()) &&
          SortDirection.DESC == query.sort().orders().get(0).direction() &&
          "currentPrice".equals(query.sort().orders().get(1).property()) &&
          SortDirection.ASC == query.sort().orders().get(1).direction() &&
          "id".equals(query.sort().orders().get(2).property())
      )
    );
  }

  @Test
  void listingAcceptsSingleFieldDirectionSortParam() throws Exception {
    when(products.findActiveProducts(any())).thenReturn(
      new PageResult<>(
        List.of(storageProduct()),
        0,
        20,
        1,
        1,
        true,
        true,
        SortCriteria.of("title", SortDirection.ASC)
      )
    );

    mvc
      .perform(get("/api/v1/products").param("sort", "title,asc").with(catalogueJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.sort[0].property").value("title"))
      .andExpect(jsonPath("$.sort[0].direction").value("ASC"));
  }

  @Test
  void listingRejectsUnsupportedSortUnknownFilterOversizedPageAndIncompatibleFilters()
    throws Exception {
    mvc
      .perform(get("/api/v1/products").param("sort", "passwordHash,asc").with(catalogueJwt()))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_SORT_FIELD"));

    mvc
      .perform(get("/api/v1/products").param("secret", "true").with(catalogueJwt()))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INVALID_FILTER_FIELD"));

    mvc
      .perform(get("/api/v1/products").param("size", "101").with(catalogueJwt()))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("PAGE_SIZE_TOO_LARGE"));

    mvc
      .perform(get("/api/v1/storage-products").param("coreCountMin", "8").with(catalogueJwt()))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("INCOMPATIBLE_FILTER_COMBINATION"));
  }

  @Test
  void categoryListingReturnsPageEnvelopeForCategorySlug() throws Exception {
    when(products.findActiveProducts(any())).thenReturn(
      new PageResult<>(
        List.of(storageProduct()),
        0,
        20,
        1,
        1,
        true,
        true,
        SortCriteria.of("title", SortDirection.ASC)
      )
    );

    mvc
      .perform(get("/api/v1/categories/storage/products").with(catalogueJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].id").value(1))
      .andExpect(jsonPath("$.page.totalElements").value(1))
      .andExpect(
        jsonPath("$.links.self").value(
          "/api/v1/categories/storage/products?page=0&size=20&sort=title,asc"
        )
      );

    verify(products).findActiveProducts(argThat(query -> "storage".equals(query.categorySlug())));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MeterConfiguration {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  private static RequestPostProcessor catalogueJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:read"));
  }

  private static ProductListItemProjection storageProduct() {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-12T10:15:30Z");
    return new ProductListItemProjection(
      1L,
      "Samsung 990 Pro 2TB",
      "samsung-990-pro-2tb",
      ProductFamily.STORAGE,
      1L,
      "Samsung",
      "samsung",
      1L,
      "Samsung Electronics",
      "samsung-electronics",
      ProductIdentifierRules.normalize("UPC", "036000291452", true),
      "https://example.test/990-pro.png",
      "Fast NVMe storage",
      new BigDecimal("149.99"),
      "CAD",
      "CA",
      "IN_STOCK",
      1L,
      "Amazon Canada",
      "amazon-ca",
      "NVME_SSD",
      2000,
      "NVME",
      "M_2_2280",
      7450,
      6900,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      now,
      now
    );
  }
}
