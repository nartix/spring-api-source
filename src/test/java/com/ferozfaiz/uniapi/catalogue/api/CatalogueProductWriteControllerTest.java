package com.ferozfaiz.uniapi.catalogue.api;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueQueryService;
import com.ferozfaiz.uniapi.catalogue.application.ProductApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicy;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueManufacturer;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProduct;
import com.ferozfaiz.uniapi.platform.i18n.LocaleConfiguration;
import com.ferozfaiz.uniapi.platform.i18n.SpringMessageResolver;
import com.ferozfaiz.uniapi.platform.security.SecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.resourceserver.ResourceServerSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.web.RestExceptionHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
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

@WebMvcTest(CatalogueController.class)
@Import({
  SecurityConfiguration.class,
  ResourceServerSecurityConfiguration.class,
  LocaleConfiguration.class,
  SpringMessageResolver.class,
  RestExceptionHandler.class,
  CatalogueApiExceptionHandler.class,
  CatalogueProductWriteControllerTest.MeterConfiguration.class,
})
class CatalogueProductWriteControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private CatalogueQueryService queries;

  @MockitoBean
  private ProductApplicationService products;

  @MockitoBean
  private CatalogueListPolicy policy;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @Test
  void createsReplacesAndPatchesProducts() throws Exception {
    when(products.create(argThat(command -> command.id() == null))).thenReturn(
      product(1L, "Samsung 990 Pro 2TB", "samsung-990-pro-2tb", true, null, null)
    );
    when(products.update(argThat(command -> Long.valueOf(1L).equals(command.id())))).thenReturn(
      product(1L, "Samsung 990 Pro 2TB Updated", "samsung-990-pro-2tb", true, null, null)
    );
    when(products.get(1L)).thenReturn(
      product(
        1L,
        "Samsung 990 Pro 2TB Updated",
        "samsung-990-pro-2tb",
        true,
        "https://example.test/old.png",
        "Fast NVMe storage"
      )
    );

    mvc
      .perform(
        post("/api/v1/products")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(productJson("Samsung 990 Pro 2TB", "samsung-990-pro-2tb"))
      )
      .andExpect(status().isCreated())
      .andExpect(header().string("Location", "http://localhost/api/v1/products/1"))
      .andExpect(jsonPath("$.id").value(1))
      .andExpect(jsonPath("$.canonicalUrl").value("/api/v1/products/1/samsung-990-pro-2tb"));
    verify(products).create(
      argThat(
        command ->
          command.identifiers().size() == 1 &&
          "00036000291452".equals(command.identifiers().getFirst().canonicalGtin14()) &&
          command.identifiers().getFirst().primary()
      )
    );

    mvc
      .perform(
        put("/api/v1/products/1")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(productJson("Samsung 990 Pro 2TB Updated", "samsung-990-pro-2tb"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.title").value("Samsung 990 Pro 2TB Updated"));

    mvc
      .perform(
        patch("/api/v1/products/1")
          .with(writeJwt())
          .contentType("application/merge-patch+json")
          .content(
            """
            {
              "mainImageUrl": null
            }
            """
          )
      )
      .andExpect(status().isOk());

    verify(products).update(
      argThat(
        command ->
          Long.valueOf(1L).equals(command.id()) &&
          command.mainImageUrl() == null &&
          "Fast NVMe storage".equals(command.shortDescription())
      )
    );
  }

  @Test
  void writeCommandsReturnNoContentForLifecycleDeleteAndCategoryMembership() throws Exception {
    mvc
      .perform(post("/api/v1/products/1/activate").with(writeJwt()))
      .andExpect(status().isNoContent());
    mvc
      .perform(post("/api/v1/products/1/deactivate").with(writeJwt()))
      .andExpect(status().isNoContent());
    mvc
      .perform(post("/api/v1/products/1/categories/10").with(writeJwt()))
      .andExpect(status().isNoContent());
    mvc
      .perform(delete("/api/v1/products/1/categories/10").with(writeJwt()))
      .andExpect(status().isNoContent());
    mvc.perform(delete("/api/v1/products/1").with(writeJwt())).andExpect(status().isNoContent());

    verify(products).activate(1L);
    verify(products).deactivate(1L);
    verify(products).assignCategory(1L, 10L);
    verify(products).removeCategory(1L, 10L);
    verify(products).delete(1L);
  }

  @Test
  void validatesCreateRequestsAndPatchRequiredNulls() throws Exception {
    mvc
      .perform(
        post("/api/v1/products").with(writeJwt()).contentType(MediaType.APPLICATION_JSON).content(
          """
          {
            "title": "",
            "slug": "bad slug"
          }
          """
        )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    when(products.get(1L)).thenReturn(
      product(1L, "Samsung 990 Pro 2TB", "samsung-990-pro-2tb", true, null, null)
    );
    when(products.update(argThat(command -> command.title() == null))).thenThrow(
      new CatalogueException(
        "catalogue.application.product.title.required",
        "product title is required"
      )
    );

    mvc
      .perform(
        patch("/api/v1/products/1")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "title": null
            }
            """
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MeterConfiguration {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor writeJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:write"));
  }

  private static String productJson(String title, String slug) {
    return """
    {
      "title": "%s",
      "slug": "%s",
      "family": "STORAGE",
      "brandId": 10,
      "manufacturerId": 20,
      "identifiers": [
        {
          "family": "UPC",
          "value": "036000291452",
          "primary": true
        }
      ],
      "active": true
    }
    """.formatted(title, slug);
  }

  private static CatalogueProduct product(
    Long id,
    String title,
    String slug,
    boolean active,
    String mainImageUrl,
    String description
  ) {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-12T10:15:30Z");
    return new CatalogueProduct(
      id,
      title,
      slug,
      ProductFamily.STORAGE,
      new CatalogueBrand(10L, "Samsung", "samsung", true, now, now),
      new CatalogueManufacturer(20L, "Samsung Electronics", "samsung-electronics", true, now, now),
      mainImageUrl,
      description,
      active,
      now,
      now
    );
  }
}
