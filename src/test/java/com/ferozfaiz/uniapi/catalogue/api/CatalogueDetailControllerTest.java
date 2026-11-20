package com.ferozfaiz.uniapi.catalogue.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueQueryService;
import com.ferozfaiz.uniapi.catalogue.application.ProductApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicy;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicyProperties;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueReadAuthorizationPolicy;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductListingRepository;
import com.ferozfaiz.uniapi.catalogue.domain.validation.ProductIdentifierRules;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.projection.ProductDetailProjection;
import com.ferozfaiz.uniapi.catalogue.infrastructure.persistence.projection.ProductListItemProjection;
import com.ferozfaiz.uniapi.platform.i18n.LocaleConfiguration;
import com.ferozfaiz.uniapi.platform.i18n.SpringMessageResolver;
import com.ferozfaiz.uniapi.platform.security.SecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.resourceserver.ResourceServerSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.web.RestExceptionHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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

@WebMvcTest(CatalogueController.class)
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
  CatalogueDetailControllerTest.MeterConfiguration.class,
})
class CatalogueDetailControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private CatalogueProductListingRepository products;

  @MockitoBean
  private ProductApplicationService productApplicationService;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @Test
  void detailRequiresAuthentication() throws Exception {
    mvc
      .perform(get("/api/v1/products/1/samsung-990-pro-2tb"))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
      .andExpect(jsonPath("$.path").value("/api/v1/products/1/samsung-990-pro-2tb"));
  }

  @Test
  void detailRequiresCatalogueReadAuthority() throws Exception {
    mvc
      .perform(
        get("/api/v1/products/1/samsung-990-pro-2tb").with(
          jwt().authorities(new SimpleGrantedAuthority("SCOPE_users:read"))
        )
      )
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("FORBIDDEN"))
      .andExpect(jsonPath("$.path").value("/api/v1/products/1/samsung-990-pro-2tb"));
  }

  @Test
  void detailSupportsIdOnlyLookup() throws Exception {
    when(products.findActiveProductDetail(any())).thenReturn(Optional.of(detail()));

    mvc
      .perform(get("/api/v1/products/1").with(catalogueJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(1))
      .andExpect(jsonPath("$.slug").value("samsung-990-pro-2tb"))
      .andExpect(jsonPath("$.canonicalUrl").value("/api/v1/products/1/samsung-990-pro-2tb"));
  }

  @Test
  void detailReturnsCanonicalUrlCategoriesSpecsAndCurrentPrice() throws Exception {
    when(products.findActiveProductDetail(any())).thenReturn(Optional.of(detail()));

    mvc
      .perform(get("/api/v1/products/1/samsung-990-pro-2tb").with(catalogueJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(1))
      .andExpect(jsonPath("$.canonicalUrl").value("/api/v1/products/1/samsung-990-pro-2tb"))
      .andExpect(jsonPath("$.categories[0].slug").value("storage"))
      .andExpect(jsonPath("$.storage.storageType").value("NVME_SSD"))
      .andExpect(jsonPath("$.storage.readSpeedMbps").value(7450))
      .andExpect(jsonPath("$.storage.rpm").value(0))
      .andExpect(jsonPath("$.storage.cacheMb").value(2048))
      .andExpect(jsonPath("$.identifiers[0].family").value("UPC"))
      .andExpect(jsonPath("$.identifiers[0].canonicalGtin14").value("00036000291452"))
      .andExpect(jsonPath("$.currentPrice.marketplace").value("CA"));
  }

  @Test
  void detailMapsMissingOrWrongSlugToNotFound() throws Exception {
    when(products.findActiveProductDetail(any())).thenReturn(Optional.empty());

    mvc
      .perform(get("/api/v1/products/404/wrong-slug").with(catalogueJwt()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
      .andExpect(jsonPath("$.path").value("/api/v1/products/404/wrong-slug"));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MeterConfiguration {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  private static RequestPostProcessor catalogueJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("SERVICE_CATALOGUE_READ"));
  }

  private static ProductDetailProjection detail() {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-12T10:15:30Z");
    var identifier = ProductIdentifierRules.normalize("UPC", "036000291452", true);
    ProductListItemProjection product = new ProductListItemProjection(
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
      identifier,
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
    return new ProductDetailProjection(
      product,
      List.of(new ProductDetailProjection.CategorySummaryProjection(1L, "Storage", "storage")),
      List.of(identifier),
      null,
      null,
      null,
      null,
      0,
      2048,
      null,
      null,
      null
    );
  }
}
