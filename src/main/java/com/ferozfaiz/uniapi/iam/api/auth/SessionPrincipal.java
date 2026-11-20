package com.ferozfaiz.uniapi.iam.api.auth;

import java.security.Principal;
import java.util.Set;

public interface SessionPrincipal extends Principal {
  Long userId();

  String username();

  Set<String> roles();

  Set<String> permissions();
}
