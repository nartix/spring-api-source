package com.ferozfaiz.uniapi.catalogue.application.query;

public record GetProductDetailQuery(
  Long id,
  String slug,
  String marketplace,
  String merchantStore
) {}
