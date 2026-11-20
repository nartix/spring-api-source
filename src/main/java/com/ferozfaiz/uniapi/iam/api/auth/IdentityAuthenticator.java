package com.ferozfaiz.uniapi.iam.api.auth;

public interface IdentityAuthenticator {
  AuthenticatedIdentity authenticate(String usernameOrEmail, String rawPassword);
}
