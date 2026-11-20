package com.ferozfaiz.uniapi.catalogue.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

import com.ferozfaiz.uniapi.catalogue.application.CatalogueCategoryApplicationService;
import com.ferozfaiz.uniapi.catalogue.application.CatalogueQueryService;
import com.ferozfaiz.uniapi.catalogue.application.command.ReorderCatalogueCategoryCommand;
import com.ferozfaiz.uniapi.catalogue.application.policy.CatalogueListPolicy;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueCategoryRankingException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueConflictException;
import com.ferozfaiz.uniapi.catalogue.domain.exception.CatalogueException;
import com.ferozfaiz.uniapi.catalogue.domain.model.CatalogueCategory;
import com.ferozfaiz.uniapi.platform.i18n.LocaleConfiguration;
import com.ferozfaiz.uniapi.platform.i18n.SpringMessageResolver;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.CategorySystemRegistry;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.ClosureTableDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.DisabledRankingRequestPolicy;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.FallbackOrderDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.PlacementMode;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.RankingCapabilityDescriptor;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseOrderTarget;
import com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy.SparseRankTableDescriptor;
import com.ferozfaiz.uniapi.platform.security.SecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.resourceserver.ResourceServerSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.web.RestExceptionHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(CatalogueCategoryController.class)
@Import({
  SecurityConfiguration.class,
  ResourceServerSecurityConfiguration.class,
  LocaleConfiguration.class,
  SpringMessageResolver.class,
  RestExceptionHandler.class,
  CatalogueApiExceptionHandler.class,
  CatalogueCategoryWriteControllerTest.MeterConfiguration.class,
})
class CatalogueCategoryWriteControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private CatalogueQueryService queries;

  @MockitoBean
  private CatalogueCategoryApplicationService categories;

  @MockitoBean
  private CatalogueListPolicy policy;

  @MockitoBean
  private CategorySystemRegistry categorySystems;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @Test
  void publicCategoryCapabilitiesExposeRankedOrderingMetadata() throws Exception {
    when(categorySystems.requireDescriptor("catalogue")).thenReturn(rankedDescriptor());

    mvc
      .perform(get("/api/v1/catalogue/categories/capabilities").with(readJwt()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.rankingEnabled").value(true))
      .andExpect(jsonPath("$.placementModes[0]").value("APPEND"))
      .andExpect(jsonPath("$.placementModes[1]").value("BEFORE_ID"))
      .andExpect(jsonPath("$.sortRankExposed").value(true))
      .andExpect(jsonPath("$.fallbackOrder").isEmpty());
  }

  @Test
  void publicCategoryCapabilitiesRequireReadAuthority() throws Exception {
    mvc
      .perform(get("/api/v1/catalogue/categories/capabilities"))
      .andExpect(status().isUnauthorized());

    mvc
      .perform(get("/api/v1/catalogue/categories/capabilities").with(writeJwt()))
      .andExpect(status().isForbidden());
  }

  @Test
  void allPublicCategoryMutationsRequireCatalogueWriteBeforeApplicationWork() throws Exception {
    for (Supplier<MockHttpServletRequestBuilder> request : publicMutationRequests()) {
      mvc.perform(request.get()).andExpect(status().isUnauthorized());
      mvc.perform(request.get().with(readJwt())).andExpect(status().isForbidden());
    }

    verifyNoInteractions(categories);
  }

  @Test
  void publicCategoryMutationAcceptsOnlyFrozenCatalogueWriteAliases() throws Exception {
    for (String rejected : List.of(
      "admin:catalogue-categories:write",
      "ROLE_ADMIN",
      "ROLE_MODERATOR"
    )) {
      mvc
        .perform(delete("/api/v1/catalogue/categories/1").with(authorityJwt(rejected)))
        .andExpect(status().isForbidden());
    }
    verifyNoInteractions(categories);

    for (String accepted : List.of(
      "SCOPE_catalogue:write",
      "CATALOGUE_WRITE",
      "SERVICE_CATALOGUE_WRITE"
    )) {
      mvc
        .perform(delete("/api/v1/catalogue/categories/1").with(authorityJwt(accepted)))
        .andExpect(status().isNoContent());
    }

    verify(categories, times(3)).delete(1L);
  }

  @Test
  void publicCategoryCreateKeepsRouteSpecificDtoStatusAndLocationRepresentation() throws Exception {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-12T10:15:30Z");
    when(categories.create(any())).thenReturn(
      new CatalogueCategory(41L, 7L, 900_000_000L, "Storage", "storage", true, now, now)
    );

    mvc
      .perform(
        post("/api/v1/catalogue/categories")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(categoryJson(7L, "Storage", "storage"))
      )
      .andExpect(status().isCreated())
      .andExpect(header().string("Location", "http://localhost/api/v1/catalogue/categories/41"))
      .andExpect(jsonPath("$.length()").value(8))
      .andExpect(jsonPath("$.id").value(41))
      .andExpect(jsonPath("$.parentId").value(7))
      .andExpect(jsonPath("$.sortRank").value(900_000_000))
      .andExpect(jsonPath("$.name").value("Storage"))
      .andExpect(jsonPath("$.slug").value("storage"))
      .andExpect(jsonPath("$.active").value(true))
      .andExpect(jsonPath("$.createdAt").value("2026-05-12T10:15:30Z"))
      .andExpect(jsonPath("$.updatedAt").value("2026-05-12T10:15:30Z"));
  }

  @Test
  void publicCategoryDtoValidationPrecedesApplicationWork() throws Exception {
    mvc
      .perform(
        post("/api/v1/catalogue/categories")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "targetIndex": -1,
              "name": "",
              "slug": "bad slug"
            }
            """
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.message").value("Request validation failed"))
      .andExpect(jsonPath("$.path").value("/api/v1/catalogue/categories"))
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'targetIndex')]").exists())
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists())
      .andExpect(jsonPath("$.fieldErrors[?(@.field == 'slug')]").exists());

    verifyNoInteractions(categories);
  }

  @Test
  void publicCategoryErrorsKeepRouteSpecificStatusCodeMessageAndPath() throws Exception {
    doThrow(CatalogueConflictException.categoryDeleteBlocked(1L)).when(categories).delete(1L);

    mvc
      .perform(delete("/api/v1/catalogue/categories/1").with(writeJwt()))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("CATEGORY_DELETE_REJECTED"))
      .andExpect(
        jsonPath("$.message").value(
          "category cannot be deleted because dependent catalogue data exists"
        )
      )
      .andExpect(jsonPath("$.path").value("/api/v1/catalogue/categories/1"));

    doThrow(CatalogueConflictException.invalidCategoryMove(1L, 2L))
      .when(categories)
      .move(eq(1L), eq(2L), any());

    mvc
      .perform(
        post("/api/v1/catalogue/categories/1/move")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"newParentId\":2}")
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("CATEGORY_MOVE_REJECTED"))
      .andExpect(jsonPath("$.message").value("category move is invalid"))
      .andExpect(jsonPath("$.path").value("/api/v1/catalogue/categories/1/move"));
  }

  @Test
  void createsReplacesPatchesAndMovesCategories() throws Exception {
    when(categories.create(argThat(command -> command.id() == null))).thenReturn(
      category(1L, null, "Storage", "storage", true)
    );
    when(categories.update(argThat(command -> Long.valueOf(1L).equals(command.id())))).thenReturn(
      category(1L, 10L, "Storage Updated", "storage-updated", true)
    );
    when(categories.get(1L)).thenReturn(
      category(1L, 10L, "Storage Updated", "storage-updated", true)
    );

    mvc
      .perform(
        post("/api/v1/catalogue/categories")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(categoryJson(null, "Storage", "storage"))
      )
      .andExpect(status().isCreated())
      .andExpect(header().string("Location", "http://localhost/api/v1/catalogue/categories/1"))
      .andExpect(jsonPath("$.slug").value("storage"));

    mvc
      .perform(
        put("/api/v1/catalogue/categories/1")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(categoryJson(10L, "Storage Updated", "storage-updated"))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.parentId").value(10));

    mvc
      .perform(
        patch("/api/v1/catalogue/categories/1")
          .with(writeJwt())
          .contentType("application/merge-patch+json")
          .content(
            """
            {
              "parentId": null,
              "name": "Root Storage"
            }
            """
          )
      )
      .andExpect(status().isOk());

    verify(categories).update(
      argThat(
        command ->
          Long.valueOf(1L).equals(command.id()) &&
          command.parentId() == null &&
          "Root Storage".equals(command.name()) &&
          "storage-updated".equals(command.slug())
      )
    );

    mvc
      .perform(
        post("/api/v1/catalogue/categories/1/move")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "newParentId": null
            }
            """
          )
      )
      .andExpect(status().isNoContent());

    when(
      categories.reorder(
        eq(1L),
        argThat(command -> Long.valueOf(2L).equals(command.orderTarget().afterId()))
      )
    ).thenReturn(category(1L, null, "Storage Updated", "storage-updated", true));

    mvc
      .perform(
        post("/api/v1/catalogue/categories/1/reorder")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "afterId": 2
            }
            """
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.id").value("1"));

    verify(categories).move(1L, null, SparseOrderTarget.append());
    verify(categories).reorder(
      eq(1L),
      argThat(command -> Long.valueOf(2L).equals(command.orderTarget().afterId()))
    );
  }

  @Test
  void createPublicCategoryRejectsConflictingPlacementFieldsWithStableMessage() throws Exception {
    mvc
      .perform(
        post("/api/v1/catalogue/categories")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "beforeId": 2,
              "afterId": 3,
              "name": "Storage",
              "slug": "storage"
            }
            """
          )
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(
        jsonPath("$.message").value(
          "Specify only one placement field: beforeId, afterId, or targetIndex"
        )
      );
  }

  @Test
  void reorderPublicCategoryRequiresWriteAuthority() throws Exception {
    String body = """
      {
        "afterId": 2
      }
      """;

    mvc
      .perform(
        post("/api/v1/catalogue/categories/1/reorder")
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isUnauthorized());

    mvc
      .perform(
        post("/api/v1/catalogue/categories/1/reorder")
          .with(readJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(body)
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void reorderPublicCategoryMapsRankRebalanceLimitToConflict() throws Exception {
    when(categories.reorder(eq(1L), any(ReorderCatalogueCategoryCommand.class))).thenThrow(
      CatalogueCategoryRankingException.rebalanceLimitExceeded(1_001, 1_000)
    );

    mvc
      .perform(
        post("/api/v1/catalogue/categories/1/reorder")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "targetIndex": 0
            }
            """
          )
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("CATEGORY_RANK_REBALANCE_REJECTED"))
      .andExpect(
        jsonPath("$.message").value("Category rank rebalance would affect 1,001 rows, limit 1,000")
      );
  }

  @Test
  void lifecycleAndDeleteReturnNoContent() throws Exception {
    mvc
      .perform(post("/api/v1/catalogue/categories/1/activate").with(writeJwt()))
      .andExpect(status().isNoContent());
    mvc
      .perform(post("/api/v1/catalogue/categories/1/deactivate").with(writeJwt()))
      .andExpect(status().isNoContent());
    mvc
      .perform(delete("/api/v1/catalogue/categories/1").with(writeJwt()))
      .andExpect(status().isNoContent());

    verify(categories).activate(1L);
    verify(categories).deactivate(1L);
    verify(categories).delete(1L);
  }

  @Test
  void validatesCategoryRequestsAndPatchRequiredNulls() throws Exception {
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
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    when(categories.get(1L)).thenReturn(category(1L, null, "Storage", "storage", true));
    when(categories.update(argThat(command -> command.name() == null))).thenThrow(
      new CatalogueException(
        "catalogue.application.category.name.required",
        "category name is required"
      )
    );

    mvc
      .perform(
        patch("/api/v1/catalogue/categories/1")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "name": null
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

  private static RequestPostProcessor writeJwt() {
    return authorityJwt("SCOPE_catalogue:write");
  }

  private static RequestPostProcessor readJwt() {
    return authorityJwt("SCOPE_catalogue:read");
  }

  private static RequestPostProcessor authorityJwt(String authority) {
    return jwt().authorities(new SimpleGrantedAuthority(authority));
  }

  private static List<Supplier<MockHttpServletRequestBuilder>> publicMutationRequests() {
    String category =
      "{\"parentId\":null,\"name\":\"Storage\",\"slug\":\"storage\",\"active\":true}";
    String placement = "{\"newParentId\":null}";
    String reorder = "{\"afterId\":2}";
    return List.of(
      () ->
        post("/api/v1/catalogue/categories")
          .contentType(MediaType.APPLICATION_JSON)
          .content(category),
      () ->
        put("/api/v1/catalogue/categories/1")
          .contentType(MediaType.APPLICATION_JSON)
          .content(category),
      () ->
        patch("/api/v1/catalogue/categories/1")
          .contentType("application/merge-patch+json")
          .content("{}"),
      () -> delete("/api/v1/catalogue/categories/1"),
      () ->
        post("/api/v1/catalogue/categories/1/move")
          .contentType(MediaType.APPLICATION_JSON)
          .content(placement),
      () ->
        post("/api/v1/catalogue/categories/1/reorder")
          .contentType(MediaType.APPLICATION_JSON)
          .content(reorder),
      () -> post("/api/v1/catalogue/categories/1/activate"),
      () -> post("/api/v1/catalogue/categories/1/deactivate")
    );
  }

  private static String categoryJson(Long parentId, String name, String slug) {
    String parent = parentId == null ? "null" : parentId.toString();
    return """
    {
      "parentId": %s,
      "name": "%s",
      "slug": "%s",
      "active": true
    }
    """.formatted(parent, name, slug);
  }

  private static CatalogueCategory category(
    Long id,
    Long parentId,
    String name,
    String slug,
    boolean active
  ) {
    OffsetDateTime now = OffsetDateTime.parse("2026-05-12T10:15:30Z");
    return new CatalogueCategory(id, parentId, name, slug, active, now, now);
  }

  private static CategorySystemDescriptor rankedDescriptor() {
    return new CategorySystemDescriptor(
      "catalogue",
      "catalogue",
      ClosureTableDescriptor.of("catalogue_category", "catalogue_category_closure"),
      RankingCapabilityDescriptor.ranked(
        "sort_rank",
        1_000_000_000L,
        1_000_000_000L,
        1_000L,
        DisabledRankingRequestPolicy.REJECT,
        List.of(
          PlacementMode.APPEND,
          PlacementMode.BEFORE_ID,
          PlacementMode.AFTER_ID,
          PlacementMode.TARGET_INDEX
        ),
        true
      ),
      new FallbackOrderDescriptor(
        List.of(
          new FallbackOrderDescriptor.Order("name", "name", FallbackOrderDescriptor.Direction.ASC),
          new FallbackOrderDescriptor.Order("id", "id", FallbackOrderDescriptor.Direction.ASC)
        )
      ),
      Optional.of(SparseRankTableDescriptor.of("catalogue_category", "parent_id", "sort_rank"))
    );
  }
}
