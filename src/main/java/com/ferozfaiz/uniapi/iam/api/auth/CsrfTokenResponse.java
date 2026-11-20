package com.ferozfaiz.uniapi.iam.api.auth;

public record CsrfTokenResponse(String token, String headerName, String parameterName) {}
