package com.ferozfaiz.uniapi.iam.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferozfaiz.uniapi.platform.security.authentication.AuthenticatedPrincipal;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class RegularSessionAuthControllerTest {

  private final RegularSessionAuthController controller = new RegularSessionAuthController();

  @Test
  void csrfReturnsRegularTokenMetadata() {
    CsrfTokenResponse response = controller.csrf(
      new DefaultCsrfToken("X-TGP-USER-XSRF-TOKEN", "_csrf", "opaque-token")
    );

    assertThat(response.token()).isEqualTo("opaque-token");
    assertThat(response.headerName()).isEqualTo("X-TGP-USER-XSRF-TOKEN");
    assertThat(response.parameterName()).isEqualTo("_csrf");
  }

  @Test
  void meReturnsRegularIdentityWithoutSessionIdentifier() {
    var authentication = new TestingAuthenticationToken(
      new AuthenticatedPrincipal(1L, "feroz", Set.of("USER"), Set.of("user:profile:read")),
      null
    );

    SessionIdentityResponse response = controller.me(authentication);

    assertThat(response.id()).isEqualTo(1L);
    assertThat(response.username()).isEqualTo("feroz");
    assertThat(response.roles()).containsExactly("USER");
    assertThat(response.permissions()).containsExactly("user:profile:read");
    assertThat(response.sessionScope()).isEqualTo(SessionScope.USER);
  }
}
