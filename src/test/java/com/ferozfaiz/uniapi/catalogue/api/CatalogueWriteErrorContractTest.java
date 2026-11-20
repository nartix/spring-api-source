package com.ferozfaiz.uniapi.catalogue.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueQueryService;
import com.ferozfaiz.uniapi.catalogue.application.ProductApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicy;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueReferenceNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueEnums.ProductFamily;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueManufacturer;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueProduct;
import com.ferozfaiz.uniapi.platform.i18n.LocaleConfiguration;
import com.ferozfaiz.uniapi.platform.i18n.SpringMessageResolver;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
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

@WebMvcTest({ CatalogueController.class, CatalogueCategoryController.class })
@Import({
  SecurityConfiguration.class,
  ResourceServerSecurityConfiguration.class,
  LocaleConfiguration.class,
  SpringMessageResolver.class,
  RestExceptionHandler.class,
  CatalogueApiExceptionHandler.class,
  CatalogueWriteErrorContractTest.MeterConfiguration.class,
})
class CatalogueWriteErrorContractTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private CatalogueQueryService queries;

  @MockitoBean
  private ProductApplicationService products;

  @MockitoBean
  private CatalogueCategoryApplicationService categories;

  @MockitoBean
  private CatalogueListPolicy policy;

  @MockitoBean
  private CategorySystemRegistry categorySystemRegistry;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @Test
  void duplicateSlugsUseConflictErrorShape() throws Exception {
    when(products.create(any())).thenThrow(DuplicateCatalogueSlugException.product("duplicate"));

    mvc
      .perform(
        post("/api/v1/products")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(productJson())
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("DUPLICATE_PRODUCT_SLUG"))
      .andExpect(jsonPath("$.path").value("/api/v1/products"));
  }

  @Test
  void missingProductReferencesUseStableNotFoundErrorShape() throws Exception {
    doThrow(CatalogueReferenceNotFoundException.brand(10L)).when(products).create(any());

    mvc
      .perform(
        post("/api/v1/products")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(productJson())
      )
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"))
      .andExpect(jsonPath("$.path").value("/api/v1/products"));

    doThrow(CatalogueReferenceNotFoundException.manufacturer(20L)).when(products).create(any());

    mvc
      .perform(
        post("/api/v1/products")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(productJson())
      )
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("MANUFACTURER_NOT_FOUND"));
  }

  @Test
  void replaceAndPatchMissingProductReferencesUseStableNotFoundErrorShape() throws Exception {
    doThrow(CatalogueReferenceNotFoundException.brand(10L)).when(products).update(any());

    mvc
      .perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
          "/api/v1/products/1"
        )
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(productJson())
      )
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"));

    when(products.get(1L)).thenReturn(product());
    doThrow(CatalogueReferenceNotFoundException.manufacturer(20L)).when(products).update(any());

    mvc
      .perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
          "/api/v1/products/1"
        )
          .with(writeJwt())
          .contentType("application/merge-patch+json")
          .content(
            """
            {
              "manufacturerId": 20
            }
            """
          )
      )
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("MANUFACTURER_NOT_FOUND"));
  }

  @Test
  void patchNullProductReferencesStayValidationFailures() throws Exception {
    when(products.get(1L)).thenReturn(product());
    doThrow(
      new com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueException(
        "catalogue.application.product.brand.required",
        "product brand is required"
      )
    )
      .when(products)
      .update(any());

    mvc
      .perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
          "/api/v1/products/1"
        )
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "brandId": null
            }
            """
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    doThrow(
      new com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueException(
        "catalogue.application.product.manufacturer.required",
        "product manufacturer is required"
      )
    )
      .when(products)
      .update(any());

    mvc
      .perform(
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
          "/api/v1/products/1"
        )
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "manufacturerId": null
            }
            """
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void validationFailuresUseStableErrorShape() throws Exception {
    mvc
      .perform(
        post("/api/v1/catalogue/categories")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "name": "",
              "slug": "bad slug"
            }
            """
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.path").value("/api/v1/catalogue/categories"));
  }

  @Test
  void missingProductReferenceFieldsStayBeanValidationFailures() throws Exception {
    mvc
      .perform(
        post("/api/v1/products").with(writeJwt()).contentType(MediaType.APPLICATION_JSON).content(
          """
          {
            "title": "Samsung 990 Pro 2TB",
            "slug": "samsung-990-pro-2tb",
            "family": "STORAGE",
            "active": true
          }
          """
        )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'brandId')]").exists())
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'manufacturerId')]").exists());
  }

  @Test
  void missingEntitiesAndWriteConflictsUseStableErrorShape() throws Exception {
    when(products.create(any())).thenThrow(CatalogueNotFoundException.product(404L));

    mvc
      .perform(
        post("/api/v1/products")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(productJson())
      )
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

    doThrow(CatalogueConflictException.productDeleteBlocked(1L)).when(products).delete(1L);
    mvc
      .perform(delete("/api/v1/products/1").with(writeJwt()))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("PRODUCT_DELETE_REJECTED"));

    doThrow(CatalogueNotFoundException.category("1")).when(categories).delete(1L);
    mvc
      .perform(delete("/api/v1/catalogue/categories/1").with(writeJwt()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

    doThrow(CatalogueConflictException.invalidCategoryMove(1L, 2L))
      .when(categories)
      .move(eq(1L), eq(2L), any());
    mvc
      .perform(
        post("/api/v1/catalogue/categories/1/move")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "newParentId": 2
            }
            """
          )
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("CATEGORY_MOVE_REJECTED"));
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

  private static String productJson() {
    return """
    {
      "title": "Samsung 990 Pro 2TB",
      "slug": "duplicate",
      "family": "STORAGE",
      "brandId": 10,
      "manufacturerId": 20,
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
}
