package com.ferozfaiz.uniapi.catalogue.api.dto;

public record ProductIdentifierResponse(
  String family,
  String value,
  String canonicalGtin14,
  boolean primary
) {}
