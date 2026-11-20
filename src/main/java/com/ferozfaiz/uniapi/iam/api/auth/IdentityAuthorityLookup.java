package com.ferozfaiz.uniapi.iam.api.auth;

public interface IdentityAuthorityLookup {
  AuthenticatedAuthorities findAuthorities(Long userId);
}
