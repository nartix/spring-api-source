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
import com.ferozfaiz.uniapi.catalogue.domain.repository.CatalogueProductListingRepository;
import com.ferozfaiz.uniapi.platform.i18n.LocaleConfiguration;
import com.ferozfaiz.uniapi.platform.i18n.SpringMessageResolver;
import com.ferozfaiz.uniapi.platform.security.SecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.resourceserver.ResourceServerSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.web.RestExceptionHandler;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
  CatalogueProductReadContractRegressionTest.MeterConfiguration.class,
})
class CatalogueProductReadContractRegressionTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private CatalogueProductListingRepository products;

  @MockitoBean
  private ProductApplicationService productApplicationService;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @Test
  void listProductsPreservesPublicPageEnvelopeForEmptyResults() throws Exception {
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
        get("/api/v1/products")
          .param("page", "0")
          .param("size", "20")
          .param("sort", "title,asc")
          .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:read")))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data").isArray())
      .andExpect(jsonPath("$.page.number").value(0))
      .andExpect(jsonPath("$.page.size").value(20))
      .andExpect(jsonPath("$.page.totalElements").value(0))
      .andExpect(jsonPath("$.page.totalPages").value(0))
      .andExpect(jsonPath("$.page.first").value(true))
      .andExpect(jsonPath("$.page.last").value(true))
      .andExpect(jsonPath("$.sort[0].property").value("title"))
      .andExpect(jsonPath("$.sort[0].direction").value("ASC"))
      .andExpect(jsonPath("$.links.self").value("/api/v1/products?page=0&size=20&sort=title,asc"));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MeterConfiguration {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
