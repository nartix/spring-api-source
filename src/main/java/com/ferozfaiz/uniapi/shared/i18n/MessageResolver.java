package com.ferozfaiz.uniapi.shared.i18n;

public interface MessageResolver {
  String get(String code, Object... args);
}
