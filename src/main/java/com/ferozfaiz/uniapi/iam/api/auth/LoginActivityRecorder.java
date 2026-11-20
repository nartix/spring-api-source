package com.ferozfaiz.uniapi.iam.api.auth;

public interface LoginActivityRecorder {
  void recordSuccessfulLogin(Long userId);
}
