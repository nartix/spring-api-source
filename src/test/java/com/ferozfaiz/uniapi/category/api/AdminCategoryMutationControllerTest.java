package com.ferozfaiz.uniapi.category.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.authorization.AdminCategorySystemAuthorizationPolicy;
import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryCreateValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPatchValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPlacementValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryReplaceValidationInput;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture;
import com.ferozfaiz.uniapi.platform.i18n.LocaleConfiguration;
import com.ferozfaiz.uniapi.platform.i18n.SpringMessageResolver;
import com.ferozfaiz.uniapi.platform.security.SecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.resourceserver.ResourceServerSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.web.RestExceptionHandler;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AdminCategoryController.class)
@Import({
  SecurityConfiguration.class,
  ResourceServerSecurityConfiguration.class,
  LocaleConfiguration.class,
  SpringMessageResolver.class,
  RestExceptionHandler.class,
  AdminCategoryApiExceptionHandler.class,
  AdminCategorySystemAuthorizationPolicy.class,
})
class AdminCategoryMutationControllerTest {

  private static final String BASE_PATH = "/api/v1/admin/category-systems/catalogue/categories";

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private AdminCategoryApplicationService categories;

  @MockitoBean
  private AdminCategorySystemRegistry registrations;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  private AdminCategoryRuntimeTestFixture.Harness fixture;

  @BeforeEach
  void registerCatalogue() {
    fixture = AdminCategoryRuntimeTestFixture.harness();
    when(registrations.findRegistration("catalogue")).thenReturn(
      Optional.of(fixture.registration())
    );
    for (Operation operation : new Operation[] {
      Operation.CREATE,
      Operation.REPLACE,
      Operation.PATCH,
      Operation.MOVE,
      Operation.REORDER,
    }) {
      when(categories.definition("catalogue", operation)).thenReturn(fixture.definition());
    }
    when(
      categories.create(eq("catalogue"), any(AdminCategoryCreateValidationInput.class))
    ).thenReturn(category());
    when(
      categories.update(eq("catalogue"), eq(10L), any(AdminCategoryReplaceValidationInput.class))
    ).thenReturn(category());
    when(
      categories.patch(eq("catalogue"), eq(10L), any(AdminCategoryPatchValidationInput.class))
    ).thenReturn(category());
    when(
      categories.reorder(eq("catalogue"), eq(10L), any(AdminCategoryPlacementValidationInput.class))
    ).thenReturn(category());
  }

