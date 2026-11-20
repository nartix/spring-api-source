package com.ferozfaiz.uniapi.category.application.registration;

public interface AdminCategoryIdentifierCodec<ID> {
  AdminCategoryIdentifierProfile identifierProfile();

  ID parse(String value);

  String serialize(ID value);
}
