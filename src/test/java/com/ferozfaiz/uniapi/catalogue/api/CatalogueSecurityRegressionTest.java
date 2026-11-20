package com.ferozfaiz.uniapi.catalogue.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueQueryService;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueReferenceDataApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.ProductApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicy;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicyProperties;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueReadAuthorizationPolicy;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueManufacturer;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProduct;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductListingRepository;
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
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
  CatalogueController.class,
  CatalogueCategoryController.class,
  CatalogueReferenceDataController.class,
})
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
  CatalogueSecurityRegressionTest.MeterConfiguration.class,
})
class CatalogueSecurityRegressionTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private CatalogueProductListingRepository products;

  @MockitoBean
  private ProductApplicationService productApplicationService;

  @MockitoBean
  private CatalogueCategoryApplicationService categoryApplicationService;

  @MockitoBean
  private CatalogueReferenceDataApplicationService referenceDataApplicationService;

  @MockitoBean
  private CategorySystemRegistry categorySystemRegistry;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @Test
  void rejectsAnonymousBrowserStyleCatalogueRequests() throws Exception {
    mvc
      .perform(
        get("/api/v1/products").header("Accept", "text/html").header("User-Agent", "Mozilla/5.0")
      )
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void ignoresUntrustedForwardedIdentityWithoutVerifiedAuthentication() throws Exception {
    mvc
      .perform(
        get("/api/v1/products")
          .header("X-Forwarded-User", "shopper-123")
          .header("X-Shopper-Verified", "true")
          .header("X-Forwarded-Scope", "catalogue:read")
      )
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void catalogueWritesRequireWriteAuthorityAndDoNotAcceptReadOnlyTokens() throws Exception {
    mvc
      .perform(
        post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(productJson())
      )
      .andExpect(status().isUnauthorized());

    mvc
      .perform(
        post("/api/v1/products")
          .contentType(MediaType.APPLICATION_JSON)
          .content(productJson())
          .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:read")))
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void catalogueCategoryWritesRequireWriteAuthorityAndDoNotAcceptReadOnlyTokens() throws Exception {
    mvc
      .perform(
        post("/api/v1/catalogue/categories")
          .contentType(MediaType.APPLICATION_JSON)
          .content(categoryJson())
      )
      .andExpect(status().isUnauthorized());

    mvc
      .perform(
        post("/api/v1/catalogue/categories")
          .contentType(MediaType.APPLICATION_JSON)
          .content(categoryJson())
          .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:read")))
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void catalogueReferenceWritesRequireWriteAuthorityAndDoNotAcceptReadOnlyTokens()
    throws Exception {
    mvc
      .perform(
        post("/api/v1/catalogue/brands")
          .contentType(MediaType.APPLICATION_JSON)
          .content(referenceJson())
      )
      .andExpect(status().isUnauthorized());

    mvc
      .perform(
        post("/api/v1/catalogue/brands")
          .contentType(MediaType.APPLICATION_JSON)
          .content(referenceJson())
          .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:read")))
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void catalogueWriteAuthorityAllowsWriteRequestsWithoutGrantingReadAuthority() throws Exception {
    when(productApplicationService.create(any())).thenReturn(product());

    mvc
      .perform(
        post("/api/v1/products")
          .contentType(MediaType.APPLICATION_JSON)
          .content(productJson())
          .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:write")))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").value(1));

    mvc
      .perform(
        get("/api/v1/products").with(
          jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:write"))
        )
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void catalogueWriteAuthorityAllowsCategoryWritesAndReadOnlyAuthorityStillReads()
    throws Exception {
    when(categoryApplicationService.create(any())).thenReturn(category());
    when(products.findActiveProducts(any())).thenReturn(
      new PageResult<>(
        List.of(),
        0,
        20,
        0,
        0,
        true,
        true,
        SortCriteria.of("title", SortDirection.ASC)
      )
    );

    mvc
      .perform(
        post("/api/v1/catalogue/categories")
          .contentType(MediaType.APPLICATION_JSON)
          .content(categoryJson())
          .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:write")))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").value(1));

    mvc
      .perform(
        get("/api/v1/products").with(
          jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:read"))
        )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page.totalElements").value(0));
  }

  @Test
  void catalogueWriteAuthorityAllowsReferenceWritesAndReadOnlyAuthorityStillReads()
    throws Exception {
    when(referenceDataApplicationService.createOrUpdateBrand(any())).thenReturn(brand());
    when(referenceDataApplicationService.listBrands(any())).thenReturn(
      new PageResult<>(
        List.of(),
        0,
        20,
        0,
        0,
        true,
        true,
        SortCriteria.of("name", SortDirection.ASC)
      )
    );

    mvc
      .perform(
        post("/api/v1/catalogue/brands")
          .contentType(MediaType.APPLICATION_JSON)
          .content(referenceJson())
          .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:write")))
      )
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.id").value(1));

    mvc
      .perform(
        get("/api/v1/catalogue/brands").with(
          jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:read"))
        )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.page.totalElements").value(0));
  }

  @Test
  void roleAdminIsNotCatalogueBypass() throws Exception {
    mvc
      .perform(
        get("/api/v1/products").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
      )
      .andExpect(status().isForbidden());

    mvc
      .perform(
        post("/api/v1/products")
          .contentType(MediaType.APPLICATION_JSON)
          .content(productJson())
          .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
      )
      .andExpect(status().isForbidden());
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MeterConfiguration {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  private static String productJson() {
    return """
    {
      "title": "Samsung 990 Pro 2TB",
      "slug": "samsung-990-pro-2tb",
      "family": "STORAGE",
      "brandId": 10,
      "manufacturerId": 20,
      "active": true
    }
    """;
  }

  private static String categoryJson() {
    return """
    {
      "parentId": null,
      "name": "Storage",
      "slug": "storage",
      "active": true
    }
    """;
  }

  private static String referenceJson() {
    return """
    {
      "name": "Samsung",
      "slug": "samsung",
      "active": true
    }
    """;
  }

  private static CatalogueProduct product() {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-12T10:15:30Z");
    return new CatalogueProduct(
      1L,
      "Samsung 990 Pro 2TB",
      "samsung-990-pro-2tb",
      ProductFamily.STORAGE,
      new CatalogueBrand(10L, "Samsung", "samsung", true, now, now),
      new CatalogueManufacturer(20L, "Samsung Electronics", "samsung-electronics", true, now, now),
      null,
      null,
      true,
      now,
      now
    );
  }

  private static CatalogueCategory category() {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-12T10:15:30Z");
    return new CatalogueCategory(1L, null, "Storage", "storage", true, now, now);
  }

  private static CatalogueBrand brand() {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-12T10:15:30Z");
    return new CatalogueBrand(1L, "Samsung", "samsung", true, now, now);
  }
}