  @Test
  void allEightMutationRoutesRetainShapesAndTypedMappings() throws Exception {
    mvc
      .perform(
        post(BASE_PATH).with(writeJwt()).contentType(MediaType.APPLICATION_JSON).content(
          """
          {"parentId":2,"name":"Storage","slug":"storage","active":true,"targetIndex":0}
          """
        )
      )
      .andExpect(status().isCreated())
      .andExpect(header().string("Location", "http://localhost" + BASE_PATH + "/10"))
      .andExpect(jsonPath("$.id").value(10));
    mvc
      .perform(
        put(BASE_PATH + "/10")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {"parentId":2,"name":"Storage","slug":"storage","active":true}
            """
          )
      )
      .andExpect(status().isOk());
    mvc
      .perform(
        patch(BASE_PATH + "/10")
          .with(writeJwt())
          .contentType("application/merge-patch+json")
          .content("{\"name\":17,\"unknown\":\"ignored\"}")
      )
      .andExpect(status().isOk());
    mvc.perform(delete(BASE_PATH + "/10").with(writeJwt())).andExpect(status().isNoContent());
    mvc
      .perform(
        post(BASE_PATH + "/10/move")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"newParentId\":20,\"beforeId\":21}")
      )
      .andExpect(status().isNoContent());
    mvc
      .perform(
        post(BASE_PATH + "/10/reorder")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"parentId\":20,\"afterId\":9}")
      )
      .andExpect(status().isOk());
    mvc
      .perform(post(BASE_PATH + "/10/activate").with(writeJwt()))
      .andExpect(status().isNoContent());
    mvc
      .perform(post(BASE_PATH + "/10/deactivate").with(writeJwt()))
      .andExpect(status().isNoContent());

    verify(categories).create(
      eq("catalogue"),
      org.mockito.ArgumentMatchers.argThat(
        input -> input.parentId().equals(2L) && input.targetIndex().equals(0)
      )
    );
    verify(categories).patch(
      eq("catalogue"),
      eq(10L),
      org.mockito.ArgumentMatchers.argThat(
        input -> input.namePresent() && input.name().equals("17") && !input.slugPresent()
      )
    );
    verify(categories).move(
      eq("catalogue"),
      eq(10L),
      org.mockito.ArgumentMatchers.argThat(
        input ->
          input.operation() == Operation.MOVE &&
          input.parentId().equals(20L) &&
          input.beforeId().equals(21L)
      )
    );
    verify(categories).reorder(
      eq("catalogue"),
      eq(10L),
      org.mockito.ArgumentMatchers.argThat(
        input -> input.operation() == Operation.REORDER && input.afterId().equals(9L)
      )
    );
    verify(categories).delete("catalogue", 10L);
    verify(categories).activate("catalogue", 10L);
    verify(categories).deactivate("catalogue", 10L);
  }

  @Test
  void mutationsRequireExactWritePermissionBeforeBodyDependentWork() throws Exception {
    mvc
      .perform(
        post(BASE_PATH)
          .with(jwt().authorities(new SimpleGrantedAuthority("admin:catalogue-categories:read")))
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isForbidden());
    verifyNoInteractions(categories);
  }

  @Test
  void genericPatchIgnoresUnknownMembersBeforeMutationValidation() throws Exception {
    mvc
      .perform(
        patch(BASE_PATH + "/10")
          .with(writeJwt())
          .contentType("application/merge-patch+json")
          .content(
            """
            {
              "unknown": {"nested": "ignored"},
              "anotherUnknown": null
            }
            """
          )
      )
      .andExpect(status().isOk());

    InOrder validationOrder = inOrder(categories);
    validationOrder.verify(categories).definition("catalogue", Operation.PATCH);
    validationOrder
      .verify(categories)
      .patch(
        eq("catalogue"),
        eq(10L),
        org.mockito.ArgumentMatchers.argThat(
          input ->
            !input.parentIdPresent() &&
            !input.namePresent() &&
            !input.slugPresent() &&
            !input.activePresent()
        )
      );
  }

  @Test
  void genericPatchPreservesAbsenceAndExplicitNullForEveryMergeField() throws Exception {
    mvc
      .perform(
        patch(BASE_PATH + "/10")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isOk());
    mvc
      .perform(
        patch(BASE_PATH + "/10")
          .with(writeJwt())
          .contentType("application/merge-patch+json")
          .content(
            """
            {
              "parentId": null,
              "name": null,
              "slug": null,
              "active": null
            }
            """
          )
      )
      .andExpect(status().isOk());

    ArgumentCaptor<AdminCategoryPatchValidationInput> inputs = ArgumentCaptor.forClass(
      AdminCategoryPatchValidationInput.class
    );
    verify(categories, org.mockito.Mockito.times(2)).patch(
      eq("catalogue"),
      eq(10L),
      inputs.capture()
    );

    List<AdminCategoryPatchValidationInput> captured = inputs.getAllValues();
    assertThat(captured).hasSize(2);
    AdminCategoryPatchValidationInput absent = captured.getFirst();
    assertThat(absent.parentIdPresent()).isFalse();
    assertThat(absent.namePresent()).isFalse();
    assertThat(absent.slugPresent()).isFalse();
    assertThat(absent.activePresent()).isFalse();
    assertThat(absent.parentId()).isNull();
    assertThat(absent.name()).isNull();
    assertThat(absent.slug()).isNull();
    assertThat(absent.active()).isNull();

    AdminCategoryPatchValidationInput explicitNull = captured.get(1);
    assertThat(explicitNull.parentIdPresent()).isTrue();
    assertThat(explicitNull.namePresent()).isTrue();
    assertThat(explicitNull.slugPresent()).isTrue();
    assertThat(explicitNull.activePresent()).isTrue();
    assertThat(explicitNull.parentId()).isNull();
    assertThat(explicitNull.name()).isNull();
    assertThat(explicitNull.slug()).isNull();
    assertThat(explicitNull.active()).isNull();
  }

  @Test
  void genericPatchRetainsRecognizedValueCoercionForEveryMergeField() throws Exception {
    mvc
      .perform(
        patch(BASE_PATH + "/10")
          .with(writeJwt())
          .contentType("application/merge-patch+json")
          .content(
            """
            {
              "parentId": "23",
              "name": 17,
              "slug": false,
              "active": "true"
            }
            """
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()").value(8))
      .andExpect(jsonPath("$.id").value(10))
      .andExpect(jsonPath("$.parentId").value(2))
      .andExpect(jsonPath("$.sortRank").value(1_000_000_000))
      .andExpect(jsonPath("$.active").value(true));

    verify(categories).patch(
      eq("catalogue"),
      eq(10L),
      org.mockito.ArgumentMatchers.argThat(
        input ->
          input.parentIdPresent() &&
          Long.valueOf(23L).equals(input.parentId()) &&
          input.namePresent() &&
          "17".equals(input.name()) &&
          input.slugPresent() &&
          "false".equals(input.slug()) &&
          input.activePresent() &&
          Boolean.TRUE.equals(input.active())
      )
    );
  }

  @Test
  void genericPatchMalformedJsonKeepsStableTransportErrorBeforeApplicationWork() throws Exception {
    mvc
      .perform(
        patch(BASE_PATH + "/10")
          .with(writeJwt())
          .contentType("application/merge-patch+json")
          .content("{\"name\":")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
      .andExpect(jsonPath("$.message").value("Request body is invalid"))
      .andExpect(jsonPath("$.path").value(BASE_PATH + "/10"));

    verifyNoInteractions(categories);
  }

  @Test
  void genericPatchValidatesPositiveNumericIdBeforePresentNullFields() throws Exception {
    when(
      categories.patch(eq("catalogue"), eq(0L), any(AdminCategoryPatchValidationInput.class))
    ).thenAnswer(invocation -> fixture.service().patch("catalogue", 0L, invocation.getArgument(2)));

    mvc
      .perform(
        patch(BASE_PATH + "/0")
          .with(writeJwt())
          .contentType("application/merge-patch+json")
          .content("{\"name\":null}")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.message").value("category.api.category.id.positive"))
      .andExpect(jsonPath("$.path").value(BASE_PATH + "/0"));

    verifyNoInteractions(fixture.ports().basicMutation());
  }

  @Test
  void genericPatchPresentNullNameKeepsStableValidationOutcome() throws Exception {
    when(
      categories.patch(eq("catalogue"), eq(10L), any(AdminCategoryPatchValidationInput.class))
    ).thenAnswer(invocation ->
      fixture.service().patch("catalogue", 10L, invocation.getArgument(2))
    );

    mvc
      .perform(
        patch(BASE_PATH + "/10")
          .with(writeJwt())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"name\":null}")
      )
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
      .andExpect(jsonPath("$.message").value("category name is required"))
      .andExpect(jsonPath("$.path").value(BASE_PATH + "/10"));

    verifyNoInteractions(fixture.ports().basicMutation());
  }

  private static RequestPostProcessor writeJwt() {
    return jwt().authorities(new SimpleGrantedAuthority("admin:catalogue-categories:write"));
  }

  private static AdminCategoryModels.Category category() {
    var timestamp = OffsetDateTime.parse("2026-07-14T00:00:00Z");
    return new AdminCategoryModels.Category(
      10L,
      2L,
      1_000_000_000L,
      "Storage",
      "storage",
      true,
      timestamp,
      timestamp
    );
  }
}
