package com.ferozfaiz.uniapi.iam.api.auth;

import com.ferozfaiz.uniapi.iam.api.authorization.RequireAdminAccess;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${spring.data.rest.base-path}/admin/auth")
public class AdminSessionAuthController {

  @GetMapping("/csrf")
  CsrfTokenResponse csrf(CsrfToken csrfToken) {
    return new CsrfTokenResponse(
      csrfToken.getToken(),
      csrfToken.getHeaderName(),
      csrfToken.getParameterName()
    );
  }

  @GetMapping("/me")
  @RequireAdminAccess
  SessionIdentityResponse me(Authentication authentication) {
    SessionPrincipal principal = (SessionPrincipal) authentication.getPrincipal();
    return new SessionIdentityResponse(
      principal.userId(),
      principal.username(),
      Set.copyOf(principal.roles()),
      Set.copyOf(principal.permissions()),
      principal.roles().contains("MODERATOR") && !principal.roles().contains("ADMIN")
        ? SessionScope.MODERATOR
        : SessionScope.ADMIN
    );
  }
}
