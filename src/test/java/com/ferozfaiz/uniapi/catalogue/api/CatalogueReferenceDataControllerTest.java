package com.ferozfaiz.uniapi.catalogue.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferozfaiz.uniapi.catalogue.application.CatalogueReferenceDataApplicationService;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueReferenceNotFoundException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.DuplicateCatalogueSlugException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueBrand;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueManufacturer;
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

@WebMvcTest(CatalogueReferenceDataController.class)
@Import({
  SecurityConfiguration.class,
  ResourceServerSecurityConfiguration.class,
  LocaleConfiguration.class,
  SpringMessageResolver.class,
  RestExceptionHandler.class,
  CatalogueApiExceptionHandler.class,
  CatalogueReferenceDataControllerTest.MeterConfiguration.class,
})
class CatalogueReferenceDataControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private CatalogueReferenceDataApplicationService referenceData;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @Test
  void listsAndReadsBrandsWithReadAuthority() throws Exception {
    when(referenceData.listBrands(any())).thenReturn(
      new PageResult<>(
        List.of(brand(1L, "Samsung", "samsung", true)),
        0,
        20,
        1,
        1,
        true,
        true,
        SortCriteria.of("name", SortDirection.ASC)
      )
    );
    when(referenceData.getBrand(1L)).thenReturn(brand(1L, "Samsung", "samsung", true));

    mvc
      .perform(get("/api/v1/catalogue/brands").with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].slug").value("samsung"))
      .andExpect(jsonPath("$.page.totalElements").value(1));

    mvc
      .perform(get("/api/v1/catalogue/brands/1").with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("Samsung"));
  }

  @Test
  void brandListingPreservesPublicQueryParametersAndPageResponseShape() throws Exception {
    when(referenceData.listBrands(any())).thenReturn(
      new PageResult<>(
        List.of(brand(1L, "Samsung", "samsung", true)),
        1,
        5,
        6,
        2,
        false,
        true,
        SortCriteria.of("slug", SortDirection.DESC)
      )
    );

    mvc
      .perform(
        get("/api/v1/catalogue/brands")
          .param("page", "1")
          .param("size", "5")
          .param("sort", "slug,desc")
          .param("search", "sam")
          .param("active", "true")
          .with(readJwt())
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].slug").value("samsung"))
      .andExpect(jsonPath("$.page.number").value(1))
      .andExpect(jsonPath("$.page.size").value(5))
      .andExpect(jsonPath("$.page.totalElements").value(6))
      .andExpect(jsonPath("$.page.totalPages").value(2))
      .andExpect(jsonPath("$.page.first").value(false))
      .andExpect(jsonPath("$.page.last").value(true))
      .andExpect(jsonPath("$.sort[0].property").value("slug"))
      .andExpect(jsonPath("$.sort[0].direction").value("DESC"));

    verify(referenceData).listBrands(
      argThat(
        query ->
          query.page().page() == 1 &&
          query.page().size() == 5 &&
          "sam".equals(query.search()) &&
          Boolean.TRUE.equals(query.active()) &&
          "slug".equals(query.sort().orders().getFirst().property())
      )
    );
  }

  @Test
  void createsReplacesPatchesAndDeletesBrands() throws Exception {
    when(
      referenceData.createOrUpdateBrand(argThat(command -> command != null && command.id() == null))
    ).thenReturn(brand(1L, "Samsung", "samsung", true));
    when(
      referenceData.createOrUpdateBrand(
        argThat(command -> command != null && Long.valueOf(1L).equals(command.id()))
      )
    ).thenReturn(brand(1L, "Samsung Updated", "samsung-updated", false));
    when(referenceData.getBrand(1L)).thenReturn(brand(1L, "Samsung", "samsung", true));

    mvc
      .perform(
        post("/api/v1/catalogue/brands")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(referenceJson("Samsung", "samsung", true))
      )
      .andExpect(status().isCreated())
      .andExpect(header().string("Location", "http://localhost/api/v1/catalogue/brands/1"))
      .andExpect(jsonPath("$.slug").value("samsung"));

    mvc
      .perform(
        put("/api/v1/catalogue/brands/1")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(referenceJson("Samsung Updated", "samsung-updated", false))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.active").value(false));

    mvc
      .perform(
        patch("/api/v1/catalogue/brands/1")
          .with(writeJwt())
          .contentType("application/merge-patch+json")
          .content(
            """
            {
              "name": "Samsung Patched"
            }
            """
          )
      )
      .andExpect(status().isOk());

    verify(referenceData).createOrUpdateBrand(
      argThat(
        command ->
          Long.valueOf(1L).equals(command.id()) &&
          "Samsung Patched".equals(command.name()) &&
          "samsung".equals(command.slug()) &&
          command.active()
      )
    );

    mvc
      .perform(delete("/api/v1/catalogue/brands/1").with(writeJwt()))
      .andExpect(status().isNoContent());
    verify(referenceData).deleteBrand(1L);
  }

  @Test
  void managesManufacturersWithTheSameContract() throws Exception {
    when(referenceData.listManufacturers(any())).thenReturn(
      new PageResult<>(
        List.of(manufacturer(2L, "Samsung Electronics", "samsung-electronics", true)),
        0,
        20,
        1,
        1,
        true,
        true,
        SortCriteria.of("name", SortDirection.ASC)
      )
    );
    when(referenceData.getManufacturer(2L)).thenReturn(
      manufacturer(2L, "Samsung Electronics", "samsung-electronics", true)
    );
    when(
      referenceData.createOrUpdateManufacturer(
        argThat(command -> command != null && command.id() == null)
      )
    ).thenReturn(manufacturer(2L, "Samsung Electronics", "samsung-electronics", true));

    mvc
      .perform(get("/api/v1/catalogue/manufacturers").with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].slug").value("samsung-electronics"));

    mvc
      .perform(get("/api/v1/catalogue/manufacturers/2").with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value(2));

    mvc
      .perform(
        post("/api/v1/catalogue/manufacturers")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(referenceJson("Samsung Electronics", "samsung-electronics", true))
      )
      .andExpect(status().isCreated())
      .andExpect(header().string("Location", "http://localhost/api/v1/catalogue/manufacturers/2"));
  }

  @Test
  void validationNotFoundDuplicateAndConflictErrorsUseStableShapes() throws Exception {
    mvc
      .perform(
        post("/api/v1/catalogue/brands")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(referenceJson("", "bad slug", true))
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    when(referenceData.getBrand(404L)).thenThrow(
      CatalogueReferenceNotFoundException.catalogueBrand(404L)
    );
    mvc
      .perform(get("/api/v1/catalogue/brands/404").with(readJwt()))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"));

    when(referenceData.createOrUpdateBrand(any())).thenThrow(
      DuplicateCatalogueSlugException.brand("samsung")
    );
    mvc
      .perform(
        post("/api/v1/catalogue/brands")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(referenceJson("Samsung", "samsung", true))
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("DUPLICATE_BRAND_SLUG"));

    doThrow(CatalogueConflictException.manufacturerDeleteBlocked(2L))
      .when(referenceData)
      .deleteManufacturer(2L);
    mvc
      .perform(delete("/api/v1/catalogue/manufacturers/2").with(writeJwt()))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("MANUFACTURER_DELETE_REJECTED"));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MeterConfiguration {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor readJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:read"));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor writeJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalogue:write"));
  }

  private static String referenceJson(String name, String slug, boolean active) {
    return """
    {
      "name": "%s",
      "slug": "%s",
      "active": %s
    }
    """.formatted(name, slug, active);
  }

  private static CatalogueBrand brand(Long id, String name, String slug, boolean active) {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-12T10:15:30Z");
    return new CatalogueBrand(id, name, slug, active, now, now);
  }

  private static CatalogueManufacturer manufacturer(
    Long id,
    String name,
    String slug,
    boolean active
  ) {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-12T10:15:30Z");
    return new CatalogueManufacturer(id, name, slug, active, now, now);
  }
}
