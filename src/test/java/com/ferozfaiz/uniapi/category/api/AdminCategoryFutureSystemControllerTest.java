package com.ferozfaiz.uniapi.category.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferozfaiz.uniapi.category.application.AdminCategoryApplicationService;
import com.ferozfaiz.uniapi.category.application.authorization.AdminCategorySystemAuthorizationPolicy;
import com.ferozfaiz.uniapi.category.application.error.AdminCategoryErrors;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemRegistry;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture;
import com.ferozfaiz.uniapi.platform.i18n.LocaleConfiguration;
import com.ferozfaiz.uniapi.platform.i18n.SpringMessageResolver;
import com.ferozfaiz.uniapi.platform.security.SecurityConfiguration;
import com.ferozfaiz.uniapi.platform.security.resourceserver.ResourceServerSecurityConfiguration;
import com.ferozfaiz.uniapi.platform.web.RestExceptionHandler;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
class AdminCategoryFutureSystemControllerTest {

  private static final String SYSTEM_KEY = "flat-restricted";
  private static final String BASE_PATH =
    "/api/v1/admin/category-systems/" + SYSTEM_KEY + "/categories";

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
  void registerRestrictedSystem() {
    fixture = AdminCategoryRuntimeTestFixture.harness(
      AdminCategoryRuntimeTestFixture.completeDefinition(
        SYSTEM_KEY,
        Set.of(Operation.CAPABILITIES, Operation.LIST, Operation.GET)
      )
    );
    when(registrations.findRegistration(SYSTEM_KEY)).thenReturn(
      Optional.of(fixture.registration())
    );
    when(categories.definition(SYSTEM_KEY, Operation.LIST)).thenReturn(fixture.definition());
    when(categories.list(eq(SYSTEM_KEY), any(AdminCategoryListValidationInput.class))).thenReturn(
      new PageResult<>(List.of(), 0, 20, 0, 0, true, true, SortCriteria.of("id", SortDirection.ASC))
    );
    when(categories.definition(SYSTEM_KEY, Operation.CREATE)).thenThrow(
      AdminCategoryErrors.capabilityUnsupported("create", SYSTEM_KEY)
    );
  }

  @Test
  void supportedRestrictedOperationUsesItsRegisteredPermissionAndReturnsNormally()
    throws Exception {
    mvc
      .perform(
        get(BASE_PATH).with(
          jwt().authorities(new SimpleGrantedAuthority("admin:flat-restricted-categories:read"))
        )
      )
      .andExpect(status().isOk());
  }

  @Test
  void knownUnsupportedOperationRequiresStandardPreliminaryPermissionBefore409() throws Exception {
    mvc
      .perform(
        post(BASE_PATH)
          .with(
            jwt().authorities(new SimpleGrantedAuthority("admin:flat-restricted-categories:read"))
          )
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isForbidden());

    mvc
      .perform(
        post(BASE_PATH)
          .with(
            jwt().authorities(new SimpleGrantedAuthority("admin:flat-restricted-categories:write"))
          )
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("CATEGORY_CAPABILITY_UNSUPPORTED"))
      .andExpect(
        jsonPath("$.message").value(
          "operation create is not supported for category system flat-restricted"
        )
      );

    mvc
      .perform(
        post(BASE_PATH)
          .with(
            jwt().authorities(new SimpleGrantedAuthority("admin:flat-restricted-categories:write"))
          )
          .header("Accept-Language", "fr")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isConflict())
      .andExpect(
        jsonPath("$.message").value(
          "l'operation create n'est pas prise en charge pour le systeme de categories flat-restricted"
        )
      );
  }

  @Test
  void absentSystemPreservesPermissionBeforeExistencePrecedence() throws Exception {
    String path = "/api/v1/admin/category-systems/absent/categories";
    when(categories.definition("absent", Operation.LIST)).thenThrow(
      AdminCategoryErrors.unknownSystem("absent")
    );

    mvc
      .perform(
        get(path).with(
          jwt().authorities(new SimpleGrantedAuthority("admin:flat-restricted-categories:read"))
        )
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        get(path).with(
          jwt().authorities(new SimpleGrantedAuthority("admin:absent-categories:read"))
        )
      )
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.code").value("UNKNOWN_CATEGORY_SYSTEM"));
  }
}
